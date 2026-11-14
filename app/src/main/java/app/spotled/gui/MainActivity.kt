package app.spotled.gui

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var model: AppModel
    private var afterPermission: (() -> Unit)? = null

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> runCatching { contentResolver.openInputStream(u)!!.use { it.readBytes().decodeToString() } }
            .onSuccess { model.load(it, displayName(u)) }
            .onFailure { model.message(t("error"), t("err_read")) } }
    }
    private val saveDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { u -> runCatching { contentResolver.openOutputStream(u, "wt")!!.use { it.write(model.serialize().toByteArray()) } }
            .onSuccess { model.fileName = displayName(u) }
            .onFailure { model.message(t("error"), t("err_write")) } }
    }
    private val pngDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> runCatching { decodePng(u) }
            .onSuccess { model.startPlacement(it) }
            .onFailure { model.message(t("error"), t("err_import", it.message ?: "")) } }
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) afterPermission?.invoke() else model.message(t("bt_title"), t("bt_perm_denied_msg"))
        afterPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val fonts = Fonts.load({ name -> assets.open(name).bufferedReader().readText() })
        model = AppModel(applicationContext, fonts)
        if (savedInstanceState == null) model.restore()
        val actions = Actions(
            open = { openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
            save = { saveDoc.launch(model.fileName ?: "spotled_project.json") },
            importPng = { pngDoc.launch(arrayOf("image/png")) },
            ensureBluetooth = ::ensureBluetooth,
        )
        setContent { SpotApp(model, actions) }
    }

    override fun onStop() { super.onStop(); if (::model.isInitialized) model.autosave() }

    private fun ensureBluetooth(then: () -> Unit) {
        val needed = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needed.all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) then()
        else { afterPermission = then; permissions.launch(needed) }
    }

    private fun displayName(u: Uri): String? = contentResolver.query(u, null, null, null, null)?.use {
        if (it.moveToFirst()) it.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { i -> i >= 0 }?.let { i -> it.getString(i) } else null
    }

    /** PNG pixel rule: on iff alpha >= 128 and HSL lightness >= 128. */
    private fun decodePng(u: Uri): Bitmap1 {
        val bmp = contentResolver.openInputStream(u)!!.use { android.graphics.BitmapFactory.decodeStream(it) } ?: error(t("err_invalid_image"))
        val b = Bitmap1(bmp.width, bmp.height)
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255; val al = c ushr 24
            b.bits[y * bmp.width + x] = al >= 128 && (maxOf(r, g, bl) + minOf(r, g, bl)) / 2 >= 128
        }
        return b
    }
}
