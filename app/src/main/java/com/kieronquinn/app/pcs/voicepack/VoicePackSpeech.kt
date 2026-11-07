package com.kieronquinn.app.pcs.voicepack

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 *  Speaks a voice pack's sentences into files with the device's own text-to-speech engine, through
 *  the public [TextToSpeech] API. Google's engine is preferred when it is installed, since it has
 *  the voices Phone itself uses.
 */
internal class VoicePackSpeech private constructor(
    private val tts: TextToSpeech,
    val voice: Voice
) : Closeable {

    open class UnavailableException(message: String) : Exception(message)

    /** The engine works, but has no installed voice for the language. */
    class NoVoiceException(locale: Locale) :
        UnavailableException("No ${locale.displayName} voice is installed")

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                pending.remove(utteranceId)?.complete(Unit)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                pending.remove(utteranceId)?.completeExceptionally(
                    UnavailableException("Speech synthesis failed")
                )
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                pending.remove(utteranceId)?.completeExceptionally(
                    UnavailableException("Speech synthesis failed with error $errorCode")
                )
            }
        })
    }

    /** Writes [text] to [file] as WAV. */
    suspend fun synthesise(text: String, file: File) {
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        file.delete()
        val result = tts.synthesizeToFile(text, Bundle(), file, id)
        if (result != TextToSpeech.SUCCESS) {
            pending.remove(id)
            throw UnavailableException("Speech synthesis was not accepted")
        }
        try {
            withTimeout(SYNTHESIS_TIMEOUT_MS) { done.await() }
        } finally {
            pending.remove(id)
        }
        if (!file.isFile || file.length() <= 44) {
            throw UnavailableException("Speech synthesis wrote no audio")
        }
    }

    override fun close() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val GOOGLE_TTS = "com.google.android.tts"
        private const val SYNTHESIS_TIMEOUT_MS = 60_000L
        private const val INIT_TIMEOUT_MS = 30_000L

        suspend fun open(context: Context, locale: Locale, rate: Float): VoicePackSpeech {
            val engine = GOOGLE_TTS.takeIf { isTtsEngine(context, it) }
            val tts = withTimeout(INIT_TIMEOUT_MS) { create(context, engine) }
            try {
                val voice = selectVoice(tts.voices.orEmpty(), locale)
                    ?: throw NoVoiceException(locale)
                if (tts.setVoice(voice) != TextToSpeech.SUCCESS) {
                    throw UnavailableException("Unable to use voice ${voice.name}")
                }
                tts.setSpeechRate(rate)
                return VoicePackSpeech(tts, voice)
            } catch (e: Exception) {
                tts.shutdown()
                throw e
            }
        }

        private fun isTtsEngine(context: Context, packageName: String): Boolean {
            val intent = android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
                .setPackage(packageName)
            return context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
        }

        private suspend fun create(context: Context, engine: String?): TextToSpeech =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    var instance: TextToSpeech? = null
                    val listener = TextToSpeech.OnInitListener { status ->
                        val tts = instance ?: return@OnInitListener
                        if (status == TextToSpeech.SUCCESS) {
                            continuation.resume(tts)
                        } else {
                            tts.shutdown()
                            continuation.cancel(UnavailableException("Text-to-speech engine failed to start"))
                        }
                    }
                    instance = if (engine != null) {
                        TextToSpeech(context.applicationContext, listener, engine)
                    } else {
                        TextToSpeech(context.applicationContext, listener)
                    }
                    continuation.invokeOnCancellation { instance.shutdown() }
                }
            }

        /**
         *  The best installed voice for the locale: same language, same country when the locale
         *  has one, highest quality, and a voice that works offline when quality is equal.
         */
        internal fun selectVoice(voices: Collection<Voice>, locale: Locale): Voice? {
            return voices.filter { voice ->
                voice.locale.language == locale.language &&
                    (locale.country.isEmpty() || voice.locale.country == locale.country) &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
            }.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name }
            ).firstOrNull()
        }
    }

}
