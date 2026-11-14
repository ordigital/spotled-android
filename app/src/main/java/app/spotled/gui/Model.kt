package app.spotled.gui

import org.json.JSONArray
import org.json.JSONObject

const val W = 48
const val H = 12
const val MAX_FRAMES = 20

/** One 48x12 monochrome frame. Immutable value: every edit returns a new Frame. */
class Frame(private val p: BooleanArray = BooleanArray(W * H)) {
    fun get(x: Int, y: Int) = x in 0 until W && y in 0 until H && p[y * W + x]

    private inline fun edit(f: (BooleanArray) -> Unit): Frame = Frame(p.copyOf().also(f))

    fun with(x: Int, y: Int, on: Boolean): Frame =
        if (x !in 0 until W || y !in 0 until H || p[y * W + x] == on) this else edit { it[y * W + x] = on }

    fun inverted() = Frame(BooleanArray(p.size) { !p[it] })
    fun mirroredH() = Frame(BooleanArray(p.size) { val x = it % W; val y = it / W; p[y * W + (W - 1 - x)] })
    fun mirroredV() = Frame(BooleanArray(p.size) { val x = it % W; val y = it / W; p[(H - 1 - y) * W + x] })

    /** Non-wrapping shift. */
    fun shifted(dx: Int, dy: Int) = Frame(BooleanArray(p.size) {
        val x = it % W - dx
        val y = it / W - dy
        x in 0 until W && y in 0 until H && p[y * W + x]
    })

    /** Opaque overwrite of the stamp region. */
    fun blit(b: Bitmap1, ox: Int, oy: Int): Frame = edit { a ->
        for (y in 0 until b.h) for (x in 0 until b.w) {
            val fx = ox + x
            val fy = oy + y
            if (fx in 0 until W && fy in 0 until H) a[fy * W + fx] = b.get(x, y)
        }
    }

    fun toWire(): ByteArray = packRows((0 until H).map { y -> BooleanArray(W) { x -> p[y * W + x] } })

    fun toRows(): List<String> = (0 until H).map { y -> (0 until W).joinToString("") { x -> if (p[y * W + x]) "1" else "0" } }

    override fun equals(other: Any?) = other is Frame && p.contentEquals(other.p)
    override fun hashCode() = p.contentHashCode()

    companion object {
        fun fromRows(rows: List<String>): Frame? {
            if (rows.size != H || rows.any { it.length != W }) return null
            return Frame(BooleanArray(W * H) { rows[it / W][it % W] == '1' })
        }
    }
}

class Bitmap1(val w: Int, val h: Int, val bits: BooleanArray = BooleanArray(w * h)) {
    fun get(x: Int, y: Int) = x in 0 until w && y in 0 until h && bits[y * w + x]
}

enum class Effect(val label: String) {
    NONE("NONE"), SCROLL_UP("SCROLL_UP"), SCROLL_DOWN("SCROLL_DOWN"), SCROLL_LEFT("SCROLL_LEFT"),
    SCROLL_RIGHT("SCROLL_RIGHT"), STACK("STACK"), EXPAND("EXPAND"), LASER("LASER");

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name == s }
    }
}

/** Project file, JSON version 1 (compatible with the Python and Rust apps). */
data class Project(
    val frames: List<Frame> = listOf(Frame()),
    val current: Int = 0,
    val imgEffect: Effect = Effect.NONE,
    val imgSpeed: Int = 100,
    val text: String = "",
    val txtEffect: Effect = Effect.SCROLL_LEFT,
    val txtSpeed: Int = 0,
    val twoLines: Boolean = false,
    val tab: Int = 0,
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("version", 1)
        o.put("tab", tab)
        o.put("current_frame", current)
        o.put("image", JSONObject().apply {
            put("frames", JSONArray(frames.map { JSONArray(it.toRows()) }))
            put("effect", imgEffect.name)
            put("speed", imgSpeed)
        })
        o.put("text", JSONObject().apply {
            put("content", text)
            put("effect", txtEffect.name)
            put("speed", txtSpeed)
            put("two_lines", twoLines)
        })
        return o.toString(2)
    }

    companion object {
        fun fromJson(s: String): Project {
            val o = try { JSONObject(s) } catch (e: Exception) { throw IllegalArgumentException("invalid JSON") }
            val img = o.optJSONObject("image") ?: throw IllegalArgumentException("invalid structure")
            val fr = img.optJSONArray("frames") ?: throw IllegalArgumentException("invalid structure")
            val frames = (0 until fr.length()).map { i ->
                val rows = fr.optJSONArray(i) ?: throw IllegalArgumentException("invalid structure")
                Frame.fromRows((0 until rows.length()).map { rows.optString(it) })
                    ?: throw IllegalArgumentException("invalid structure")
            }
            if (frames.isEmpty()) throw IllegalArgumentException("invalid structure")
            val t = o.optJSONObject("text")
            return Project(
                frames = frames,
                current = o.optInt("current_frame", 0).coerceIn(0, frames.size - 1),
                imgEffect = Effect.parse(img.optString("effect")) ?: Effect.NONE,
                imgSpeed = img.optInt("speed", 100).coerceIn(1, 3500),
                text = t?.optString("content", "") ?: "",
                txtEffect = Effect.parse(t?.optString("effect")) ?: Effect.SCROLL_LEFT,
                txtSpeed = (t?.optInt("speed", 0) ?: 0).coerceIn(0, 3500),
                twoLines = t?.optBoolean("two_lines", false) ?: false,
                tab = o.optInt("tab", 0).coerceIn(0, 2),
            )
        }
    }
}

/** Undo/redo over whole-frame snapshots. */
class History {
    private class Edit(val idx: Int, val before: Frame, val after: Frame)
    private val edits = ArrayList<Edit>()
    private var pos = 0
    fun reset() { edits.clear(); pos = 0 }
    fun record(idx: Int, before: Frame, after: Frame) {
        if (before == after) return
        while (edits.size > pos) edits.removeAt(edits.size - 1)
        edits.add(Edit(idx, before, after)); pos = edits.size
    }
    val canUndo get() = pos > 0
    val canRedo get() = pos < edits.size
    fun undo(): Pair<Int, Frame>? = if (pos == 0) null else edits[--pos].let { it.idx to it.before }
    fun redo(): Pair<Int, Frame>? = if (pos >= edits.size) null else edits[pos++].let { it.idx to it.after }
}

/** A floating stamp being positioned over a frame. */
class Placement(val stamp: Bitmap1, val base: Frame, ox: Int = Math.floorDiv(W - stamp.w, 2), oy: Int = Math.floorDiv(H - stamp.h, 2)) {
    var ox = ox; private set
    var oy = oy; private set
    fun moveTo(x: Int, y: Int) {
        ox = x.coerceIn(-stamp.w + 1, W - 1)
        oy = y.coerceIn(-stamp.h + 1, H - 1)
    }
    fun compose() = base.blit(stamp, ox, oy)
}

/** Pack rows of pixels MSB first, each row padded to whole bytes. */
fun packRows(rows: List<BooleanArray>): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    for (r in rows) {
        var i = 0
        while (i < r.size) {
            var b = 0
            for (k in 0 until 8) if (i + k < r.size && r[i + k]) b = b or (0x80 shr k)
            out.write(b); i += 8
        }
    }
    return out.toByteArray()
}
