package com.kieronquinn.app.pcs.callscreen.pack

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.Reader
import java.util.Locale

/**
 *  A translation of Call Screen's spoken and displayed text, shipped with PCS as
 *  `assets/callscreen/<language>.json`. It contains only translated text; Google's pack and audio
 *  are never included. The pack is localised on the device from the copy Phone downloads.
 */
class CallScreenTranslation private constructor(
    val locale: Locale,
    val speechRate: Float,
    /** Audio path in the pack (without ".wav") to the sentence spoken and shown for it. */
    val prompts: Map<String, String>,
    private val texts: Map<String, String>,
    val chips: Map<String, String>,
    val voiceTriggers: Map<String, List<String>>
) {

    class InvalidException(message: String) : Exception(message)

    private val normalised = texts.mapKeys { normalise(it.key) }
    private val loose = texts.mapKeys { loose(it.key) }

    /** Every distinct sentence that needs to be synthesised. */
    val sentences: List<String> = prompts.values.distinct().sorted()

    /**
     *  The translation for a prompt. The text of the recording is used when the prompt has an
     *  audio path, so the transcript on screen always matches what the caller hears.
     */
    fun translate(audioPath: String?, english: String): String? {
        if (audioPath != null) prompts[audioPath]?.let { return it }
        return normalised[normalise(english)] ?: loose[loose(english)]
    }

    companion object {
        private const val FORMAT_VERSION = 1

        fun parse(reader: Reader): CallScreenTranslation {
            val data = Gson().fromJson(reader, Data::class.java)
                ?: throw InvalidException("Empty translation")
            if (data.version != FORMAT_VERSION) {
                throw InvalidException("Unsupported translation version ${data.version}")
            }
            val locale = data.locale?.let { Locale.forLanguageTag(it) }
                ?.takeIf { it.language.isNotEmpty() }
                ?: throw InvalidException("Missing locale")
            val prompts = data.prompts.orEmpty()
            val texts = data.texts.orEmpty()
            if (prompts.isEmpty()) throw InvalidException("No prompts")
            (prompts.values + texts.values + data.chips.orEmpty().values).firstOrNull { it.isBlank() }
                ?.let { throw InvalidException("Blank translation") }
            return CallScreenTranslation(
                locale = locale,
                speechRate = data.tts?.rate?.takeIf { it in 0.5f..2f } ?: 1f,
                prompts = prompts,
                texts = texts,
                chips = data.chips.orEmpty(),
                voiceTriggers = data.voiceTriggers.orEmpty()
            )
        }

        /** Curly quotes, NBSP, dashes and spacing differ between pack versions of the same text. */
        internal fun normalise(text: String): String {
            val replaced = text
                .replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('—', '-').replace(' ', ' ')
                // Newer packs use semicolons where older ones used commas.
                .replace(';', ',')
            return replaced.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        }

        /** Ignores punctuation and case, for the same sentence written differently. */
        internal fun loose(text: String): String =
            normalise(text).lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }

    private class Data(
        val version: Int = 0,
        val locale: String? = null,
        val tts: Tts? = null,
        val prompts: Map<String, String>? = null,
        val texts: Map<String, String>? = null,
        val chips: Map<String, String>? = null,
        @SerializedName("voiceTriggers") val voiceTriggers: Map<String, List<String>>? = null
    )

    private class Tts(val rate: Float? = null)

}
