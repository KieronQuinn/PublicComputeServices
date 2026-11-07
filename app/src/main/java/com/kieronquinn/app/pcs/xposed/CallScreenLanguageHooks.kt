package com.kieronquinn.app.pcs.xposed

import android.content.Context
import android.os.SystemClock
import com.kieronquinn.app.pcs.voicepack.ProtoWire
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.voicepack.ProtoWire.VARINT
import com.kieronquinn.app.pcs.voicepack.VoicePack
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 *  Runs agentic Call Screen in the phone's language with its voice pack (see [VoicePackHooks]).
 *  Call Screen's file group comes from the model picker ("dobby-duplex-files"), its locale from
 *  `DobbySingletonLocaleProvider`, the picker's first constructor parameter.
 *
 *  Speech recognition for some languages reports a new "speech start" within a few hundred
 *  milliseconds of every "speech end". Call Screen's silence detector restarts its timer on each
 *  start and never sees the 2 s of silence it waits for, so the bot never answers. While a pack is
 *  active, a start that close to an end is not passed to the silence detector; real speech still
 *  resets the timer through recognition results.
 */
internal object CallScreenLanguageHooks {

    private const val TAG = "PhoneHooks"
    private const val SPURIOUS_START_WINDOW_MS = 500L

    private val voicePack = VoicePackHooks(VoicePack.CALL_SCREEN)

    fun install(dexKit: DexKitBridge, classLoader: ClassLoader, context: Context) {
        try {
            val picker = DobbyModelPicker.find(dexKit, classLoader)
            voicePack.install(dexKit, classLoader, context,
                picker.constructor.declaringClass, picker.localeProvider)
        } catch (error: Exception) {
            log("Call Screen language unavailable: $error")
            return
        }
        hookSilenceDetector(dexKit, classLoader)
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
                    if (voicePack.activePack == null) return
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
        val bytes = VoicePackHooks.serialize(event) ?: return null
        val data = ProtoWire.parseOrNull(bytes)?.firstOrNull {
            it.number == 5 && it.wireType == LENGTH_DELIMITED
        } ?: return null
        return ProtoWire.parseOrNull(data.bytes)?.firstOrNull {
            it.number == 1 && it.wireType == VARINT
        }?.value as? Long
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
