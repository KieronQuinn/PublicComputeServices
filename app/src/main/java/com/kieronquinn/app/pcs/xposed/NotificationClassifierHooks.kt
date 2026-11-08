package com.kieronquinn.app.pcs.xposed

import android.content.Context
import com.kieronquinn.app.pcs.translation.TranslationProvider
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.util.Collections

/**
 *  Lets Notification Organizer classify notifications in the phone's language.
 *
 *  ASI sorts notifications into bundles (promotions, news, social…) with a classifier model
 *  trained on English only: a promotion in another language comes out as unknown, the same text in
 *  English as a promotion. Just before the text goes to the model, it is translated into English on
 *  the device ([TranslationProvider]). Only the model's input changes: the notification, the app's
 *  name and category, and every later decision stay ASI's. Text in another language, or before
 *  the translation model is downloaded, goes to the model unchanged. Notification text is never
 *  logged.
 *
 *  The classifier method is found from its log line and its signature
 *  (model type, model handle, text) to scores.
 */
internal object NotificationClassifierHooks {

    private const val TAG = "AsHooks"
    private const val UNKNOWN_MODEL_LOG = "classifyByModelType: unknown model type!"
    /** A notification is classified again when it is updated; translate it once. */
    private const val CACHE_SIZE = 128

    private val cache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > CACHE_SIZE
        }
    )

    fun install(dexKit: DexKitBridge, classLoader: ClassLoader, context: () -> Context?) {
        try {
            val classify = dexKit.findMethod { matcher { usingStrings(UNKNOWN_MODEL_LOG) } }
                .map { it.getMethodInstance(classLoader) }
                .single {
                    it.parameterCount == 3 && it.parameterTypes[0].isEnum &&
                        it.parameterTypes[1] == Long::class.javaPrimitiveType &&
                        it.parameterTypes[2] == String::class.java &&
                        it.returnType == FloatArray::class.java
                }
            XposedBridge.hookMethod(classify, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val text = param.args[2] as? String ?: return
                    if (text.isBlank()) return
                    try {
                        val english = cache[text] ?: run {
                            val appContext = context() ?: return
                            TranslationProvider.toEnglish(appContext, text)?.also { cache[text] = it }
                        } ?: return
                        param.args[2] = english
                    } catch (error: Throwable) {
                        log("Notification text left untranslated: ${error.javaClass.simpleName}")
                    }
                }
            })
            log("Notification classifier translation installed on ${classify.declaringClass.name}.${classify.name}")
        } catch (error: Throwable) {
            log("Notification classifier translation unavailable: $error")
        }
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
