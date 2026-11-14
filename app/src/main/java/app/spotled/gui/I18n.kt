package app.spotled.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** UI translations from assets/i18n/<code>.json; English is the fallback. */
object I18n {
    /** Display order: English first, then the author's language, then Latin scripts, Cyrillic, East Asian. */
    val languages = listOf(
        "en" to "English", "pl" to "Polski", "de" to "Deutsch", "fr" to "Français", "es" to "Español", "it" to "Italiano",
        "cs" to "Čeština", "hu" to "Magyar", "uk" to "Українська", "ru" to "Русский", "zh" to "中文", "ja" to "日本語",
    )
    private var reader: ((String) -> String)? = null
    private val cache = HashMap<String, Map<String, String>>()
    var code by mutableStateOf("en")

    fun init(read: (String) -> String) { reader = read }

    private fun table(c: String): Map<String, String> = cache.getOrPut(c) {
        runCatching {
            val o = JSONObject(reader!!("i18n/$c.json"))
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun text(key: String): String = table(code)[key] ?: table("en")[key] ?: key

    /** Language for the device locale, or English when unsupported. */
    fun systemDefault(): String {
        val l = java.util.Locale.getDefault().language
        return languages.firstOrNull { it.first == l }?.first ?: "en"
    }
}

fun t(key: String): String = I18n.text(key)
fun t(key: String, vararg args: Any): String = String.format(I18n.text(key), *args)
