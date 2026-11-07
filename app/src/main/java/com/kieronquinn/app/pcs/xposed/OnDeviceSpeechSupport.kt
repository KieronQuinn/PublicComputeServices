package com.kieronquinn.app.pcs.xposed

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import de.robv.android.xposed.XposedBridge
import java.util.Locale

/**
 *  Whether on-device speech recognition supports a language, asked through the public
 *  [SpeechRecognizer.checkRecognitionSupport] API. On Pixels this is backed by the same Google
 *  speech (SODA) language packs that Dialer downloads for its own features, so a language listed as
 *  installed, supported or pending can be downloaded and used by Dialer as well.
 *
 *  The check is asynchronous and the recognizer has to be created on the main thread, so it is
 *  started once when the process starts and the result is cached. Until it arrives, or if it
 *  fails, [isSupported] returns false and callers keep Dialer's own behaviour.
 */
internal class OnDeviceSpeechSupport(private val tag: String, private val locale: Locale) {

    @Volatile
    var isSupported = false
        private set

    fun start(context: Context) {
        Handler(Looper.getMainLooper()).post {
            try {
                check(context)
            } catch (e: Exception) {
                log("Unable to check on-device speech support for ${locale.toLanguageTag()}: $e")
            }
        }
    }

    private fun check(context: Context) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            log("On-device speech recognition is not available")
            return
        }
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        recognizer.checkRecognitionSupport(intent, context.mainExecutor,
            object: RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val languages = support.installedOnDeviceLanguages +
                            support.supportedOnDeviceLanguages +
                            support.pendingOnDeviceLanguages
                    isSupported = languages.any { it.matches(locale) }
                    log("On-device speech support for ${locale.toLanguageTag()}: $isSupported")
                    recognizer.destroy()
                }

                override fun onError(error: Int) {
                    log("On-device speech support check for ${locale.toLanguageTag()} failed: $error")
                    recognizer.destroy()
                }
            })
    }

    /**
     *  Language packs are named by language and region (tr-TR, en-US). A pack for another region
     *  of the same language does not count when both have a region.
     */
    private fun String.matches(locale: Locale): Boolean {
        val other = Locale.forLanguageTag(this)
        if (!other.language.equals(locale.language, ignoreCase = true)) return false
        return other.country.isEmpty() || locale.country.isEmpty() ||
                other.country.equals(locale.country, ignoreCase = true)
    }

    private fun log(message: String) {
        XposedBridge.log("$tag: $message")
    }

}
