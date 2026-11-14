package app.spotled.gui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProtocolTest {
    private fun hex(s: String): ByteArray {
        val t = s.filterNot { it.isWhitespace() }
        return ByteArray(t.length / 2) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    private fun cat(vararg b: ByteArray) = b.fold(ByteArray(0)) { a, x -> a + x }
    private val assets = File("src/main/assets")
    private fun fonts() = Fonts.load({ File(assets, it).readText() })

    @Test fun checksumRule() {
        val w = Proto.Writer().begin().u8(255).u8(1).checksum().toBytes()
        assertEquals(0, w.last().toInt())
        assertEquals(255, Proto.Writer().begin().u8(255).checksum().toBytes().last().toInt() and 0xFF)
    }
    @Test fun animationVector() {
        val a = Proto.animationFromFrames(listOf(Frame()), 100, 0, Effect.NONE)
        val exp = cat(hex("00000009000b000115 00000054 0060 0030 000c 01"), ByteArray(72), hex("f1 0000000a0007000064 75 0000000800090011 0000000800080010"))
        assertArrayEquals(exp, a)
    }
    @Test fun textVector() {
        val t = Proto.textData("A", 128, Effect.SCROLL_LEFT)
        assertArrayEquals(hex("0000000a00040001 0110 0000000a0002fffffff7 00000009000300414d 0000000a000700000011 0000000800098091 0000000800080313"), t)
    }
    @Test fun fontVector() {
        val g = List(12) { BooleanArray(12) { true } }
        val f = Proto.fontData(listOf(Proto.FontChar('A', 12, 12, g)))
        assertArrayEquals(cat(hex("00000009000500010f 00000027000d01000c000c004118"), hex("fff0".repeat(12)), hex("26")), f)
    }
    @Test fun envelope() {
        val body = Proto.Writer().begin().u32(8).u16(14).u8(50).checksum().toBytes()
        assertArrayEquals(hex("0000000f800400000001000000089c 00000008000e3248"), Proto.sendData(body, 1))
    }
    @Test fun responses() {
        val plain = byteArrayOf(10, 2, 0, 1, 0, 0x80.toByte(), 4, 0, 0, 0)
        val r = Proto.parseResponse(plain)
        assertEquals(Proto.Response.SendingData(1, 0, 0x8004), r)
        assertEquals(r, Proto.parseResponse(byteArrayOf(0x1b, 0x0e, 0) + plain))
    }
    @Test fun sendingDataType4FromHardware() {
        val r = Proto.parseResponse(byteArrayOf(7, 4, 0, 1, 0, 0x80.toByte(), 4))
        assertEquals(Proto.Response.SendingData(1, 0, 0x8004), r)
    }
    @Test fun fontsAndLayout() {
        val f = fonts()
        assertEquals(12, f.slf[0].width)
        assertTrue(f.slf[0].textStamp("Hi") != null)
        val fr = FontLayout.linesToFrames(listOf("DRIVE SAFE!"), f.yaff6x12, FontLayout.Align.LEFT, 48, 1, 12)
        assertEquals(2, fr.size)
        assertEquals(listOf("A long time", "ago in a", "galaxy far", "away…"), FontLayout.reflow("A long time ago in a galaxy far away…", f.yaff4x6, 48))
    }
    @Test fun examplesLoad() {
        val dir = File("../examples")
        val files = dir.listFiles { x -> x.extension == "json" }!!
        assertTrue(files.isNotEmpty())
        for (x in files) {
            val p = Project.fromJson(x.readText())
            assertEquals(p.frames, Project.fromJson(p.toJson()).frames)
        }
    }
    @Test fun frameOps() {
        var f = Frame().with(0, 0, true).mirroredH()
        assertTrue(f.get(47, 0))
        f = f.mirroredV(); assertTrue(f.get(47, 11))
        assertTrue(f.shifted(-1, 0).get(46, 11)); assertFalse(f.shifted(1, 0).get(47, 11))
    }
}
