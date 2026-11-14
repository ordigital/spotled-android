package app.spotled.gui

import org.json.JSONObject

/** Glyph rows; each row has the same width. */
typealias Glyph = List<BooleanArray>

class SpotFont(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    private val glyphs: Map<Char, Glyph>,
    private val fallbacks: List<Char>,
) {
    fun glyph(c: Char): Glyph =
        glyphs[c] ?: fallbacks.firstNotNullOfOrNull { glyphs[it] } ?: glyphs.values.first()

    fun glyphWidth(c: Char) = glyph(c).firstOrNull()?.size ?: 0

    /** Text rendered into a bitmap for placement on the canvas (trimmed glyphs, 1 px spacing). */
    fun textStamp(text: String): Bitmap1? {
        val parts = text.mapNotNull { ch ->
            val g = trimColumns(glyph(ch), if (ch.isWhitespace()) 2 else 1)
            if (g.isEmpty() || g[0].isEmpty()) null else g
        }
        if (parts.isEmpty()) return null
        val total = parts.sumOf { it[0].size } + parts.size - 1
        val b = Bitmap1(total, height)
        var x0 = 0
        for (g in parts) {
            for (y in g.indices) for (x in g[y].indices) b.bits[y * total + x0 + x] = g[y][x]
            x0 += g[0].size + 1
        }
        return b
    }

    companion object {
        fun parseSlf(id: String, text: String, stem: String): SpotFont {
            val o = JSONObject(text)
            val name = o.optString("name").trim().ifEmpty { stem }
            val width = o.optInt("width", 0)
            val height = o.optInt("height", 0)
            require(width > 0 && height > 0) { "Font width/height must be positive." }
            val chars = o.optJSONObject("chars")
            require(chars != null && chars.length() > 0) { "Font file is missing glyph data." }
            val map = HashMap<Char, Glyph>()
            for (key in chars.keys()) {
                if (key.isEmpty()) continue
                val rows = chars.optJSONArray(key) ?: continue
                map[key[0]] = (0 until height).map { i ->
                    val row = if (i < rows.length()) rows.optString(i, "") else ""
                    BooleanArray(width) { x -> x < row.length && (row[x] == '1' || row[x] == '#') }
                }
            }
            map.getOrPut(' ') { List(height) { BooleanArray(width) } }
            return SpotFont(id, name, width, height, map, listOf('?', ' '))
        }

        fun parseYaff(id: String, text: String): SpotFont {
            val glyphs = HashMap<Char, Glyph>()
            var cur: Char? = null
            var acc = ArrayList<BooleanArray>()
            var w = 0
            fun flush() { cur?.let { if (acc.isNotEmpty()) glyphs[it] = acc }; acc = ArrayList(); cur = null }
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith("#")) continue
                if (line.endsWith(":") && (line.startsWith("0x") || line.startsWith("u+"))) {
                    flush()
                    cur = line.substring(2, line.length - 1).toIntOrNull(16)?.takeIf { it <= 0xFFFF }?.toChar()
                } else if ((line.contains('.') || line.contains('@')) && !line.contains(':')) {
                    val row = BooleanArray(line.length) { line[it] == '@' }
                    w = maxOf(w, row.size); acc.add(row)
                }
            }
            flush()
            return SpotFont(id, id, w, glyphs.values.maxOf { it.size }, glyphs, listOf('�', '\u0000', ' '))
        }
    }
}

fun trimColumns(g: Glyph, minWidth: Int): Glyph {
    val height = g.size
    val width = g.firstOrNull()?.size ?: 0
    fun blank(w: Int) = List(height) { BooleanArray(maxOf(1, w)) }
    if (width == 0) return blank(minWidth)
    val left = (0 until width).firstOrNull { x -> g.any { it[x] } } ?: return blank(minWidth)
    val right = (width - 1 downTo left).first { x -> g.any { it[x] } }
    return g.map { r -> r.copyOfRange(left, right + 1).let { if (it.size < minWidth) it.copyOf(minWidth) else it } }
}

/** Fonts available to the app: built-ins (assets) plus any user-installed `.slf`. */
class Fonts(val yaff6x12: SpotFont, val yaff4x6: SpotFont, val slf: List<SpotFont>) {
    companion object {
        fun load(open: (String) -> String, extra: List<Pair<String, String>> = emptyList()): Fonts {
            val slf = ArrayList<SpotFont>()
            slf.add(SpotFont.parseSlf("amstrad_cpc", open("amstrad_cpc.slf"), "amstrad_cpc"))
            for ((name, text) in extra) runCatching { slf.add(SpotFont.parseSlf(name, text, name.substringBeforeLast('.'))) }
            return Fonts(SpotFont.parseYaff("6x12", open("6x12.yaff")), SpotFont.parseYaff("4x6", open("4x6.yaff")), slf)
        }
    }
}

object FontLayout {
    enum class Align { LEFT, CENTER }

    /** Greedy word wrap (same rules as the device library). */
    fun reflow(text: String, font: SpotFont, width: Int): List<String> {
        fun tw(s: String) = s.sumOf { font.glyphWidth(it) }
        val out = ArrayList<String>()
        for (line in text.replace("\r", "").split("\n")) {
            var cur = StringBuilder()
            var remaining = width
            line.split(" ").forEachIndexed { i, orig ->
                val word = if (i != 0) " $orig" else orig
                val w = tw(word)
                when {
                    remaining - w >= 0 -> { remaining -= w; cur.append(word) }
                    w > width -> for (c in word) {
                        val cw = font.glyphWidth(c)
                        if (remaining - cw >= 0) { remaining -= cw; cur.append(c) }
                        else { out.add(cur.toString()); remaining = width - cw; cur = StringBuilder().append(c) }
                    }
                    else -> { out.add(cur.toString()); remaining = width - tw(orig); cur = StringBuilder(orig) }
                }
            }
            out.add(cur.toString())
        }
        return out
    }

    /** Rasterise lines into frames of [linesPerFrame] lines. A frame is a list of pixel rows. */
    fun linesToFrames(
        lines: List<String>, font: SpotFont, align: Align, width: Int, linesPerFrame: Int, lineHeight: Int,
    ): List<List<BooleanArray>> {
        val raster = ArrayList<List<BooleanArray>>()
        for (line in lines) {
            val rl = Array(lineHeight) { ArrayList<Boolean>() }
            for (c in line) {
                val g = font.glyph(c)
                require(g.size <= lineHeight) { "Character height exceeds line height." }
                val gw = g.firstOrNull()?.size ?: 0
                val top = (lineHeight - g.size) / 2 + (lineHeight - g.size) % 2
                for (i in 0 until lineHeight) {
                    if (i >= top && i - top < g.size) g[i - top].forEach { rl[i].add(it) } else repeat(gw) { rl[i].add(false) }
                }
            }
            while (rl[0].size > width) {
                raster.add(rl.map { r -> BooleanArray(width) { r[it] }.also { _ -> repeat(width) { r.removeAt(0) } } })
            }
            val rem = width - rl[0].size
            if (rem > 0) {
                val (l, r) = if (align == Align.LEFT) 0 to rem else rem / 2 to rem / 2 + rem % 2
                for (row in rl) { repeat(l) { row.add(0, false) }; repeat(r) { row.add(false) } }
            }
            raster.add(rl.map { r -> BooleanArray(r.size) { r[it] } })
        }
        val frames = ArrayList<List<BooleanArray>>()
        var cur = ArrayList<BooleanArray>()
        var n = 0
        for (rl in raster) {
            if (n < linesPerFrame) { cur.addAll(rl); n++ } else { frames.add(cur); cur = ArrayList(rl); n = 1 }
        }
        if (cur.isNotEmpty()) {
            repeat(linesPerFrame - n) { repeat(lineHeight) { cur.add(BooleanArray(width)) } }
            frames.add(cur)
        }
        return frames
    }
}
