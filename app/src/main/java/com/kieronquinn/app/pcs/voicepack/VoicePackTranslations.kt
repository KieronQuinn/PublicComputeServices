package com.kieronquinn.app.pcs.voicepack

import android.content.Context
import android.content.res.Resources
import java.security.MessageDigest
import java.util.Locale

/** The voice pack translations shipped in `assets/<feature>/` ([VoicePack.assetDirectory]). */
object VoicePackTranslations {

    class Asset(val name: String, val translation: VoicePackTranslation, val hash: String)

    fun phoneLocale(): Locale = Resources.getSystem().configuration.locales.get(0)

    /** The translation for the phone's language, or null if there is none. */
    fun forPhone(context: Context, pack: VoicePack): Asset? = forLocale(context, pack, phoneLocale())

    fun forLocale(context: Context, pack: VoicePack, locale: Locale): Asset? {
        val directory = pack.assetDirectory
        val names = context.assets.list(directory).orEmpty().filter { it.endsWith(".json") }
        // Prefer an exact language-region file (pt-BR.json), then the language (tr.json).
        val name = listOf("${locale.toLanguageTag()}.json", "${locale.language}.json")
            .firstOrNull { it in names } ?: return null
        val bytes = context.assets.open("$directory/$name").use { it.readBytes() }
        val translation = VoicePackTranslation.parse(bytes.inputStream().reader())
        if (translation.locale.language != locale.language) return null
        return Asset(name, translation, sha256(bytes))
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

}
