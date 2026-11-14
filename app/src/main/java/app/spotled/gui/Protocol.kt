package app.spotled.gui

import java.io.ByteArrayOutputStream
import java.util.UUID

/** SpotLED wire protocol (pure bytes in/out). All integers big-endian. */
object Proto {
    val SERVICE: UUID = UUID.fromString("0000ff20-0000-1000-8000-00805f9b34fb")
    val CMD: UUID = UUID.fromString("0000ff21-0000-1000-8000-00805f9b34fb")
    val DATA: UUID = UUID.fromString("0000ff22-0000-1000-8000-00805f9b34fb")
    const val SEND_DATA_TYPE = 0x8004
    const val CHUNK = 20
    const val NAME_PREFIX = "SPOTLED_"

    val GET_DISPLAY_INFO = byteArrayOf(4, 18, 0, 0)
    val GET_BUFFER_SIZE = byteArrayOf(4, 20, 0, 0)

    /** Byte writer; the checksum covers the bytes written since [begin]. */
    class Writer {
        val buf = ByteArrayOutputStream()
        private var start = 0
        fun begin() = apply { start = buf.size() }
        fun u8(v: Int) = apply { buf.write(v and 0xFF) }
        fun u16(v: Int) = apply { u8(v shr 8); u8(v) }
        fun u32(v: Long) = apply { u16((v shr 16).toInt()); u16(v.toInt()) }
        fun bytes(b: ByteArray) = apply { buf.write(b) }
        fun checksum() = apply {
            val a = buf.toByteArray()
            var s = 0
            for (i in start until a.size) s += a[i].toInt() and 0xFF
            u8(if (s > 255) (-s) and 0xFF else s)
        }
        fun toBytes(): ByteArray = buf.toByteArray()
    }

    private fun record(w: Writer, len: Int, type: Int, body: Writer.() -> Unit) {
        w.begin().u32(len.toLong()).u16(type); w.body(); w.checksum()
    }
    private fun time(w: Writer, ms: Int) = record(w, 10, 7) { u8(0); u16(ms) }
    private fun speed(w: Writer, s: Int) = record(w, 8, 9) { u8(s) }
    private fun effect(w: Writer, e: Effect) = record(w, 8, 8) { u8(e.ordinal) }

    class RawFrame(val width: Int, val height: Int, val bitmap: ByteArray)

    fun animation(frames: List<RawFrame>, timeMs: Int, speed: Int, effect: Effect): ByteArray {
        val w = Writer()
        record(w, 9, 11) { u16(frames.size) }
        for (f in frames) {
            w.begin().u32(12L + f.bitmap.size).u16(0x60).u16(f.width).u16(f.height).u8(1).bytes(f.bitmap).checksum()
        }
        time(w, timeMs); speed(w, speed); effect(w, effect)
        return w.toBytes()
    }

    fun animationFromFrames(frames: List<Frame>, timeMs: Int, speed: Int, effect: Effect) =
        animation(frames.map { RawFrame(W, H, it.toWire()) }, timeMs, speed, effect)

    class FontChar(val ch: Char, val width: Int, val height: Int, val glyph: List<BooleanArray>)

    fun fontData(chars: List<FontChar>): ByteArray {
        val w = Writer()
        record(w, 9, 5) { u16(chars.size) }
        for (c in chars) {
            val bm = packRows(c.glyph)
            w.begin().u32(15L + bm.size).u16(13).u8(1).u16(c.width).u16(c.height).u16(c.ch.code).u8(bm.size).bytes(bm).checksum()
        }
        return w.toBytes()
    }

    fun textData(text: String, speed: Int, effect: Effect): ByteArray {
        val w = Writer()
        record(w, 10, 4) { u16(text.length); u8(1) }
        for (c in text) {
            record(w, 10, 2) { u8(255); u8(255); u8(255) }
            record(w, 9, 3) { u16(c.code) }
        }
        time(w, 0); speed(w, speed); effect(w, effect)
        return w.toBytes()
    }

    /** Wrap content in the SendData envelope. */
    fun sendData(content: ByteArray, dataSerial: Long): ByteArray {
        val w = Writer()
        w.begin().u32(15).u16(SEND_DATA_TYPE).u32(dataSerial).u32(content.size.toLong()).checksum()
        w.bytes(content)
        return w.toBytes()
    }

    fun startCmd(serial: Int, type: Int, len: Int) = Writer().u8(10).u8(1).u16(serial).u16(type).u32(len.toLong()).toBytes()
    fun finishCmd(serial: Int, type: Int, len: Int) = startCmd(serial, type, len).also { it[1] = 3 }

    sealed class Response {
        data class SendingData(val serial: Int, val error: Int, val type: Int) : Response()
        data class ContinueSending(val serial: Int, val type: Int, val continueFrom: Long) : Response()
        data class Display(val width: Int, val height: Int, val frameLimit: Int) : Response()
        data class BufferSize(val size: Long) : Response()
        object Version : Response()
    }

    private fun be16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun be32(b: ByteArray, o: Int) = (be16(b, o).toLong() shl 16) or be16(b, o + 2).toLong()

    /** Accepts the plain notification layout and the gattlib layout (3 leading ATT bytes). */
    fun parseResponse(raw: ByteArray): Response {
        val off = when {
            raw.isNotEmpty() && (raw[0].toInt() and 0xFF) == raw.size -> 0
            raw.size >= 5 && (raw[3].toInt() and 0xFF) == raw.size - 3 -> 3
            else -> throw IllegalArgumentException("bad notification")
        }
        val v = raw.copyOfRange(off, raw.size)
        val c = v.copyOfRange(2, v.size)
        fun need(n: Int) { if (c.size < n) throw IllegalArgumentException("short notification") }
        return when (v[1].toInt() and 0xFF) {
            // Observed on real hardware: the board answers the start request with type 4 (python-spotled expects 2).
            2, 4 -> { need(5); Response.SendingData(be16(c, 0), c[2].toInt() and 0xFF, be16(c, 3)) }
            255 -> { need(8); Response.ContinueSending(be16(c, 0), be16(c, 2), be32(c, 4)) }
            19 -> { need(11); Response.Display(be16(c, 3), be16(c, 5), c[8].toInt() and 0xFF) }
            17 -> Response.Version
            21 -> { need(7); Response.BufferSize(be32(c, 3)) }
            else -> throw IllegalArgumentException("unknown response type")
        }
    }

    // ---- jobs ----
    sealed class Job {
        class Animation(val frames: List<Frame>, val timeMs: Int, val effect: Effect) : Job()
        class BuiltinText(val text: String, val twoLines: Boolean, val effect: Effect, val speed: Int) : Job()
        class CustomText(val font: SpotFont, val text: String, val effect: Effect, val speed: Int) : Job()
    }

    /** Content blobs (before the SendData envelope) for a job. */
    fun jobContents(job: Job, width: Int, height: Int, frameLimit: Int, fonts: Fonts): List<ByteArray> = when (job) {
        is Job.Animation -> {
            require(job.frames.isNotEmpty() && job.frames.size <= minOf(MAX_FRAMES, frameLimit)) { "Max. ${minOf(MAX_FRAMES, frameLimit)} frames." }
            listOf(animationFromFrames(job.frames, job.timeMs, 0, job.effect))
        }
        is Job.BuiltinText -> {
            val frames = if (job.twoLines) {
                val f = fonts.yaff4x6
                FontLayout.linesToFrames(FontLayout.reflow(job.text, f, width), f, FontLayout.Align.CENTER, width, maxOf(1, height / 6), 6)
            } else {
                val f = fonts.yaff6x12
                FontLayout.linesToFrames(job.text.replace("\r", "").split("\n"), f, FontLayout.Align.LEFT, width, maxOf(1, height / 12), 12)
            }
            require(frames.size <= frameLimit) { "The animation exceeds the device frame limit." }
            listOf(animation(frames.map { RawFrame(width, height, packRows(it)) }, 2000, job.speed and 0xFF, job.effect))
        }
        is Job.CustomText -> {
            val chars = job.text.toList().distinct().map {
                FontChar(it, job.font.width, job.font.height, job.font.glyph(it))
            }
            require(chars.isNotEmpty()) { "No characters to send." }
            listOf(fontData(chars), textData(job.text, job.speed, job.effect))
        }
    }
}
