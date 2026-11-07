package com.kieronquinn.app.pcs.xposed

import android.content.Context
import android.os.SystemClock
import com.kieronquinn.app.pcs.callscreen.pack.CallScreenPackLayout
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.VARINT
import com.kieronquinn.app.pcs.utils.extensions.reflectParseProto
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

/**
 *  Runs agentic Call Screen in the phone's language with the voice pack PCS builds on the device
 *  (see `CallScreenPackRepository`). Everything here only takes effect when a pack built from the
 *  exact English pack Phone has selected is present; otherwise Phone runs unchanged in English.
 *
 *  - The model picker's file group (name "dobby-duplex-files") tells which zip is in use, by its
 *    version and the file's SHA-1. Its English locale tags are replaced with the pack's, or Phone
 *    refuses the group for a non-English call ("failed to match locale").
 *  - `DobbySingletonLocaleProvider` keeps the Call Screen locale in a lazy property; it is
 *    replaced so the conversation, transcript and speech recognition use the pack's language.
 *  - The AudioPrism duplex model runner hands the unzipped pack directory to Call Screen as
 *    `WithPath(unzippedFilePath=…)` (`files/dobby-duplex-files/<version>`); that path is pointed
 *    at the localised copy.
 *  - Speech recognition would fall back to English for the pack's language; while a pack is
 *    active, [SodaLocaleHooks] lets it use the pack's language.
 *  - Speech recognition for some languages reports a new "speech start" within a few hundred
 *    milliseconds of every "speech end". Call Screen's silence detector restarts its timer on
 *    each start and never sees the 2 s of silence it waits for, so the bot never answers. A start
 *    that close to an end is not passed to the silence detector; real speech still resets the
 *    timer through recognition results.
 */
internal object CallScreenLanguageHooks {

    private const val TAG = "PhoneHooks"
    private const val SPURIOUS_START_WINDOW_MS = 500L
    private val ENGLISH_TAGS = setOf("en-US", "en-GB", "en-AU", "en-CA", "en-IE", "en-IN")

    @Volatile
    private var activePack: CallScreenPackLayout.Pack? = null
    private val resolving = ThreadLocal<Boolean>()

    fun install(dexKit: DexKitBridge, classLoader: ClassLoader, context: Context) {
        val filesDir = context.filesDir
        val phoneLocale = context.resources.configuration.locales.get(0)
        val picker = try {
            DobbyModelPicker.find(dexKit, classLoader)
        } catch (error: Exception) {
            log("Call Screen language unavailable, model picker not found: $error")
            return
        }
        val pickerInstance = Collections.synchronizedMap(WeakHashMap<Any, Unit>())
        hookPicker(picker, filesDir, phoneLocale, pickerInstance)
        hookLocaleProvider(picker.localeProvider, pickerInstance)
        hookUnzippedPath(dexKit, classLoader, filesDir)
        hookSilenceDetector(dexKit, classLoader)
        SodaLocaleHooks.register(dexKit, classLoader, context, phoneLocale,
            SodaLocaleHooks.Use("Call Screen") { activePack?.locale?.toLanguageTag() })
        log("Call Screen language hooks installed for ${phoneLocale.toLanguageTag()}")
    }

    /**
     *  The picker's zero-argument methods return the file group, or a node holding it. The group
     *  is rewritten when a pack is ready for its zip.
     */
    private fun hookPicker(
        picker: DobbyModelPicker,
        filesDir: File,
        phoneLocale: Locale,
        instances: MutableMap<Any, Unit>
    ) {
        val type = picker.constructor.declaringClass
        XposedBridge.hookMethod(picker.constructor, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                instances[param.thisObject] = Unit
            }
        })
        val logged = HashSet<String>()
        type.declaredMethods.filter {
            it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) &&
                !it.returnType.isPrimitive && it.returnType != String::class.java
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    param.result = rewriteGroupOrNode(result, filesDir, phoneLocale) { source, pack ->
                        synchronized(logged) {
                            if (logged.add(source)) {
                                log(if (pack != null) {
                                    "Call Screen group $source uses the ${pack.locale.toLanguageTag()} pack"
                                } else {
                                    "No ${phoneLocale.toLanguageTag()} Call Screen pack for group $source; keeping English"
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
        filesDir: File,
        phoneLocale: Locale,
        onResolved: (String, CallScreenPackLayout.Pack?) -> Unit
    ): Any? {
        serialize(value)?.let { bytes ->
            return rewriteGroup(value, bytes, filesDir, phoneLocale, onResolved)
        }
        // A node: a final class whose fields are the group and its use cases. Rebuild it with
        // the rewritten group through its constructor, keeping every other field.
        val fields = value.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
        val groupField = fields.firstOrNull { serialize(it.get(value) ?: return@firstOrNull false) != null }
            ?: return null
        val group = groupField.get(value)!!
        val rewritten = rewriteGroup(group, serialize(group)!!, filesDir, phoneLocale, onResolved)
            ?: return null
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
        filesDir: File,
        phoneLocale: Locale,
        onResolved: (String, CallScreenPackLayout.Pack?) -> Unit
    ): Any? {
        val group = CallScreenPackLayout.parseGroup(bytes) ?: return null
        val pack = CallScreenPackLayout.findReadyPack(filesDir, phoneLocale, group)
        activePack = pack
        onResolved(group.sourceId, pack)
        pack ?: return null
        val tag = pack.locale.toLanguageTag().toByteArray()
        return replaceEnglishTags(bytes, tag).reflectParseProto(group.javaClass)
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
     *  The provider's locale is a lazy property (the provider's only field of a non-provider
     *  interface type). Replace it with a delegate that answers the pack's locale while a pack is
     *  active, resolving the picker first if it has not run yet.
     */
    private fun hookLocaleProvider(provider: Class<*>, pickers: Map<Any, Unit>) {
        XposedBridge.hookAllConstructors(provider, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val instance = param.thisObject
                val lazyField = provider.declaredFields.singleOrNull {
                    !Modifier.isStatic(it.modifiers) && it.type.isInterface &&
                        it.type.methods.any { m -> m.parameterCount == 0 && m.returnType == Any::class.java } &&
                        it.type.methods.any { m -> m.parameterCount == 0 && m.returnType == Boolean::class.javaPrimitiveType } &&
                        it.type.methods.size == 2
                } ?: run {
                    log("Call Screen locale property not found in ${provider.name}")
                    return
                }
                lazyField.isAccessible = true
                val original = lazyField.get(instance) ?: return
                val type = lazyField.type
                lazyField.set(instance, Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                    when {
                        method.name == "equals" && method.parameterCount == 1 -> false
                        method.name == "hashCode" && method.parameterCount == 0 -> System.identityHashCode(original)
                        method.name == "toString" && method.parameterCount == 0 -> original.toString()
                        method.returnType == Any::class.java -> {
                            resolvePack(pickers)?.locale?.toLanguageTag()
                                ?: method.invoke(original, *args.orEmpty())
                        }
                        else -> method.invoke(original, *args.orEmpty())
                    }
                })
            }
        })
    }

    private fun resolvePack(pickers: Map<Any, Unit>): CallScreenPackLayout.Pack? {
        activePack?.let { return it }
        if (resolving.get() == true) return null
        resolving.set(true)
        try {
            val picker = synchronized(pickers) { pickers.keys.firstOrNull() } ?: return null
            // Calling any group method runs the picker hook, which sets the active pack.
            picker.javaClass.declaredMethods.firstOrNull {
                it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) && !it.returnType.isPrimitive &&
                    it.returnType != String::class.java
            }?.apply { isAccessible = true }?.invoke(picker)
            return activePack
        } catch (error: Exception) {
            log("Unable to resolve the Call Screen pack: $error")
            return null
        } finally {
            resolving.set(false)
        }
    }

    private fun hookUnzippedPath(dexKit: DexKitBridge, classLoader: ClassLoader, filesDir: File) {
        val type = try {
            dexKit.findClass {
                matcher { usingStrings("WithPath(unzippedFilePath=") }
            }.single().getInstance(classLoader)
        } catch (error: Exception) {
            log("Call Screen pack path not found: $error")
            return
        }
        val constructor = type.declaredConstructors.singleOrNull {
            it.parameterTypes.contentEquals(arrayOf(String::class.java))
        } ?: run {
            log("Unsupported Call Screen pack path type ${type.name}")
            return
        }
        val parent = File(filesDir, CallScreenPackLayout.SOURCE_DIRECTORY).absoluteFile
        XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val path = File(param.args[0] as? String ?: return).absoluteFile
                if (path.parentFile != parent) return
                val pack = activePack?.takeIf { it.version.toString() == path.name } ?: return
                param.args[0] = pack.directory.absolutePath
                log("Call Screen uses ${pack.directory.absolutePath}")
            }
        })
    }

    private fun hookSilenceDetector(dexKit: DexKitBridge, classLoader: ClassLoader) {
        try {
            val events = dexKit.findClass {
                matcher { usingStrings("EVENT_ENDPOINT_SPEECH_START", "EVENT_ENDPOINT_SPEECH_END") }
            }.single().getInstance(classLoader)
            val start = eventNumber(events, "EVENT_ENDPOINT_SPEECH_START")
            val end = eventNumber(events, "EVENT_ENDPOINT_SPEECH_END")
            val detector = dexKit.findClass {
                matcher {
                    usingStrings("com/google/quality/views/extraction/kcube/bg/ondevice/modules/callassistant/agenticcallscreen/AgenticCallScreenSilenceDetectorProcessor")
                }
            }.map { it.getInstance(classLoader) }.single { type ->
                type.declaredMethods.count(::isConsume) == 1
            }
            val consume = detector.declaredMethods.single(::isConsume)
            val lastEnd = Collections.synchronizedMap(WeakHashMap<Any, Long>())
            var logged = false
            XposedBridge.hookMethod(consume, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (activePack == null) return
                    val code = eventCode(param.args[0]) ?: return
                    val now = SystemClock.elapsedRealtime()
                    when (code) {
                        end -> lastEnd[param.thisObject] = now
                        start -> {
                            val last = lastEnd.remove(param.thisObject) ?: return
                            if (now - last < SPURIOUS_START_WINDOW_MS) {
                                param.result = null
                                if (!logged) {
                                    logged = true
                                    log("Ignoring speech start ${now - last} ms after speech end for silence detection")
                                }
                            }
                        }
                    }
                }
            })
        } catch (error: Exception) {
            log("Call Screen silence detection fix unavailable: $error")
        }
    }

    /**
     *  The detector's event consumer: the processor interface's one-parameter method taking a
     *  protobuf message, with no result.
     */
    private fun isConsume(method: Method): Boolean = method.parameterCount == 1 &&
        method.returnType == Void.TYPE && Modifier.isPublic(method.modifiers) &&
        !Modifier.isStatic(method.modifiers) &&
        method.parameterTypes[0].methods.any { it.parameterCount == 0 && it.returnType == ByteArray::class.java } &&
        method.declaringClass.interfaces.any { contract ->
            contract.methods.any { it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes) }
        }

    private fun eventNumber(type: Class<*>, name: String): Long {
        val constant = type.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
            ?: error("$name not found")
        val number = type.methods.first {
            it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType &&
                !Modifier.isStatic(it.modifiers) && it.name != "ordinal" && it.name != "hashCode"
        }
        return (number.invoke(constant) as Int).toLong()
    }

    /** Event field 5 is the event data, whose field 1 is the event type number. */
    private fun eventCode(event: Any): Long? {
        val bytes = serialize(event) ?: return null
        val data = ProtoWire.parseOrNull(bytes)?.firstOrNull {
            it.number == 5 && it.wireType == LENGTH_DELIMITED
        } ?: return null
        return ProtoWire.parseOrNull(data.bytes)?.firstOrNull {
            it.number == 1 && it.wireType == VARINT
        }?.value as? Long
    }

    private val serializers = Collections.synchronizedMap(HashMap<Class<*>, Method?>())

    private fun serialize(value: Any): ByteArray? {
        val method = serializers.getOrPut(value.javaClass) {
            value.javaClass.methods.singleOrNull {
                it.parameterCount == 0 && it.returnType == ByteArray::class.java
            }?.apply { isAccessible = true }
        } ?: return null
        return method.invoke(value) as? ByteArray
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
