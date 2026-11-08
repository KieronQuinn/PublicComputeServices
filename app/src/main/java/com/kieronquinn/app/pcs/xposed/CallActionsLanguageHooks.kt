package com.kieronquinn.app.pcs.xposed

import android.content.Context
import com.kieronquinn.app.pcs.callnotes.CallActionsText
import com.kieronquinn.app.pcs.translation.TranslationProvider
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge

/**
 *  Shows Call Notes' suggested tasks in the phone's language.
 *
 *  The suggested-tasks model gets only the call's transcript, with no language, so it writes some
 *  tasks in English even for a call in another language. Before `CallActionsParser` reads the
 *  model's output, the tasks' text that is English is translated into the phone's language on the
 *  device, through PCS ([TranslationProvider]). The task saved, shown, and passed on to a reminder
 *  or calendar invite is then the same text. When translation cannot be done (no model yet), the
 *  output is passed on unchanged and no task is lost.
 */
internal object CallActionsLanguageHooks {

    private const val TAG = "PhoneHooks"
    private const val PARSER = "com/android/dialer/callnotesinfra/summarizer/callaction/CallActionsParser"

    fun install(dexKit: DexKitBridge, classLoader: ClassLoader, context: Context) {
        try {
            // parse(String modelOutput, ZonedDateTime callTime): List<CallAction>
            val parse = dexKit.findClass { matcher { usingStrings(PARSER) } }
                .map { it.getInstance(classLoader) }
                .flatMap { type ->
                    type.declaredMethods.filter {
                        it.parameterCount == 2 &&
                            it.parameterTypes[0] == String::class.java &&
                            it.parameterTypes[1].name.endsWith(".time.ZonedDateTime") &&
                            List::class.java.isAssignableFrom(it.returnType)
                    }
                }.single()
            XposedBridge.hookMethod(parse, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val output = param.args[0] as? String ?: return
                        val tasks = CallActionsText.parse(output) ?: return
                        val translated = TranslationProvider.fromEnglish(context, tasks.texts, ::log) ?: return
                        tasks.replace(translated)?.let {
                            param.args[0] = it
                            log("Call Notes suggested tasks shown in the phone's language")
                        }
                    } catch (error: Throwable) {
                        log("Call Notes suggested tasks left as they are: $error")
                    }
                }
            })
            log("Call Notes suggested tasks hook installed: ${parse.declaringClass.name}.${parse.name}")
        } catch (error: Throwable) {
            log("Call Notes suggested tasks hook unavailable: $error")
        }
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
