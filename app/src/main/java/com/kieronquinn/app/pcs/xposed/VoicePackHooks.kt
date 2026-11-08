package com.kieronquinn.app.pcs.xposed

import android.content.Context
import com.kieronquinn.app.pcs.utils.extensions.reflectParseProto
import com.kieronquinn.app.pcs.voicepack.ProtoWire
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.voicepack.VoicePack
import com.kieronquinn.app.pcs.voicepack.VoicePackLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

/**
 *  Runs a Phone duplex feature (see [VoicePack]) in the phone's language with the voice pack PCS
 *  builds on the device (see `VoicePackRepository`). Everything here only takes effect when a
 *  pack built from the exact English zip Phone has selected is present; otherwise Phone runs
 *  unchanged in English.
 *
 *  - The feature's file group source returns the group (or a node holding it). The group tells
 *    which zip is in use, by its version and the file's SHA-1. Its English locale tags are
 *    replaced with the pack's, or Phone refuses the group for a non-English call.
 *  - The feature's locale provider keeps its locale in a lazy property; it is replaced so the
 *    conversation, transcript and speech recognition use the pack's language.
 *  - The AudioPrism duplex model runner hands the unzipped zip directory to the feature as
 *    `WithPath(unzippedFilePath=…)` (`files/<group name>/<version>`); that path is pointed at the
 *    localised copy.
 *  - Speech recognition would fall back to English for the pack's language; while a pack is
 *    active, [SodaLocaleHooks] lets it use the pack's language.
 */
internal class VoicePackHooks(private val pack: VoicePack) {

    @Volatile
    var activePack: VoicePackLayout.Pack? = null
        private set
    private val resolving = ThreadLocal<Boolean>()
    private val sources = Collections.synchronizedMap(WeakHashMap<Any, Unit>())
    private val sourceMethods = ArrayList<Method>()
    private lateinit var filesDir: File
    private lateinit var mddGroups: File
    private lateinit var phoneLocale: Locale
    private var downloaded: Pair<Long, List<VoicePackLayout.Group>>? = null

    /**
     *  @param groupSource the class whose zero-argument methods return the feature's file group
     *  @param localeProvider the feature's locale provider, holding the locale in a lazy property
     */
    fun install(
        dexKit: DexKitBridge,
        classLoader: ClassLoader,
        context: Context,
        groupSource: Class<*>,
        localeProvider: Class<*>
    ) {
        filesDir = context.filesDir
        mddGroups = File(context.dataDir, "shared_prefs/${VoicePackLayout.MDD_GROUPS}")
        phoneLocale = context.resources.configuration.locales.get(0)
        // Find everything before hooking, so a missing part leaves Phone untouched: a rewritten
        // group with an English locale would stop the feature.
        val lazyField = findLazyLocale(localeProvider)
        val methods = findGroupMethods(groupSource)
        hookGroupSource(groupSource, methods)
        hookLocaleProvider(localeProvider, lazyField)
        hookUnzippedPath(dexKit, classLoader)
        SodaLocaleHooks.register(dexKit, classLoader, context, phoneLocale,
            SodaLocaleHooks.Use(pack.title) { activePack?.locale?.toLanguageTag() })
        log("${pack.title} language hooks installed for ${phoneLocale.toLanguageTag()}")
    }

    /**
     *  The pack's locale, resolving the group source first if Phone has not asked for the group
     *  yet, or null to keep Phone's own locale. Feature hooks use this for locale accessors that
     *  are not the lazy property.
     */
    fun resolvePack(): VoicePackLayout.Pack? {
        activePack?.let { return it }
        if (resolving.get() == true) return null
        resolving.set(true)
        try {
            val source = synchronized(sources) { sources.keys.firstOrNull() } ?: return null
            // Calling any group method runs the source hook, which sets the active pack.
            sourceMethods.firstOrNull { !Modifier.isStatic(it.modifiers) }
                ?.apply { isAccessible = true }?.invoke(source)
            return activePack
        } catch (error: Exception) {
            log("Unable to resolve the ${pack.title} pack: $error")
            return null
        } finally {
            resolving.set(false)
        }
    }

    private fun findGroupMethods(type: Class<*>): List<Method> = type.declaredMethods.filter {
        it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) &&
            !it.returnType.isPrimitive && it.returnType != String::class.java
    }.also { check(it.isNotEmpty()) { "no group methods in ${type.name}" } }

    private fun hookGroupSource(type: Class<*>, methods: List<Method>) {
        XposedBridge.hookAllConstructors(type, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                sources[param.thisObject] = Unit
            }
        })
        val logged = HashSet<String>()
        sourceMethods += methods
        sourceMethods.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    param.result = rewriteGroupOrNode(result) { source, ready ->
                        synchronized(logged) {
                            if (logged.add(source)) {
                                log(if (ready != null) {
                                    "${pack.title} group $source uses the ${ready.locale.toLanguageTag()} pack"
                                } else {
                                    "No ${phoneLocale.toLanguageTag()} ${pack.title} pack for group $source; keeping English"
                                })
                            }
                        }
                    } ?: result
                }
            })
        }
    }

    /** Returns the rewritten group or node, or null to keep the original. */
    private fun rewriteGroupOrNode(
        value: Any,
        onResolved: (String, VoicePackLayout.Pack?) -> Unit
    ): Any? {
        serialize(value)?.let { bytes ->
            return rewriteGroup(value, bytes, onResolved)
        }
        // A node: a final class whose fields are the group and its use cases. Rebuild it with
        // the rewritten group through its constructor, keeping every other field.
        val fields = value.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
        val groupField = fields.firstOrNull { serialize(it.get(value) ?: return@firstOrNull false) != null }
            ?: return null
        val group = groupField.get(value)!!
        val rewritten = rewriteGroup(group, serialize(group)!!, onResolved) ?: return null
        val values = fields.map { if (it == groupField) rewritten else it.get(value) }
        val constructor = value.javaClass.declaredConstructors.firstOrNull { constructor ->
            constructor.parameterCount == fields.size &&
                constructor.parameterTypes.zip(fields).all { (type, field) -> type == field.type }
        } ?: return null
        constructor.isAccessible = true
        return constructor.newInstance(*values.toTypedArray())
    }

    private fun rewriteGroup(
        group: Any,
        bytes: ByteArray,
        onResolved: (String, VoicePackLayout.Pack?) -> Unit
    ): Any? {
        // Groups without files (a region Phone has no model for) are not this feature's zip.
        val requested = VoicePackLayout.parseGroup(pack, bytes) ?: return null
        // Phone runs a newer download in place of the requested zip, so the pack follows that.
        // Without readable metadata (before the first unlock), only the requested zip matches.
        val downloaded = downloadedGroups()
        val running = if (downloaded.isEmpty()) requested
            else VoicePackLayout.runningGroup(requested, downloaded)
        val ready = running?.let { VoicePackLayout.findReadyPack(filesDir, pack, phoneLocale, it) }
        activePack = ready
        onResolved(running?.sourceId ?: requested.sourceId, ready)
        ready ?: return null
        val tag = ready.locale.toLanguageTag().toByteArray()
        // Rebuild Phone's own proto type, not the parsed summary.
        return replaceEnglishTags(bytes, tag).reflectParseProto(group.javaClass)
    }

    /** The feature's downloaded groups, re-read when Phone's MDD metadata changes. */
    private fun downloadedGroups(): List<VoicePackLayout.Group> = synchronized(this) {
        val modified = mddGroups.lastModified()
        downloaded?.takeIf { it.first == modified }?.let { return it.second }
        val groups = try {
            VoicePackLayout.readMddGroups(pack, mddGroups.readText())
        } catch (error: Exception) {
            log("Unable to read Phone's downloaded ${pack.title} files: $error")
            emptyList()
        }
        downloaded = modified to groups
        groups
    }

    private fun replaceEnglishTags(bytes: ByteArray, tag: ByteArray): ByteArray {
        val fields = ProtoWire.parseOrNull(bytes) ?: return bytes
        return ProtoWire.encode(fields.map { field ->
            if (field.wireType != LENGTH_DELIMITED) return@map field
            val text = ProtoWire.text(field)
            when {
                text in ENGLISH_TAGS -> field.withBytes(tag)
                text == null -> field.withBytes(replaceEnglishTags(field.bytes, tag))
                else -> field
            }
        })
    }

    /**
     *  The provider's locale is a lazy property (the provider's only field of a two-method
     *  interface: the value and whether it is initialised).
     */
    private fun findLazyLocale(provider: Class<*>): Field = provider.declaredFields.singleOrNull {
        !Modifier.isStatic(it.modifiers) && it.type.isInterface &&
            it.type.methods.size == 2 &&
            it.type.methods.any { m -> m.parameterCount == 0 && m.returnType == Any::class.java } &&
            it.type.methods.any { m -> m.parameterCount == 0 && m.returnType == Boolean::class.javaPrimitiveType }
    }?.apply { isAccessible = true } ?: error("${pack.title} locale property not found in ${provider.name}")

    /** Replaces the lazy locale with a delegate that answers the pack's locale while one is active. */
    private fun hookLocaleProvider(provider: Class<*>, lazyField: Field) {
        val type = lazyField.type
        XposedBridge.hookAllConstructors(provider, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val original = lazyField.get(param.thisObject) ?: return
                lazyField.set(param.thisObject, Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                    when {
                        method.name == "equals" && method.parameterCount == 1 -> false
                        method.name == "hashCode" && method.parameterCount == 0 -> System.identityHashCode(original)
                        method.name == "toString" && method.parameterCount == 0 -> original.toString()
                        method.returnType == Any::class.java -> {
                            resolvePack()?.locale?.toLanguageTag()
                                ?: method.invoke(original, *args.orEmpty())
                        }
                        else -> method.invoke(original, *args.orEmpty())
                    }
                })
            }
        })
    }

    private fun hookUnzippedPath(dexKit: DexKitBridge, classLoader: ClassLoader) {
        val type = try {
            dexKit.findClass {
                matcher { usingStrings("WithPath(unzippedFilePath=") }
            }.single().getInstance(classLoader)
        } catch (error: Exception) {
            log("${pack.title} pack path not found: $error")
            return
        }
        val constructor = type.declaredConstructors.singleOrNull {
            it.parameterTypes.contentEquals(arrayOf(String::class.java))
        } ?: run {
            log("Unsupported ${pack.title} pack path type ${type.name}")
            return
        }
        val parent = File(filesDir, pack.sourceDirectory).absoluteFile
        XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val path = File(param.args[0] as? String ?: return).absoluteFile
                if (path.parentFile != parent) return
                val ready = activePack?.takeIf { it.version.toString() == path.name } ?: return
                param.args[0] = ready.directory.absolutePath
                log("${pack.title} uses ${ready.directory.absolutePath}")
            }
        })
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

    companion object {
        private const val TAG = "PhoneHooks"
        private val ENGLISH_TAGS = setOf("en-US", "en-GB", "en-AU", "en-CA", "en-IE", "en-IN")
        private val serializers = Collections.synchronizedMap(HashMap<Class<*>, Method?>())

        /** The protobuf message's serialised bytes, or null when the value is not a message. */
        fun serialize(value: Any): ByteArray? {
            val method = serializers.getOrPut(value.javaClass) {
                value.javaClass.methods.singleOrNull {
                    it.parameterCount == 0 && it.returnType == ByteArray::class.java
                }?.apply { isAccessible = true }
            } ?: return null
            return method.invoke(value) as? ByteArray
        }
    }

}
