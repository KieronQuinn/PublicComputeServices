package com.kieronquinn.app.pcs.xposed

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 *  Lets Phone's on-device speech recognition (SODA) run in languages its own locale map leaves out.
 *
 *  Every SODA session asks `SodaOnDeviceGrpcSession` for its recognition locale. The session takes
 *  the locale a feature passed (Call Screen) or the phone's locale (Hold for Me), looks it up in a
 *  fixed map of supported tags and falls back to en-US for any tag it does not list. A Turkish
 *  phone therefore gets English recognition ("Initialize Soda [locale: en-US]") and a transcript of
 *  English words, even when the Turkish speech pack is installed.
 *
 *  Features [register] the tag they want answered while they are active. When the map has no entry
 *  for a requested tag, a registered feature wants exactly that tag, and on-device recognition
 *  supports the language, the lookup returns that tag instead of en-US. The lookup does not say
 *  which feature asked, so the answer applies to any session asking for that same tag; tags Phone
 *  lists are never changed.
 */
internal object SodaLocaleHooks {

    private const val TAG = "PhoneHooks"
    private const val SODA_SESSION =
        "com/android/dialer/sodatranscription/impl/ondevicegrpc/SodaOnDeviceGrpcSession"

    /**
     *  Phone is desugared: its `Map.getOrDefault` calls go through these library bridges. The
     *  hook only answers for the SODA locale map instance, every other map is passed through.
     */
    private val MAP_LOOKUPS = listOf(
        "j\$.util.Map\$-EL" to "getOrDefault",
        "j\$.util.Map\$-CC" to "\$default\$getOrDefault"
    )

    /** A feature that wants SODA in [tag]'s language while it returns a tag. */
    class Use(val feature: String, val tag: () -> String?)

    private val uses = CopyOnWriteArrayList<Use>()
    private val support = ConcurrentHashMap<String, OnDeviceSpeechSupport>()
    private val logged = ConcurrentHashMap.newKeySet<String>()
    private lateinit var context: Context
    private var state: Boolean? = null

    /**
     *  Adds [use] and installs the lookup hook on first use. [locale] is the language the feature
     *  is expected to ask for; its on-device support is checked now so the answer is ready before
     *  the first call.
     */
    @Synchronized
    fun register(
        dexKit: DexKitBridge,
        classLoader: ClassLoader,
        context: Context,
        locale: Locale,
        use: Use
    ) {
        this.context = context.applicationContext ?: context
        supportFor(locale.toLanguageTag())
        if (state == null) state = install(dexKit, classLoader)
        if (state == true) {
            uses += use
            log("${use.feature} speech recognition can use ${locale.toLanguageTag()}")
        }
    }

    private fun install(dexKit: DexKitBridge, classLoader: ClassLoader): Boolean {
        return try {
            val map = findLocaleMap(dexKit, classLoader)
            val factories = findLocaleFactories(map)
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args.size != 3 || param.args[0] !== map) return
                    val tag = param.args[1] as? String ?: return
                    if (map.containsKey(tag)) return
                    val features = uses.filter { it.tag() == tag }.map { it.feature }
                    if (features.isEmpty() || !supportFor(tag).isSupported) return
                    val value = factories.firstNotNullOfOrNull { factory ->
                        runCatching { factory.invoke(null, tag) }.getOrNull()
                            ?.takeIf { it.toString() == tag }
                    } ?: return
                    param.result = value
                    if (logged.add(tag)) {
                        log("Speech recognition for ${features.joinToString()} uses $tag instead of Phone's en-US fallback")
                    }
                }
            }
            val hooked = MAP_LOOKUPS.sumOf { (type, name) ->
                val lookup = runCatching { classLoader.loadClass(type) }.getOrNull()
                    ?: return@sumOf 0
                XposedBridge.hookAllMethods(lookup, name, hook).size
            }
            check(hooked > 0) { "no Map.getOrDefault bridge" }
            true
        } catch (error: Exception) {
            log("Speech recognition locale unavailable: $error")
            false
        }
    }

    /**
     *  The session's static map from locale tag to Phone's locale type. The session and its
     *  lambdas share the logger string; the session is the one class holding such a map.
     */
    private fun findLocaleMap(dexKit: DexKitBridge, classLoader: ClassLoader): Map<*, *> {
        return dexKit.findClass {
            matcher { usingStrings(SODA_SESSION) }
        }.map { it.getInstance(classLoader) }.flatMap { type ->
            type.declaredFields.filter {
                Modifier.isStatic(it.modifiers) && Map::class.java.isAssignableFrom(it.type)
            }.map { it.isAccessible = true; it.get(null) }
        }.filterIsInstance<Map<*, *>>().singleOrNull {
            it.isNotEmpty() && it.keys.all { key -> key is String && key.contains('-') }
        } ?: error("SODA locale map not found")
    }

    /**
     *  The map's values are Phone's locale type, whose text form is the tag sent to SODA. It has
     *  several static String factories, some of which return a shared default for tags they do not
     *  accept, so the hook uses the first one whose result prints exactly the requested tag.
     */
    private fun findLocaleFactories(map: Map<*, *>): List<Method> {
        val valueType = map.values.first()!!.javaClass
        return valueType.declaredMethods.filter {
            Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) &&
                valueType.isAssignableFrom(it.returnType) &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }.ifEmpty { error("no locale factory in ${valueType.name}") }
    }

    private fun supportFor(tag: String): OnDeviceSpeechSupport = support.getOrPut(tag) {
        OnDeviceSpeechSupport(TAG, Locale.forLanguageTag(tag)).also { it.start(context) }
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
