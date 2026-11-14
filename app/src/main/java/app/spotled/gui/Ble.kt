package app.spotled.gui

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class BleException(msg: String) : Exception(msg)

data class FoundDevice(val address: String, val name: String)

@SuppressLint("MissingPermission")
class Ble(private val ctx: Context) {
    private val adapter: BluetoothAdapter?
        get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private fun ready(): BluetoothAdapter {
        val a = adapter ?: throw BleException(t("bt_none"))
        if (!a.isEnabled) throw BleException(t("bt_enable"))
        return a
    }

    /** Scans for [seconds]; calls [onFound] with the full, deduplicated list whenever it grows. */
    suspend fun scan(seconds: Int, onFound: (List<FoundDevice>) -> Unit) {
        val scanner = ready().bluetoothLeScanner ?: throw BleException(t("bt_enable"))
        val found = LinkedHashMap<String, FoundDevice>()
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: r.device.name ?: return
                if (!name.uppercase().startsWith(Proto.NAME_PREFIX)) return
                if (found.put(r.device.address, FoundDevice(r.device.address.uppercase(), name)) == null) onFound(found.values.toList())
            }
            override fun onScanFailed(code: Int) {}
        }
        scanner.startScan(cb)
        try { delay(seconds * 1000L) } finally { runCatching { scanner.stopScan(cb) } }
    }

    // ---------- GATT link ----------
    private inner class Link(val gatt: BluetoothGatt, val events: Events) {
        lateinit var cmd: BluetoothGattCharacteristic
        lateinit var data: BluetoothGattCharacteristic

        fun close() { runCatching { gatt.disconnect() }; runCatching { gatt.close() } }

        suspend fun write(ch: BluetoothGattCharacteristic, bytes: ByteArray) {
            repeat(50) {
                val ok = if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION") run {
                        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        ch.value = bytes
                        gatt.writeCharacteristic(ch)
                    }
                }
                if (ok) {
                    withTimeoutOrNull(3000) { events.writes.receive() } ?: throw BleException(t("ble_write_timeout"))
                    return
                }
                delay(20)
            }
            throw BleException(t("ble_write_fail"))
        }
    }

    private class Events {
        val connected = CompletableDeferred<Boolean>()
        val services = CompletableDeferred<Boolean>()
        val descriptor = Channel<Int>(Channel.UNLIMITED)
        val writes = Channel<Int>(Channel.UNLIMITED)
        val notifs = Channel<ByteArray>(Channel.UNLIMITED)
        @Volatile var disconnected = false
    }

    private fun callback(ev: Events) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) ev.connected.complete(true)
            else { ev.disconnected = true; ev.connected.complete(false); ev.services.complete(false); ev.notifs.close() }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { ev.services.complete(status == BluetoothGatt.GATT_SUCCESS) }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { ev.descriptor.trySend(status) }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { ev.writes.trySend(status) }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { ev.notifs.trySend(value) }
        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") ev.notifs.trySend(c.value ?: return)
        }
    }

    private suspend fun open(address: String): Link {
        val dev = ready().getRemoteDevice(address)
        var last = t("ble_connect_fail")
        repeat(4) {
            val ev = Events()
            val gatt = dev.connectGatt(ctx, false, callback(ev), BluetoothDevice.TRANSPORT_LE)
            val link = Link(gatt, ev)
            try {
                if (withTimeoutOrNull(10_000) { ev.connected.await() } != true) { last = t("ble_no_conn"); throw BleException(last) }
                delay(300)
                gatt.discoverServices()
                if (withTimeoutOrNull(10_000) { ev.services.await() } != true) { last = t("ble_no_services"); throw BleException(last) }
                val svc = gatt.getService(Proto.SERVICE) ?: run { last = t("ble_not_spotled"); throw BleException(last) }
                link.cmd = svc.getCharacteristic(Proto.CMD) ?: throw BleException(t("ble_no_cmd"))
                link.data = svc.getCharacteristic(Proto.DATA) ?: throw BleException(t("ble_no_data"))
                enableNotifications(link)
                return link
            } catch (e: CancellationException) {
                link.close(); throw e
            } catch (e: Exception) {
                last = e.message ?: last
                link.close(); delay(600)
            }
        }
        throw BleException(last)
    }

    /** The board expects a 4-byte CCCD write (as the original Python library does); falls back to the standard value. */
    private suspend fun enableNotifications(l: Link) {
        l.gatt.setCharacteristicNotification(l.cmd, true)
        val d = l.cmd.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) ?: throw BleException(t("ble_no_cccd"))
        for (value in listOf(byteArrayOf(0, 0, 0, 1), byteArrayOf(1, 0))) {
            val ev = l.events
            @Suppress("DEPRECATION")
            val started = if (Build.VERSION.SDK_INT >= 33) l.gatt.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
            else { d.value = value; l.gatt.writeDescriptor(d) }
            if (!started) continue
            val st = withTimeoutOrNull(3000) { ev.descriptor.receive() }
            if (st == BluetoothGatt.GATT_SUCCESS) return
        }
        // Some firmwares accept notifications without an acknowledged CCCD write.
    }

    // ---------- protocol session ----------
    private class Retry(msg: String, val timeout: Boolean) : Exception(msg)

    private inner class Session(var link: Link) {
        var bufferSize = 240L
        var width = W; var height = H; var frameLimit = MAX_FRAMES

        suspend fun next(): Proto.Response {
            val v = withTimeoutOrNull(1000) { link.events.notifs.receiveCatching().getOrNull() }
                ?: throw Retry(t("ble_timeout_resp"), true)
            return try { Proto.parseResponse(v) } catch (e: IllegalArgumentException) { throw Retry(t("ble_bad_resp", v.joinToString(" ") { "%02x".format(it) }), false) }
        }

        suspend fun query(cmd: ByteArray): Proto.Response {
            var last: Retry? = null
            repeat(6) {
                link.write(link.cmd, cmd)
                try { return next() } catch (e: Retry) { if (!e.timeout) throw e; last = e }
            }
            throw last!!
        }

        suspend fun init() {
            (query(Proto.GET_BUFFER_SIZE) as? Proto.Response.BufferSize)?.let { if (it.size >= Proto.CHUNK) bufferSize = it.size }
            (query(Proto.GET_DISPLAY_INFO) as? Proto.Response.Display)?.let {
                if (it.width in 1..512 && it.height in 1..128 && it.frameLimit > 0) { width = it.width; height = it.height; frameLimit = it.frameLimit }
            }
        }

        suspend fun sendPayload(payload: ByteArray, serial: Int, progress: (Int) -> Unit) {
            val ty = Proto.SEND_DATA_TYPE
            link.write(link.cmd, Proto.startCmd(serial, ty, payload.size))
            val r = next()
            if (r !is Proto.Response.SendingData || r.serial != serial || r.type != ty || r.error != 0) throw Retry(t("ble_unexpected_start", r.toString()), false)
            val perBatch = maxOf(1L, bufferSize / Proto.CHUNK).toInt()
            var seek = 0; var n = 0
            while (seek < payload.size) {
                val end = minOf(seek + Proto.CHUNK, payload.size)
                link.write(link.data, payload.copyOfRange(seek, end))
                n++; seek += Proto.CHUNK
                if (n >= perBatch) {
                    n = 0
                    val c = next()
                    if (c !is Proto.Response.ContinueSending || c.serial != serial || c.type != ty) throw Retry(t("ble_unexpected_flow", c.toString()), false)
                    seek = c.continueFrom.toInt()
                }
                progress(minOf(seek, payload.size))
            }
            link.write(link.cmd, Proto.finishCmd(serial, ty, payload.size))
            next()
        }
    }

    suspend fun send(address: String, job: Proto.Job, fonts: Fonts, progress: (Int, Int) -> Unit): String = withContext(Dispatchers.Main) {
        val s = Session(open(address))
        try {
            s.init()
            val contents = try {
                Proto.jobContents(job, s.width, s.height, s.frameLimit, fonts)
            } catch (e: IllegalArgumentException) { throw BleException(e.message ?: t("bad_data")) }
            val total = contents.sumOf { it.size + 15 }
            var dataSerial = 0L; var cmdSerial = 0; var done = 0
            for (content in contents) {
                var attempt = 0
                while (true) {
                    dataSerial++; cmdSerial = (cmdSerial + 1) and 0xFFFF
                    val payload = Proto.sendData(content, dataSerial)
                    val base = done
                    try { s.sendPayload(payload, cmdSerial) { progress(base + it, total) }; break }
                    catch (e: Retry) {
                        if (!e.timeout || attempt >= 5) throw BleException(e.message ?: t("send_error"))
                        attempt++; s.link.close(); s.link = open(address); s.init()
                    }
                }
                done += content.size + 15
            }
            when (job) { is Proto.Job.Animation -> t("sent_frames", job.frames.size) else -> t("sent_text") }
        } finally { s.link.close() }
    }
}
