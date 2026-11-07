package com.kieronquinn.app.pcs.callscreen.pack

import android.content.Context
import android.content.res.Resources
import java.security.MessageDigest
import java.util.Locale

/** The Call Screen translations shipped in `assets/callscreen/`. */
object CallScreenTranslations {

    private const val DIRECTORY = "callscreen"

    class Asset(val name: String, val translation: CallScreenTranslation, val hash: String)

    fun phoneLocale(): Locale = Resources.getSystem().configuration.locales.get(0)

    /** The translation for the phone's language, or null if there is none. */
    fun forPhone(context: Context): Asset? = forLocale(context, phoneLocale())

    fun forLocale(context: Context, locale: Locale): Asset? {
        val names = context.assets.list(DIRECTORY).orEmpty().filter { it.endsWith(".json") }
        // Prefer an exact language-region file (pt-BR.json), then the language (tr.json).
        val name = listOf("${locale.toLanguageTag()}.json", "${locale.language}.json")
            .firstOrNull { it in names } ?: return null
        val bytes = context.assets.open("$DIRECTORY/$name").use { it.readBytes() }
        val translation = CallScreenTranslation.parse(bytes.inputStream().reader())
        if (translation.locale.language != locale.language) return null
        return Asset(name, translation, sha256(bytes))
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

}
