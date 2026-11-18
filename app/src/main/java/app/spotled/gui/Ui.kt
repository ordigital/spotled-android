package app.spotled.gui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog as ComposeDialog
import kotlinx.coroutines.delay

/** Interface theme. The pixel board keeps the same colours in both themes. */
object Theme { var dark by mutableStateOf(true) }
private fun pick(dark: Long, light: Long) = Color(if (Theme.dark) dark else light)

val Bg get() = pick(0xFF0D110D, 0xFFF3F6F2)
val Panel get() = pick(0xFF141A14, 0xFFFFFFFF)
val Panel2 get() = pick(0xFF1B231B, 0xFFE6EDE4)
val Line get() = pick(0xFF263226, 0xFFC5D0C3)
val Fg get() = pick(0xFFE4EFE2, 0xFF141A14)
val Dim get() = pick(0xFF8DA38A, 0xFF5B6E58)
val Accent get() = pick(0xFF4EFF00, 0xFF1F8A00)
val AccentInk get() = pick(0xFF06210A, 0xFFFFFFFF)
val SelBg get() = pick(0xFF0F2410, 0xFFD9F2D0)
// board: identical in both themes
val PixOn = Color(0xFF4EFF00); val PixGrid = Color(0xFF0F1E0F); val PixOnion = Color(0xFF14301A)
val BoardBg = Color(0xFF050805); val BoardLine = Color(0xFF263226)

private fun scheme() = if (Theme.dark) darkColorScheme(
    primary = Accent, onPrimary = AccentInk, background = Bg, onBackground = Fg,
    surface = Panel, onSurface = Fg, surfaceVariant = Panel2, onSurfaceVariant = Dim, outline = Line,
) else lightColorScheme(
    primary = Accent, onPrimary = AccentInk, background = Bg, onBackground = Fg,
    surface = Panel, onSurface = Fg, surfaceVariant = Panel2, onSurfaceVariant = Dim, outline = Line,
)

class Actions(
    val open: () -> Unit,
    val save: () -> Unit,
    val importPng: () -> Unit,
    val ensureBluetooth: (then: () -> Unit) -> Unit,
)

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Line, activeTickColor = AccentInk, inactiveTickColor = Dim)

@Composable
fun SpotApp(m: AppModel, a: Actions) {
    Theme.dark = m.dark
    MaterialTheme(colorScheme = scheme()) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Rail(m, a)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (m.view == View.BOARD) BoardView(m, a) else TextView(m, a)
                    }
                    CoffeeLink()
                }
            }
            m.dialog?.let { DialogHost(m, a, it) }
        }
    }
}

@Composable
private fun ColumnScope.CoffeeLink() {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val pink = Color(0xFFE91E8C)
    Row(
        Modifier.align(Alignment.End).clickable { runCatching { uri.openUri("https://buymeacoffee.com/adamstrad") } }.padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.Favorite, null, tint = pink, modifier = Modifier.size(11.dp))
        Text("buy me a coffee", color = pink, fontSize = 10.sp)
    }
}

// ---------------- rail ----------------
@Composable
private fun RailButton(icon: ImageVector, label: String, selected: Boolean = false, accent: Boolean = false, onClick: () -> Unit) {
    val bg = when { selected -> Accent; accent -> Panel2; else -> Color.Transparent }
    val fg = when { selected -> AccentInk; accent -> Accent; else -> Fg }
    Column(
        Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .then(if (accent) Modifier.border(1.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, color = fg, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun Rail(m: AppModel, a: Actions) {
    Column(
        Modifier.width(64.dp).fillMaxHeight().background(Panel).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        RailButton(Icons.Filled.FolderOpen, t("open")) { a.open() }
        RailButton(Icons.Filled.Save, t("save")) { a.save() }
        if (m.view == View.BOARD) {
            RailButton(if (m.mode == Mode.DRAW) Icons.Filled.Edit else Icons.Filled.OpenWith,
                if (m.mode == Mode.DRAW) t("draw") else t("shift"), selected = m.mode == Mode.SHIFT) {
                m.mode = if (m.mode == Mode.DRAW) Mode.SHIFT else Mode.DRAW
            }
            RailButton(Icons.Filled.Build, t("tools")) { if (!m.busy) m.dialog = Dialog.Tools }
        }
        RailButton(if (m.view == View.TEXT) Icons.Filled.GridOn else Icons.Filled.TextFields,
            if (m.view == View.TEXT) t("board") else t("text"), selected = m.view == View.TEXT) {
            m.commitPlacement(); m.playing = false
            m.view = if (m.view == View.TEXT) View.BOARD else View.TEXT
        }
        RailButton(Icons.Filled.PhotoLibrary, t("gallery")) { m.commitPlacement(); m.playing = false; m.dialog = Dialog.Gallery }
        Spacer(Modifier.weight(1f))
        RailButton(Icons.AutoMirrored.Filled.Send, t("send"), accent = true) { openSend(m, a) }
    }
}

private fun openSend(m: AppModel, a: Actions) {
    m.commitPlacement(); m.playing = false
    m.sendResult = null
    m.dialog = Dialog.Send
    a.ensureBluetooth { m.scan() }
}

@Composable
private fun LanguageToggle(m: AppModel) =
    SmallChip(Icons.Filled.Language, t("language")) { m.dialog = Dialog.Language }

@Composable
private fun ThemeToggle(m: AppModel) =
    SmallChip(if (m.dark) Icons.Filled.LightMode else Icons.Filled.DarkMode, if (m.dark) t("theme_to_light") else t("theme_to_dark")) { m.applyDark(!m.dark) }

// ---------------- board view ----------------
@Composable
private fun SmallChip(icon: ImageVector, desc: String, enabled: Boolean = true, label: String? = null, onClick: () -> Unit) {
    val c = if (enabled) Fg else Dim.copy(alpha = 0.4f)
    Row(
        Modifier.height(34.dp).defaultMinSize(minWidth = 38.dp).clip(RoundedCornerShape(8.dp)).background(Panel)
            .border(1.dp, Line, RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, desc, tint = c, modifier = Modifier.size(20.dp))
        if (label != null) { Spacer(Modifier.width(4.dp)); Text(label, color = c, fontSize = 12.sp) }
    }
}

@Composable
private fun BoardView(m: AppModel, a: Actions) {
    val p = m.project
    LaunchedEffect(m.playing, p.imgSpeed) {
        while (m.playing) {
            delay(maxOf(10, m.project.imgSpeed).toLong())
            m.project = m.project.copy(current = (m.project.current + 1) % m.project.frames.size)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // top bar
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.placement != null) {
                Text(t("drag_to_place"), color = Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                SmallChip(Icons.Filled.Close, t("cancel"), label = t("cancel")) { m.cancelPlacement() }
                SmallChip(Icons.Filled.Check, t("confirm"), label = t("confirm")) { m.commitPlacement() }
            } else {
                Text(m.fileName ?: t("new_project"), color = Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SmallChip(Icons.AutoMirrored.Filled.Undo, t("undo"), enabled = m.canUndo && !m.busy) { m.undo() }
                SmallChip(Icons.AutoMirrored.Filled.Redo, t("redo"), enabled = m.canRedo && !m.busy) { m.redo() }
                SmallChip(if (m.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, t("playback"),
                    label = if (m.playing) t("stop") else t("play")) { m.playing = !m.playing }
                LanguageToggle(m)
                ThemeToggle(m)
            }
        }
        Board(m)
        // frames
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val off = m.busy
            SmallChip(Icons.Filled.NavigateBefore, t("prev_frame"), enabled = !off && p.current > 0) { m.goTo(p.current - 1) }
            Text("${p.current + 1} / ${p.frames.size}", color = Fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            SmallChip(Icons.Filled.NavigateNext, t("next_frame"), enabled = !off && p.current < p.frames.size - 1) { m.goTo(p.current + 1) }
            if (p.frames.size > 1) {
                Slider(
                    value = p.current.toFloat(), onValueChange = { m.goTo(it.toInt()) }, enabled = !off,
                    valueRange = 0f..(p.frames.size - 1).toFloat(), steps = (p.frames.size - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f), colors = sliderColors(),
                )
            } else Spacer(Modifier.weight(1f))
            SmallChip(Icons.Filled.Add, t("add_frame"), enabled = !off) { m.addFrame() }
            SmallChip(Icons.Filled.ContentCopy, t("copy_prev"), enabled = !off && p.current > 0) { m.copyPrevious() }
            SmallChip(Icons.Filled.Delete, t("del_frame"), enabled = !off) { m.removeFrame() }
        }
        // effect + speed
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("effect"), color = Dim, fontSize = 11.sp)
            EffectPicker(p.imgEffect) { m.project = m.project.copy(imgEffect = it) }
            Text(t("speed"), color = Dim, fontSize = 11.sp)
            Slider(p.imgSpeed.toFloat(), { m.project = m.project.copy(imgSpeed = it.toInt()) }, valueRange = 1f..3500f, modifier = Modifier.weight(1f), colors = sliderColors())
            Text("${p.imgSpeed} ms", color = Fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(60.dp))
        }
    }
}

@Composable
private fun Board(m: AppModel) {
    val shown = m.shown
    val prev = m.previous
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(BoardBg).border(1.dp, BoardLine, RoundedCornerShape(6.dp)).padding(3.dp)) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(W / H.toFloat())
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val cell = size.width / W.toFloat()
                        fun at(o: Offset) = (o.x / cell).toInt().coerceIn(0, W - 1) to (o.y / cell).toInt().coerceIn(0, H - 1)
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        at(down.position).let { m.press(it.first, it.second) }
                        drag(down.id) { ch -> ch.consume(); at(ch.position).let { m.move(it.first, it.second) } }
                        m.release()
                    }
                },
        ) {
            val cell = size.width / W
            for (y in 0 until H) for (x in 0 until W) {
                val c = when { shown.get(x, y) -> PixOn; prev?.get(x, y) == true -> PixOnion; else -> PixGrid }
                drawRect(c, Offset(x * cell + 1f, y * cell + 1f), Size(cell - 2f, cell - 2f))
            }
        }
    }
}

@Composable
private fun EffectPicker(value: Effect, onChange: (Effect) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(8.dp))
                .clickable { open = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text(value.label, color = Fg, fontSize = 13.sp); Icon(Icons.Filled.ArrowDropDown, null, tint = Dim) }
        DropdownMenu(open, { open = false }) {
            Effect.entries.forEach { e -> DropdownMenuItem(text = { Text(e.label) }, onClick = { onChange(e); open = false }) }
        }
    }
}

// ---------------- text view ----------------
@Composable
private fun TextView(m: AppModel, a: Actions) {
    val p = m.project
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("text_label"), color = Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            LanguageToggle(m)
            ThemeToggle(m)
        }
        OutlinedTextField(
            value = p.text, onValueChange = { m.project = m.project.copy(text = it) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
            placeholder = { Text(t("type_text"), color = Dim) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Line, cursorColor = Accent),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("font"), color = Dim, fontSize = 11.sp)
            FontPicker(m)
            val builtin = m.fontId == BUILTIN || m.selectedSlf == null
            Checkbox(p.twoLines && builtin, { m.project = m.project.copy(twoLines = it) }, enabled = builtin)
            Text(t("two_lines"), color = if (builtin) Fg else Dim, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("effect"), color = Dim, fontSize = 11.sp)
            EffectPicker(p.txtEffect) { m.project = m.project.copy(txtEffect = it) }
            Text(t("speed"), color = Dim, fontSize = 11.sp)
            Slider(p.txtSpeed.toFloat(), { m.project = m.project.copy(txtSpeed = (it / 10).toInt() * 10) }, valueRange = 0f..3500f, modifier = Modifier.weight(1f), colors = sliderColors())
            Text("${p.txtSpeed}", color = Fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { openSend(m, a) }, modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentInk),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            Text(t("send"), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FontPicker(m: AppModel) {
    var open by remember { mutableStateOf(false) }
    val label = if (m.fontId == BUILTIN) t("font_builtin") else m.selectedSlf?.name ?: t("font_builtin")
    Box {
        Row(
            Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(8.dp))
                .clickable { open = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text(label, color = Fg, fontSize = 13.sp); Icon(Icons.Filled.ArrowDropDown, null, tint = Dim) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(t("font_builtin")) }, onClick = { m.selectFont(BUILTIN); open = false })
            m.fonts.slf.forEach { f -> DropdownMenuItem(text = { Text(f.name) }, onClick = { m.selectFont(f.id); open = false }) }
        }
    }
}

// ---------------- dialogs ----------------
@Composable
private fun Sheet(title: String, onClose: (() -> Unit)?, widthFraction: Float = 0.62f, content: @Composable ColumnScope.() -> Unit) {
    ComposeDialog(onDismissRequest = { onClose?.invoke() }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(widthFraction), shape = RoundedCornerShape(14.dp), color = Panel2, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Fg)
                content()
            }
        }
    }
}

@Composable
private fun DialogHost(m: AppModel, a: Actions, d: Dialog) {
    when (d) {
        Dialog.Tools -> Sheet(t("tools"), { m.dialog = null }) {
            val tools = listOf<Triple<ImageVector, String, () -> Unit>>(
                Triple(Icons.Filled.DeleteSweep, t("tool_clear")) { m.clear() },
                Triple(Icons.Filled.InvertColors, t("tool_invert")) { m.invert() },
                Triple(Icons.Filled.Flip, t("tool_mirror_h")) { m.mirrorH() },
                Triple(Icons.Filled.Flip, t("tool_mirror_v")) { m.mirrorV() },
                Triple(Icons.Filled.Image, t("tool_import_png")) { a.importPng() },
                Triple(Icons.Filled.TextFields, t("tool_insert_text")) { m.dialog = Dialog.InsertText() },
            )
            tools.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (icon, label, act) ->
                        Row(
                            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
                                .border(1.dp, Line, RoundedCornerShape(10.dp))
                                .clickable { if (m.dialog === Dialog.Tools) m.dialog = null; act() }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) { Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp)); Text(label, color = Fg, fontSize = 13.sp) }
                    }
                }
            }
        }
        is Dialog.InsertText -> {
            var text by remember { mutableStateOf(d.text) }
            var fontIdx by remember { mutableIntStateOf(0) }
            Sheet(t("insert_text_title"), { m.dialog = null }) {
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text(t("text_label")) })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("font"), color = Dim, fontSize = 11.sp)
                    m.fonts.slf.forEachIndexed { i, f ->
                        FilterChip(fontIdx == i, { fontIdx = i }, label = { Text(f.name) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton({ m.dialog = null }) { Text(t("cancel")) }
                    Button({
                        val stamp = m.fonts.slf[fontIdx].textStamp(text)
                        if (stamp == null) m.message(t("error"), t("enter_text")) else { m.dialog = null; m.startPlacement(stamp) }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentInk)) { Text(t("ok")) }
                }
            }
        }
        is Dialog.Message -> Sheet(d.title, { m.dialog = null }) {
            Text(d.body, color = Fg)
            TextButton({ m.dialog = null }, modifier = Modifier.align(Alignment.End)) { Text(t("ok")) }
        }
        Dialog.Language -> Sheet(t("language"), { m.dialog = null }) {
            I18n.languages.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (code, name) ->
                        val sel = I18n.code == code
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) SelBg else Panel)
                                .border(1.dp, if (sel) Accent else Line, RoundedCornerShape(10.dp))
                                .clickable { m.selectLanguage(code); m.dialog = null },
                            contentAlignment = Alignment.Center,
                        ) { Text(name, color = if (sel) Accent else Fg, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
            }
        }
        Dialog.Gallery -> GalleryDialog(m)
        Dialog.Send -> SendDialog(m, a)
    }
}

@Composable
private fun SendDialog(m: AppModel, a: Actions) {
    val sending = m.progress != null
    Sheet(t("send_title"), { if (!sending) m.dialog = null }) {
        val known = m.devices
        val history = m.macHistory.filter { h -> known.none { it.address == h } }
        LazyColumn(Modifier.heightIn(max = 170.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(known) { d -> DeviceRow(d.name, d.address, t("dev_found"), m.selectedMac == d.address) { m.selectedMac = d.address } }
            items(history) { h -> DeviceRow(t("dev_last"), h, t("dev_maybe_away"), m.selectedMac == h) { m.selectedMac = h } }
            if (known.isEmpty() && history.isEmpty()) item {
                Text(if (m.scanning) t("searching") else t("none_found"), color = Dim, fontSize = 13.sp)
            }
        }
        m.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Accent) }
        m.sendResult?.let { Text(it, color = if (m.sendOk) Accent else Color(0xFFFFB54A), fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ a.ensureBluetooth { m.scan() } }, enabled = !m.scanning && !sending) {
                if (m.scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                else Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(t("scan_again"))
            }
            Spacer(Modifier.weight(1f))
            TextButton({ m.dialog = null }, enabled = !sending) { Text(if (m.sendOk) t("close") else t("cancel")) }
            val n = if (m.view == View.BOARD) t("send_frames", m.project.frames.size) else t("send_text")
            Button({ a.ensureBluetooth { m.send() } }, enabled = !sending && m.selectedMac.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentInk)) { Text(n, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DeviceRow(name: String, mac: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) SelBg else Panel)
            .border(1.dp, if (selected) Accent else Line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Bluetooth, null, tint = if (selected) Accent else Dim, modifier = Modifier.size(20.dp))
        Text(name, color = Fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(mac, color = Dim, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(hint, color = Dim, fontSize = 12.sp)
    }
}

@Composable
private fun GalleryDialog(m: AppModel) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(150); tick++ } }
    Sheet(t("gallery"), { m.dialog = null }, widthFraction = 0.86f) {
        val items = m.gallery
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.heightIn(max = 250.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items.size) { i ->
                val g = items[i]
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(10.dp))
                        .clickable { m.openGalleryItem(g) }.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Thumb(g.project.frames[tick % g.project.frames.size])
                    Text(g.name, color = Fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Animated-board thumbnail; same pixel colours as the main board. */
@Composable
private fun Thumb(f: Frame) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(W / H.toFloat()).clip(RoundedCornerShape(4.dp)).background(BoardBg)) {
        val cell = size.width / W
        for (y in 0 until H) for (x in 0 until W) {
            drawRect(if (f.get(x, y)) PixOn else PixGrid, Offset(x * cell + 0.5f, y * cell + 0.5f), Size(cell - 1f, cell - 1f))
        }
    }
}
