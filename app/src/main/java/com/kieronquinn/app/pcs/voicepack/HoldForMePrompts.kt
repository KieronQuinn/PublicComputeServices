package com.kieronquinn.app.pcs.voicepack

import java.io.File
import java.util.Locale
import java.util.Properties

/**
 *  Hold for Me's sentences to the other party in the phone's language, shared by the app (which
 *  records them) and the Phone hooks (which play them).
 *
 *  Phone ships these sentences as recordings in English and Japanese only, and copies the one it
 *  needs into its cache as `hfm-audio-<prompt>-<language>[-<variant>]` before playing it. PCS
 *  records the translated sentences with the phone's text-to-speech voice into
 *  `files/pcs-hold-for-me/<locale>/<prompt>.wav` in Phone's data directory, next to a marker that
 *  records the translation and voice. Phone's cache is left alone.
 */
object HoldForMePrompts {

    const val ASSET_DIRECTORY = "holdforme"
    const val PACK_DIRECTORY = "pcs-hold-for-me"
    const val MARKER = "pcs_voice_pack.properties"
    /** The rate of Phone's own recordings of these sentences. */
    const val SAMPLE_RATE = 24_000

    /** The sentences, by the name Phone gives them (lower case of its prompt enum). */
    val PROMPTS = listOf("greeting_to_agent", "probing_prompt", "delayed_user_message", "recording_refusal_ack")

    private val CACHE_FILE = Regex("hfm-audio-([a-z_]+)-[A-Z]+(-.+)?")

    /** The prompt of a cache file Phone created, or null for another file. */
    fun promptOf(cacheFileName: String): String? =
        CACHE_FILE.matchEntire(cacheFileName)?.groupValues?.get(1)?.takeIf { it in PROMPTS }

    fun packRoot(filesDir: File) = File(filesDir, PACK_DIRECTORY)

    fun packDirectory(filesDir: File, locale: Locale) = File(packRoot(filesDir), locale.toLanguageTag())

    fun recording(directory: File, prompt: String) = File(directory, "$prompt.wav")

    data class Marker(val locale: String, val translation: String, val voice: String) {

        fun write(): String = buildString {
            appendLine("$KEY_LOCALE=$locale")
            appendLine("$KEY_TRANSLATION=$translation")
            appendLine("$KEY_VOICE=$voice")
        }

        companion object {
            private const val KEY_LOCALE = "locale"
            private const val KEY_TRANSLATION = "translation"
            private const val KEY_VOICE = "voice"

            fun read(text: String): Marker? {
                val properties = Properties().apply { load(text.reader()) }
                return Marker(
                    properties.getProperty(KEY_LOCALE) ?: return null,
                    properties.getProperty(KEY_TRANSLATION) ?: return null,
                    properties.getProperty(KEY_VOICE) ?: return null
                )
            }
        }
    }

    /**
     *  The recorded sentences in the phone's language, if there are any. Read inside Phone's
     *  process; the pack's own locale tag (tr-TR) is used even if the phone's region differs.
     */
    fun findReady(filesDir: File, phoneLocale: Locale): File? =
        packRoot(filesDir).listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .filter { Locale.forLanguageTag(it.name).language == phoneLocale.language }
            .sortedByDescending { Locale.forLanguageTag(it.name).country == phoneLocale.country }
            .firstOrNull { directory ->
                val marker = File(directory, MARKER).takeIf { it.isFile }?.readText()?.let(Marker::read)
                marker?.locale == directory.name && PROMPTS.all { recording(directory, it).isFile }
            }

}
