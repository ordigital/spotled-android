package app.spotled.gui

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONArray

enum class Mode { DRAW, SHIFT }
enum class View { BOARD, TEXT }

sealed class Dialog {
    object Tools : Dialog()
    object Send : Dialog()
    object Language : Dialog()
    object Gallery : Dialog()
    class InsertText(val text: String = "") : Dialog()
    class Message(val title: String, val body: String) : Dialog()
}

const val BUILTIN = "__builtin__"

/** All UI state and editing logic. */
class AppModel(private val ctx: Context, val fonts: Fonts) {
    private val prefs = ctx.getSharedPreferences("spotled", Context.MODE_PRIVATE)
    private val ble = Ble(ctx)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var project by mutableStateOf(Project())
    var fileName by mutableStateOf<String?>(null)
    val history = History()
    var historyTick by mutableIntStateOf(0)       // bumps when undo/redo availability may have changed
    var mode by mutableStateOf(Mode.DRAW)
    var view by mutableStateOf(View.BOARD)
    var playing by mutableStateOf(false)
    var placement by mutableStateOf<Placement?>(null)
    var dialog by mutableStateOf<Dialog?>(null)
    var dark by mutableStateOf(prefs.getBoolean("dark", false))
    fun applyDark(v: Boolean) { dark = v; prefs.edit().putBoolean("dark", v).apply() }
    var fontId by mutableStateOf(prefs.getString("font", BUILTIN) ?: BUILTIN)

    // device / sending
    var devices by mutableStateOf<List<FoundDevice>>(emptyList())
    var scanning by mutableStateOf(false)
    var selectedMac by mutableStateOf(prefs.getString("last_mac", "") ?: "")
    var progress by mutableStateOf<Float?>(null)
    var sendResult by mutableStateOf<String?>(null)
    var sendOk by mutableStateOf(false)
    var language by mutableStateOf(I18n.code)

    class GalleryItem(val name: String, val json: String, val project: Project)

    /** Ready-made projects bundled in assets/gallery (only projects with exactly 20 frames). */
    val gallery: List<GalleryItem> by lazy {
        (ctx.assets.list("gallery") ?: emptyArray()).filter { it.endsWith(".json") }.sorted().mapNotNull { f ->
            runCatching {
                val json = ctx.assets.open("gallery/$f").bufferedReader().readText()
                val p = Project.fromJson(json)
                if (p.frames.size == MAX_FRAMES) GalleryItem(f.removeSuffix(".json"), json, p) else null
            }.getOrNull()
        }
    }
    fun openGalleryItem(g: GalleryItem) { load(g.json, g.name + ".json"); project = project.copy(current = 0); dialog = null }

    val current: Frame get() = project.frames[project.current]
    val shown: Frame get() = placement?.compose() ?: current
    val previous: Frame? get() = if (project.current > 0) project.frames[project.current - 1] else null
    val busy get() = playing || placement != null
    val canUndo: Boolean get() { historyTick; return history.canUndo }
    val canRedo: Boolean get() { historyTick; return history.canRedo }
    val macHistory: List<String> get() = runCatching {
        val a = JSONArray(prefs.getString("mac_history", "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    init {
        I18n.init { ctx.assets.open(it).bufferedReader().readText() }
        I18n.code = prefs.getString("lang", null) ?: I18n.systemDefault()
    }
    fun selectLanguage(code: String) { I18n.code = code; prefs.edit().putString("lang", code).apply() }

    fun message(title: String, body: String) { dialog = Dialog.Message(title, body) }

    // ---------- editing ----------
    private fun setCurrent(f: Frame) {
        project = project.copy(frames = project.frames.toMutableList().also { it[project.current] = f })
    }
    fun edit(f: (Frame) -> Frame) {
        if (busy) return
        val before = current; val after = f(before)
        setCurrent(after); history.record(project.current, before, after); historyTick++
    }
    fun clear() = edit { Frame() }
    fun invert() = edit { it.inverted() }
    fun mirrorH() = edit { it.mirroredH() }
    fun mirrorV() = edit { it.mirroredV() }
    fun copyPrevious() { previous?.let { p -> edit { p } } }

    fun undo() { if (busy) return; history.undo()?.let { (i, f) -> apply(i, f) } }
    fun redo() { if (busy) return; history.redo()?.let { (i, f) -> apply(i, f) } }
    private fun apply(i: Int, f: Frame) {
        project = project.copy(frames = project.frames.toMutableList().also { it[i] = f }, current = i); historyTick++
    }

    fun goTo(i: Int) { if (!busy) project = project.copy(current = i.coerceIn(0, project.frames.size - 1)) }
    fun addFrame() {
        if (busy) return
        if (project.frames.size >= MAX_FRAMES) return message(t("error"), t("max_frames", MAX_FRAMES))
        val i = project.current + 1
        project = project.copy(frames = project.frames.toMutableList().also { it.add(i, Frame()) }, current = i)
        history.reset(); historyTick++
    }
    fun removeFrame() {
        if (busy) return
        project = if (project.frames.size == 1) project.copy(frames = listOf(Frame()))
        else project.copy(frames = project.frames.toMutableList().also { it.removeAt(project.current) },
            current = minOf(project.current, project.frames.size - 2))
        history.reset(); historyTick++
    }

    // ---------- strokes / shift / placement (driven by touch) ----------
    private var strokeBefore: Frame? = null
    private var strokeValue = true
    private var lastCell: Pair<Int, Int>? = null
    private var shiftBase: Frame? = null
    private var shiftStart: Pair<Int, Int>? = null
    private var grab: Pair<Int, Int>? = null

    fun press(x: Int, y: Int) {
        val pl = placement
        when {
            pl != null -> grab = x - pl.ox to y - pl.oy
            playing -> {}
            mode == Mode.DRAW -> {
                strokeBefore = current; strokeValue = !current.get(x, y); lastCell = null; paintTo(x, y)
            }
            else -> { shiftBase = current; shiftStart = x to y }
        }
    }
    fun move(x: Int, y: Int) {
        val pl = placement
        when {
            pl != null -> grab?.let { g -> pl.moveTo(x - g.first, y - g.second); placement = pl.let { Placement(it.stamp, it.base, it.ox, it.oy) } }
            strokeBefore != null -> paintTo(x, y)
            shiftBase != null -> { val s = shiftStart!!; setCurrent(shiftBase!!.shifted(x - s.first, y - s.second)) }
        }
    }
    fun release() {
        strokeBefore?.let { history.record(project.current, it, current); historyTick++ }
        shiftBase?.let { history.record(project.current, it, current); historyTick++ }
        strokeBefore = null; shiftBase = null; shiftStart = null; lastCell = null; grab = null
    }
    private fun paintTo(x: Int, y: Int) {
        var f = current
        val (lx, ly) = lastCell ?: (x to y)
        var cx = lx; var cy = ly
        val dx = Math.abs(x - lx); val dy = -Math.abs(y - ly)
        val sx = if (lx < x) 1 else -1; val sy = if (ly < y) 1 else -1
        var err = dx + dy
        while (true) {
            f = f.with(cx, cy, strokeValue)
            if (cx == x && cy == y) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; cx += sx }
            if (e2 <= dx) { err += dx; cy += sy }
        }
        lastCell = x to y; setCurrent(f)
    }

    fun startPlacement(b: Bitmap1) { if (!playing) placement = Placement(b, current) }
    fun commitPlacement() {
        val p = placement ?: return
        placement = null
        val after = p.compose()
        setCurrent(after); history.record(project.current, p.base, after); historyTick++
    }
    fun cancelPlacement() { placement = null }

    // ---------- project IO ----------
    fun load(json: String, name: String?) {
        try {
            project = Project.fromJson(json); fileName = name; history.reset(); historyTick++
            placement = null; playing = false; view = if (project.tab == 1) View.TEXT else View.BOARD
        } catch (e: Exception) { message(t("error"), t("err_load", e.message ?: "")) }
    }
    fun serialize() = project.copy(tab = if (view == View.TEXT) 1 else 0).toJson()

    fun autosave() { prefs.edit().putString("autosave", serialize()).apply() }
    fun restore() { prefs.getString("autosave", null)?.let { runCatching { load(it, null) } } }

    fun selectFont(id: String) { fontId = id; prefs.edit().putString("font", id).apply(); if (id != BUILTIN) project = project.copy(twoLines = false) }
    val selectedSlf: SpotFont? get() = fonts.slf.firstOrNull { it.id == fontId }

    // ---------- BLE ----------
    fun scan() {
        if (scanning) return
        scanning = true
        scope.launch {
            try { ble.scan(6) { devices = it; if (selectedMac.isEmpty()) selectedMac = it.first().address } }
            catch (e: BleException) { sendResult = e.message }
            catch (e: SecurityException) { sendResult = t("bt_perm_missing") }
            finally { scanning = false }
        }
    }

    private fun buildJob(): Proto.Job? {
        commitPlacement()
        if (view == View.BOARD) {
            if (project.frames.size > MAX_FRAMES) { sendResult = t("max_frames", MAX_FRAMES); return null }
            return Proto.Job.Animation(project.frames, project.imgSpeed, project.imgEffect)
        }
        val text = project.text
        if (text.isEmpty()) { sendResult = t("enter_text"); return null }
        val slf = selectedSlf
        if (fontId == BUILTIN || slf == null)
            return Proto.Job.BuiltinText(text, project.twoLines, project.txtEffect, project.txtSpeed and 0xFF)
        if (project.twoLines) { sendResult = t("custom_one_line"); return null }
        return Proto.Job.CustomText(slf, text, project.txtEffect, Math.round(project.txtSpeed / 3500f * 255))
    }

    fun send() {
        if (progress != null) return
        val mac = selectedMac.trim().uppercase()
        if (mac.isEmpty()) { sendResult = t("choose_board"); return }
        sendResult = null; sendOk = false
        val job = buildJob() ?: return
        prefs.edit().putString("last_mac", mac)
            .putString("mac_history", JSONArray((listOf(mac) + macHistory.filter { it != mac }).take(20)).toString()).apply()
        progress = 0f
        scope.launch {
            try {
                sendResult = ble.send(mac, job, fonts) { a, b -> progress = a.toFloat() / maxOf(1, b) }; sendOk = true
            } catch (e: BleException) { sendResult = e.message }
            catch (e: SecurityException) { sendResult = t("bt_perm_missing") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { sendResult = t("generic_error", e.message ?: "") }
            finally { progress = null }
        }
    }
}
