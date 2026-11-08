package com.kieronquinn.app.pcs.translation

import android.content.res.Resources
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 *  On-device translation from English into the phone's language, with ML Kit Translation and
 *  Language ID. The language model is downloaded once (about 30 MB) by [TranslationModelWorker];
 *  text is translated on the phone and never leaves it.
 *
 *  Calls block: use them off the main thread.
 */
object PhoneLanguageTranslator {

    enum class Status { OK, MODEL_NOT_READY, UNSUPPORTED, ERROR }

    class Result(val status: Status, val lines: List<String>? = null)

    private const val ENGLISH_TAG = "en"
    /** A line counts as English when Language ID's best guess is English with at least this. */
    private const val ENGLISH_CONFIDENCE = 0.5f
    /** …and the phone's language is below this. */
    private const val TARGET_CONFIDENCE = 0.5f
    private const val IDENTIFY_TIMEOUT_SECONDS = 5L
    private const val TRANSLATE_TIMEOUT_SECONDS = 20L
    private const val CHECK_TIMEOUT_SECONDS = 10L
    private const val TO_ENGLISH_TIMEOUT_SECONDS = 5L

    private val lock = Any()
    private var translator: Pair<String, Translator>? = null
    private var toEnglish: Pair<String, Translator>? = null
    private val languageId: LanguageIdentifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.01f).build()
        )
    }

    fun phoneLocale(): Locale = Resources.getSystem().configuration.locales.get(0)

    /** ML Kit's code for [locale]'s language, or null if ML Kit cannot translate English into it. */
    fun targetLanguage(locale: Locale = phoneLocale()): String? =
        TranslateLanguage.fromLanguageTag(locale.language)?.takeIf { it != TranslateLanguage.ENGLISH }

    fun isModelDownloaded(target: String): Boolean = Tasks.await(
        RemoteModelManager.getInstance().isModelDownloaded(TranslateRemoteModel.Builder(target).build()),
        CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS
    )

    /** Downloads the model for [target] if it is not there yet; blocks until it is. */
    fun downloadModel(target: String) {
        Tasks.await(translator(target).downloadModelIfNeeded(DownloadConditions.Builder().build()))
    }

    /**
     *  [lines] with those Language ID reads as English translated into the phone's language. Lines
     *  already in the phone's language, or in no clear language, are returned unchanged, so text
     *  the model wrote correctly is never machine translated.
     */
    fun fromEnglish(lines: List<String>): Result {
        val target = targetLanguage() ?: return Result(Status.UNSUPPORTED)
        val english = try {
            lines.map { it.isNotBlank() && isEnglish(it, target) }
        } catch (e: Exception) {
            return Result(Status.ERROR)
        }
        if (english.none { it }) return Result(Status.OK, lines)
        try {
            if (!isModelDownloaded(target)) return Result(Status.MODEL_NOT_READY)
            val translator = translator(target)
            val result = lines.mapIndexed { index, line ->
                if (!english[index]) return@mapIndexed line
                Tasks.await(translator.translate(line), TRANSLATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    ?.takeIf { it.isNotBlank() } ?: return Result(Status.ERROR)
            }
            return Result(Status.OK, result)
        } catch (e: Exception) {
            return Result(Status.ERROR)
        }
    }

    /**
     *  [text] in English if Language ID reads it as the phone's language, else
     *  [Status.UNSUPPORTED] with no text: a caller then uses its original text. For English-only
     *  models given text in the phone's language.
     */
    fun toEnglish(text: String, timeoutSeconds: Long = TO_ENGLISH_TIMEOUT_SECONDS): Result {
        val target = targetLanguage() ?: return Result(Status.UNSUPPORTED)
        if (text.isBlank()) return Result(Status.UNSUPPORTED)
        return try {
            val found = Tasks.await(languageId.identifyPossibleLanguages(text), timeoutSeconds, TimeUnit.SECONDS)
            val phone = found.filter { TranslateLanguage.fromLanguageTag(it.languageTag) == target }
                .maxOfOrNull { it.confidence } ?: 0f
            if (phone < TARGET_CONFIDENCE) return Result(Status.UNSUPPORTED)
            if (!isModelDownloaded(target)) return Result(Status.MODEL_NOT_READY)
            val english = Tasks.await(toEnglishTranslator(target).translate(text), timeoutSeconds, TimeUnit.SECONDS)
                ?.takeIf { it.isNotBlank() } ?: return Result(Status.ERROR)
            Result(Status.OK, listOf(english))
        } catch (e: Exception) {
            Result(Status.ERROR)
        }
    }

    private fun isEnglish(line: String, target: String): Boolean {
        val found = Tasks.await(languageId.identifyPossibleLanguages(line), IDENTIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val best = found.maxByOrNull { it.confidence } ?: return false
        val english = found.firstOrNull { it.languageTag == ENGLISH_TAG }?.confidence ?: 0f
        val phone = found.filter { TranslateLanguage.fromLanguageTag(it.languageTag) == target }
            .maxOfOrNull { it.confidence } ?: 0f
        return best.languageTag == ENGLISH_TAG && english >= ENGLISH_CONFIDENCE && phone < TARGET_CONFIDENCE
    }

    /** The phone's language to English; the same downloaded model serves both directions. */
    private fun toEnglishTranslator(target: String): Translator = synchronized(lock) {
        toEnglish?.takeIf { it.first == target }?.second ?: run {
            toEnglish?.second?.close()
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(target)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
            ).also { toEnglish = target to it }
        }
    }

    private fun translator(target: String): Translator = synchronized(lock) {
        translator?.takeIf { it.first == target }?.second ?: run {
            translator?.second?.close()
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(target)
                    .build()
            ).also { translator = target to it }
        }
    }

}
