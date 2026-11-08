package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.journal.JournalLanguage
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.JOURNAL_LANGUAGE
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

/**
 *  Offers Pixel Journal's AI features in the phone's language ([JournalLanguage]).
 *
 *  Nothing changes while the phone's language is one Journal supports. Otherwise:
 *  - the language set gets the phone's language, at its supplier and at every constructor its
 *    callers hand it to;
 *  - the per-language tables those constructors build, and the per-feature AICore tables checked
 *    before a feature runs, get an entry copied from English, with the language's own name;
 *  - the English reflection template asks for the phone's language;
 *  - the safety classifier is loaded, named and requested for English.
 *
 *  The language set supplier and the safety model name builders are small enough to be inlined
 *  into their callers, which would skip their hooks; the callers are run in the interpreter
 *  ([deoptimize]).
 */
object JournalLanguageHooks : XposedHooks {

    override val tag = "JournalHooks"

    private const val FEATURE_UNSUPPORTED = "Unsupported language %s for feature %s."
    private const val SAFETY_UNSUPPORTED = " is not eligible for safety check."
    private const val LABEL_BUILD = "build_id"
    private val SAFETY_MODEL_NAMES = listOf("safety-model-files-", "safety_model_tpu_")
    private const val ENGLISH_REFLECTIONS = "Daily reflections"

    /** The languages Journal supports itself, read from its supplier before it is hooked. */
    @Volatile
    private var supported: Set<*> = emptySet<Any>()

    private val featuresDone = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val prompts = Collections.synchronizedMap(WeakHashMap<Any, Any>())
    private val logged = Collections.synchronizedSet(HashSet<String>())

    override fun hook(loadPackageParam: LoadPackageParam) {
        if (!SystemProperties_getBoolean(JOURNAL_LANGUAGE, false)) return
        try {
            val dexKit = loadDexKit(loadPackageParam.appInfo.sourceDir)
            val classLoader = loadPackageParam.classLoader
            val consumers = hookLanguageSet(dexKit, classLoader)
            hookFeatureGate(dexKit, classLoader)
            hookSafety(dexKit, classLoader)
            hookReflection(dexKit, classLoader)
            log("Journal language installed, ${supported.size} supported languages, $consumers table(s)")
        } catch (error: Throwable) {
            log("Journal language unavailable: $error")
        }
    }

    /** The phone's language when Journal does not support it, else null. */
    private fun phoneLanguage(): String? {
        val language = Locale.getDefault().language
        return language.takeIf { it.isNotBlank() && it !in supported }
    }

    private fun languageName(language: String): String {
        return Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH)
    }

    private fun hookLanguageSet(dexKit: DexKitBridge, classLoader: ClassLoader): Int {
        val supplierData = dexKit.findMethod {
            matcher {
                modifiers(Modifier.STATIC)
                paramCount(0)
                returnType(Set::class.java.name)
                usingStrings(listOf(JournalLanguage.ENGLISH), StringMatchType.Equals)
            }
        }.filter { method ->
            method.usingStrings.size >= 2 && JournalLanguage.isLanguageSet(method.usingStrings.toSet())
        }.single()
        val supplier = supplierData.getMethodInstance(classLoader).apply { isAccessible = true }
        supported = supplier.invoke(null) as Set<*>
        check(JournalLanguage.isLanguageSet(supported)) { "Unexpected language set $supported" }
        XposedBridge.hookMethod(supplier, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val language = phoneLanguage() ?: return
                val set = param.result as? Set<*> ?: return
                param.result = JournalLanguage.withLanguage(set, language)?.let(Collections::unmodifiableSet) ?: return
            }
        })
        supplierData.callers.forEach { caller ->
            runCatching { caller.getMethodInstance(classLoader) }.getOrNull()?.let(::deoptimize)
        }
        val consumers = supplierData.callers.flatMap { it.invokes }
            .filter { it.isConstructor && Set::class.java.name in it.paramTypeNames }
            .distinctBy { it.descriptor }
            .mapNotNull { runCatching { it.getConstructorInstance(classLoader) }.getOrNull() }
        check(consumers.isNotEmpty()) { "No language set consumers" }
        consumers.forEach(::hookConsumer)
        return consumers.size
    }

    /**
     *  Hands [constructor] the language set with the phone's language, then gives the tables it
     *  built from the set an entry for that language.
     */
    private fun hookConsumer(constructor: Constructor<*>) {
        XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val language = phoneLanguage() ?: return
                param.args.forEachIndexed { index, arg ->
                    if (!JournalLanguage.isLanguageSet(arg)) return@forEachIndexed
                    val set = JournalLanguage.withLanguage(arg as Set<*>, language) ?: return@forEachIndexed
                    param.args[index] = Collections.unmodifiableSet(set)
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val language = phoneLanguage() ?: return
                if (param.args.none { it is Set<*> && language in it }) return
                addLanguage(param.thisObject, language)
            }
        })
    }

    /**
     *  The method checking a feature looks up the phone's language in the feature's table of AICore
     *  features; the table is given an entry before the first check.
     */
    private fun hookFeatureGate(dexKit: DexKitBridge, classLoader: ClassLoader) {
        val gate = dexKit.findMethod {
            matcher { usingStrings(listOf(FEATURE_UNSUPPORTED), StringMatchType.Equals) }
        }.single().getMethodInstance(classLoader)
        check(!Modifier.isStatic(gate.modifiers)) { "Static feature gate" }
        XposedBridge.hookMethod(gate, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val language = phoneLanguage() ?: return
                if (featuresDone.put(param.thisObject, true) == null) addLanguage(param.thisObject, language)
            }
        })
    }

    /**
     *  Google publishes the safety classifier for the supported languages only, so the phone's
     *  language is checked with the English one: the loader is called for English and the model
     *  list is requested for English.
     */
    private fun hookSafety(dexKit: DexKitBridge, classLoader: ClassLoader) {
        val loader = dexKit.findMethod {
            matcher { usingStrings(listOf(SAFETY_UNSUPPORTED), StringMatchType.Contains) }
        }.single().getMethodInstance(classLoader)
        check(!Modifier.isStatic(loader.modifiers) && loader.parameterTypes.firstOrNull() == String::class.java) {
            "Unexpected safety loader $loader"
        }
        XposedBridge.hookMethod(loader, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val language = phoneLanguage() ?: return
                if (param.args[0] != language) return
                param.args[0] = JournalLanguage.SAFETY_LANGUAGE
                logOnce("Checking $language text with the ${JournalLanguage.SAFETY_LANGUAGE} safety classifier")
            }
        })
        hookSafetyModelNames(dexKit, classLoader)
        val labels = dexKit.findMethod {
            matcher {
                usingStrings(listOf(LABEL_BUILD, JournalLanguage.LOCALE_LABEL), StringMatchType.Equals)
            }
        }.single().getMethodInstance(classLoader)
        XposedBridge.hookMethod(labels, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val language = phoneLanguage() ?: return
                val result = param.result as? Map<*, *> ?: return
                param.result = JournalLanguage.safetyLabels(result, language) ?: return
            }
        })
    }

    /**
     *  The safety model's file group and file are named after the language; both names are built
     *  for English, so the English model the labels request is the one looked up.
     */
    private fun hookSafetyModelNames(dexKit: DexKitBridge, classLoader: ClassLoader) {
        SAFETY_MODEL_NAMES.forEach { prefix ->
            val builderData = dexKit.findMethod {
                matcher {
                    modifiers(Modifier.STATIC)
                    returnType(String::class.java.name)
                    usingStrings(listOf(prefix), StringMatchType.Equals)
                }
            }.single()
            val builder = builderData.getMethodInstance(classLoader)
            check(builder.parameterTypes.firstOrNull() == String::class.java) {
                "Unexpected safety model name builder $builder"
            }
            XposedBridge.hookMethod(builder, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val language = phoneLanguage() ?: return
                    if (param.args[0] == language) param.args[0] = JournalLanguage.SAFETY_LANGUAGE
                }
            })
            // A static caller (the file name adds an extension to the name) can itself be inlined,
            // so its callers run in the interpreter too.
            builderData.callers.forEach { caller ->
                val method = runCatching { caller.getMethodInstance(classLoader) }.getOrNull() ?: return@forEach
                deoptimize(method)
                if (!Modifier.isStatic(method.modifiers)) return@forEach
                caller.callers.forEach { outer ->
                    runCatching { outer.getMethodInstance(classLoader) }.getOrNull()?.let(::deoptimize)
                }
            }
        }
    }

    /**
     *  The English reflection template is the one the phone's language gets (its table entry is
     *  copied from English); it is asked for the phone's language by name.
     */
    private fun hookReflection(dexKit: DexKitBridge, classLoader: ClassLoader) {
        val owner = dexKit.findMethod {
            matcher { usingStrings(listOf(ENGLISH_REFLECTIONS), StringMatchType.Equals) }
        }.single().getMethodInstance(classLoader).declaringClass
        val template = owner.declaredMethods.single {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && !it.returnType.isPrimitive &&
                !it.returnType.name.startsWith("java.") && !it.isBridge
        }
        XposedBridge.hookMethod(template, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val language = phoneLanguage() ?: return
                val english = param.result ?: return
                param.result = translatedTemplate(english, languageName(language)) ?: return
            }
        })
    }

    private fun translatedTemplate(english: Any, languageName: String): Any? {
        prompts[english]?.let { return it }
        val text = JournalLanguage.reflectionPrompt(english.toString(), languageName) ?: run {
            logOnce("The reflection template names no output language; reflections stay in English")
            return null
        }
        val template = english.javaClass.getDeclaredConstructor(String::class.java)
            .apply { isAccessible = true }
            .newInstance(text)
        prompts[english] = template
        return template
    }

    /** Gives every per-language table of [owner] an entry for [language], copied from English. */
    private fun addLanguage(owner: Any, language: String) {
        val name = languageName(language)
        owner.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.forEach { field ->
            if (!Map::class.java.isAssignableFrom(field.type) && field.type != Any::class.java) return@forEach
            field.isAccessible = true
            val map = field.get(owner) as? Map<*, *> ?: return@forEach
            val changed = try {
                JournalLanguage.withLanguage(map, language, name, ::keyFor)
            } catch (error: Throwable) {
                logOnce("Unable to add $language to ${owner.javaClass.name}: $error")
                null
            } ?: return@forEach
            field.set(owner, changed)
            logOnce("Added $language to the tables of ${owner.javaClass.name}")
        }
    }

    /** A key of [template]'s type for [language], built through its (String) constructor. */
    private fun keyFor(template: Any, language: String): Any {
        return template.javaClass.getDeclaredConstructor(String::class.java)
            .apply { isAccessible = true }
            .newInstance(language)
    }

    private fun logOnce(message: String) {
        if (logged.add(message)) log(message)
    }

}
