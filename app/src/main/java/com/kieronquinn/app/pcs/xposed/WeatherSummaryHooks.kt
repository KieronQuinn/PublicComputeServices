package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.WEATHER_SUMMARY_LANGUAGE
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import com.kieronquinn.app.pcs.weather.WeatherSummary
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

/**
 *  Offers Pixel Weather's AI summary in the phone's language ([WeatherSummary]).
 *
 *  The phone's language is added to the offered languages ([TikTokListFlagHooks]). The model
 *  selector and the prompt builder refuse any language they do not know; only when they do, the
 *  call is made again with [WeatherSummary.STAND_IN] as the language, so the model comes from the
 *  app's own table, and the prompt then names the phone's language. Supported languages are left
 *  alone.
 */
object WeatherSummaryHooks : XposedHooks {

    override val tag = "WeatherHooks"

    private const val MODEL_REFUSAL = "Device language: "
    private const val REQUEST_REFUSAL = "Unsupported request type: "
    private const val SUMMARY_PROMPT = "Write a short and engaging weather summary in German"

    /** Set while the model selector is called again, so the locale getter returns the stand in. */
    private val standIn = ThreadLocal<Boolean>()

    /** The prompt builder's locale field is changed for the call, so calls take turns. */
    private val promptLock = ReentrantLock()

    override fun hook(loadPackageParam: LoadPackageParam) {
        if (!SystemProperties_getBoolean(WEATHER_SUMMARY_LANGUAGE, false)) return
        try {
            TikTokListFlagHooks(this, WeatherSummary.PACKAGE, setOf(WeatherSummary.SUPPORTED_LANGUAGES)) {
                Locale.getDefault().language.takeIf { it.isNotBlank() }
            }.install(loadPackageParam)
        } catch (error: Throwable) {
            log("Weather summary languages unavailable: $error")
        }
        try {
            val dexKit = loadDexKit(loadPackageParam.appInfo.sourceDir)
            hookModelSelector(dexKit, loadPackageParam.classLoader)
            hookPromptBuilder(dexKit, loadPackageParam.classLoader)
            log("Weather summary language installed")
        } catch (error: Throwable) {
            log("Weather summary language unavailable: $error")
        }
    }

    /**
     *  The model selector maps the phone's language, read through a static locale getter, to a
     *  model. When it refuses the language, it is called again with the getter returning
     *  [WeatherSummary.STAND_IN].
     */
    private fun hookModelSelector(dexKit: DexKitBridge, classLoader: ClassLoader) {
        val selectorData = dexKit.findMethod {
            matcher {
                usingStrings(listOf(REQUEST_REFUSAL, MODEL_REFUSAL), StringMatchType.Contains)
                returnType(Int::class.javaPrimitiveType!!.name)
                paramCount(1)
            }
        }.single()
        val getter = selectorData.invokes.filter {
            it.isMethod && it.paramCount == 0 && it.returnTypeName == Locale::class.java.name &&
                !it.className.startsWith("java.") && Modifier.isStatic(it.modifiers)
        }.distinct().single().getMethodInstance(classLoader)
        val selector = selectorData.getMethodInstance(classLoader)
        XposedBridge.hookMethod(getter, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (standIn.get() == true && !param.hasThrowable()) {
                    param.result = WeatherSummary.STAND_IN
                }
            }
        })
        XposedBridge.hookMethod(selector, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!WeatherSummary.isUnsupportedLanguage(param.throwable)) return
                if (standIn.get() == true) return
                standIn.set(true)
                try {
                    param.result = XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                } catch (error: InvocationTargetException) {
                    // The stand in was refused too; the app's own error stays.
                } finally {
                    standIn.remove()
                }
            }
        })
        // The getter is small enough to be inlined into the compiled selector, which would skip
        // its hook; run the selector in the interpreter.
        deoptimize(selector)
    }

    /**
     *  The prompt builder picks its prompt from the language in its locale field. When it refuses
     *  the language, it is called again with the field set to [WeatherSummary.STAND_IN] and the
     *  prompt is changed to name the phone's language. The method naming the language in other
     *  prompts gets the phone's language name.
     */
    private fun hookPromptBuilder(dexKit: DexKitBridge, classLoader: ClassLoader) {
        val prompt = dexKit.findMethod {
            matcher { usingStrings(listOf(SUMMARY_PROMPT), StringMatchType.Contains) }
        }.single().getMethodInstance(classLoader)
        val builder = prompt.declaringClass
        val localeField = builder.declaredFields.single {
            it.type == Locale::class.java && !Modifier.isStatic(it.modifiers)
        }.apply { isAccessible = true }
        XposedBridge.hookMethod(prompt, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!WeatherSummary.isUnsupportedLanguage(param.throwable)) return
                val locale = localeField.get(param.thisObject) as? Locale ?: return
                val languageName = WeatherSummary.languageName(locale) ?: return
                val result = withStandIn(param, localeField, locale) ?: return
                if (renamePrompt(result, languageName)) param.result = result
            }
        })
        builder.declaredMethods.filter {
            it.parameterCount == 0 && it.returnType == String::class.java && !Modifier.isStatic(it.modifiers)
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!WeatherSummary.isUnsupportedLanguageName(param.throwable)) return
                    val locale = localeField.get(param.thisObject) as? Locale ?: return
                    param.result = WeatherSummary.languageName(locale) ?: return
                }
            })
        }
    }

    /** The prompt builder's result for [WeatherSummary.STAND_IN], or null when it is refused. */
    private fun withStandIn(param: XC_MethodHook.MethodHookParam, localeField: Field, locale: Locale): Any? {
        promptLock.lock()
        try {
            localeField.set(param.thisObject, WeatherSummary.STAND_IN)
            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
        } catch (error: InvocationTargetException) {
            return null
        } finally {
            localeField.set(param.thisObject, locale)
            promptLock.unlock()
        }
    }

    /** Names [languageName] in the prompt strings of [result]; false when none is a prompt. */
    private fun renamePrompt(result: Any, languageName: String): Boolean {
        var renamed = false
        result.javaClass.declaredFields.filter {
            it.type == String::class.java && !Modifier.isStatic(it.modifiers)
        }.forEach { field ->
            field.isAccessible = true
            val prompt = field.get(result) as? String ?: return@forEach
            field.set(result, WeatherSummary.promptFor(prompt, languageName) ?: return@forEach)
            renamed = true
        }
        return renamed
    }

}
