package com.kieronquinn.app.pcs.xposed

import android.content.Context
import com.kieronquinn.app.pcs.voicepack.HoldForMePrompts
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Modifier

/**
 *  Plays Hold for Me's sentences to the other party in the phone's language, from the recordings
 *  PCS makes (see [HoldForMePrompts]).
 *
 *  `HoldForMeLocalAudioAssetFetcher` returns the file to play for a sentence: Phone's recording,
 *  copied to its cache as `hfm-audio-<prompt>-<language>`, and falls back to English for other
 *  languages. When PCS has recorded the sentences in the phone's language, the fetcher answers
 *  with that recording instead. Phone's cache is not changed.
 */
internal object HoldForMeVoiceHooks {

    private const val TAG = "PhoneHooks"
    private const val FETCHER = "com/android/incallui/atlas/ui/impl/audioassets/local/HoldForMeLocalAudioAssetFetcher"

    fun install(dexKit: DexKitBridge, classLoader: ClassLoader, context: Context) {
        val filesDir = context.filesDir
        val phoneLocale = context.resources.configuration.locales.get(0)
        try {
            val fetcher = dexKit.findClass {
                matcher { usingStrings(FETCHER, "hfm-audio-%s-%s") }
            }.single().getInstance(classLoader)
            // getOrCreateCacheFile(prompt, language): the public method taking the prompt number.
            val fetch = fetcher.declaredMethods.single {
                Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 2 && it.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            val logged = HashSet<String>()
            XposedBridge.hookMethod(fetch, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    val file = optionalValue(result) as? File ?: return
                    val prompt = HoldForMePrompts.promptOf(file.name) ?: return
                    val directory = HoldForMePrompts.findReady(filesDir, phoneLocale) ?: return
                    val recording = HoldForMePrompts.recording(directory, prompt)
                    param.result = result.javaClass.getMethod("of", Any::class.java).invoke(null, recording)
                    synchronized(logged) {
                        if (logged.add(prompt)) log("Hold for Me says $prompt from ${recording.path}")
                    }
                }
            })
            log("Hold for Me voice hooks installed for ${phoneLocale.toLanguageTag()}")
        } catch (error: Exception) {
            log("Hold for Me voice unavailable: $error")
        }
    }

    /** The value of a present `Optional` (Java's or the desugared one), or null. */
    private fun optionalValue(optional: Any): Any? {
        val type = optional.javaClass
        val present = type.getMethod("isPresent").invoke(optional) as? Boolean ?: return null
        return if (present) type.getMethod("get").invoke(optional) else null
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
