package com.kieronquinn.app.pcs.xposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

/**
 *  Lets Call Notes, Call Recording and Take a Message announce themselves in countries Google has
 *  not launched them in.
 *
 *  Before recording, Phone plays a disclaimer to the other party. `DisclaimerLocaleResolver` picks
 *  its language from a fixed map of network country to disclaimer locales
 *  (`"de" -> [de-DE]`, `"ch" -> [de-DE, fr-FR, it-IT]`, …); for any other country it finds no
 *  locale, downloads no disclaimer audio ("No available disclaimer audio for the current country")
 *  and the feature cannot start.
 *
 *  The map is built once by a lambda. Its result is wrapped so a country missing from it gets every
 *  disclaimer locale Phone ships, en-US first. The resolver then uses its own rule: the locale whose
 *  language matches the phone's language, otherwise the first one. Countries in Google's map are
 *  unchanged.
 */
internal object DisclaimerCountryHooks {

    private const val TAG = "PhoneHooks"
    private const val FALLBACK_LOCALE = "en-US"

    /**
     *  Country codes found together only in the disclaimer map ("br" sets it apart from the
     *  country lists of other Phone features).
     */
    private val MAP_COUNTRIES = arrayOf("at", "br", "cz", "sg", "tw", "us")

    fun install(dexKit: DexKitBridge, classLoader: ClassLoader) {
        val method = try {
            dexKit.findMethod {
                matcher {
                    usingEqStrings(*MAP_COUNTRIES)
                    paramCount = 0
                    returnType = "java.lang.Object"
                }
            }.singleOrNull()?.getMethodInstance(classLoader)
                ?: error("disclaimer country map not found")
        } catch (error: Throwable) {
            log("Disclaimer country fallback unavailable: $error")
            return
        }
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                @Suppress("UNCHECKED_CAST")
                val map = param.result as? Map<Any?, Any?> ?: return
                val fallback = fallbackLocales(map) ?: run {
                    log("Disclaimer country fallback: unexpected map values, keeping Google's map")
                    return
                }
                param.result = CountryFallbackMap(map, fallback)
            }
        })
        log("Disclaimer country fallback installed on ${method.declaringClass.name}.${method.name}")
    }

    /**
     *  Every constant of the map's locale enum, en-US first. The enum is found from the map's own
     *  values, so no class name is needed.
     */
    private fun fallbackLocales(map: Map<Any?, Any?>): List<Any>? {
        val sample = map.values.asSequence().filterIsInstance<List<*>>().flatten().firstOrNull()
            ?: return null
        val type = sample.javaClass.takeIf { it.isEnum } ?: sample.javaClass.superclass
            ?.takeIf { it.isEnum } ?: return null
        val constants = type.enumConstants?.toList()?.filterNotNull() ?: return null
        val english = constants.firstOrNull { tag(it) == FALLBACK_LOCALE } ?: return null
        return listOf(english) + constants.filter { it !== english }
    }

    /** The locale tag the enum constant carries, such as "en-US". */
    private fun tag(constant: Any): String? = constant.javaClass.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .firstNotNullOfOrNull { field ->
            field.isAccessible = true
            (field.get(constant) as? String)?.takeIf { it.contains('-') }
        }

    private class CountryFallbackMap(
        private val base: Map<Any?, Any?>,
        private val fallback: List<Any>
    ) : Map<Any?, Any?> by base {
        private val logged = HashSet<Any?>()

        override fun get(key: Any?): Any? {
            base[key]?.let { return it }
            if (key !is String) return null
            synchronized(logged) {
                if (logged.add(key)) log("No disclaimer locale for country \"$key\"; offering all, $FALLBACK_LOCALE first")
            }
            return fallback
        }

        override fun containsKey(key: Any?) = key is String || base.containsKey(key)
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
