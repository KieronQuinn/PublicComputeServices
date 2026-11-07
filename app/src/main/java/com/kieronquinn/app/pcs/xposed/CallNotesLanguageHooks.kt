package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AICORE_CALL_NOTES_LANGUAGE
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.ByteArrayOutputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Makes Call Notes summaries come out in the language of the call.
 *
 * Google Phone's Call Notes summary is AICore feature 208 ("Dialer Smart Notes"). Its definition
 * pairs the base model with an English-only LoRA adapter and a one line prompt ("Summarize the
 * following conversation: %1$s"). With a call in any other language the adapter misreads the
 * transcript and writes the summary in English. The same base model without the adapter follows
 * an instruction to keep the conversation's language.
 *
 * AICore reads feature definitions (`FeatureExperiment` protos) from the model file group's
 * metadata, parsing them with protobuf-lite's generic `parseFrom(defaultInstance, ByteString,
 * ExtensionRegistryLite)`. This hooks that call from the method that logs
 * [PARSE_FAILURE_LOG] and, for feature [FEATURE_ID] only, removes the adapter file name and
 * replaces the prompt template. Every other field, including allowed packages and signatures,
 * versions and safety settings, is kept as is. Phone formats the input and reads the output the
 * same way as before: a call direction line then "You:"/"Other:" lines in, a sentence followed by
 * "* " bullet points out.
 */
object CallNotesLanguageHooks: XposedHooks {

    override val tag = "CallNotesLanguageHooks"

    private const val FEATURE_ID = 208L
    private const val PARSE_FAILURE_LOG =
        "Failed to parse FeatureExperiment proto from Any value for file group %s"

    /**
     *  Based on the Recorder app's on-device summary prompt for the same base model. The output
     *  format is the one Phone expects: a purpose sentence, then bullet points starting with "* ".
     */
    private const val TEMPLATE = "<ctrl99>system\n" +
            "You are an expert in phone call summarization. The transcript starts with a line" +
            " saying whether the call was incoming or outgoing. Each following line starts with" +
            " \"You:\" (the owner of this phone) or \"Other:\" (the other person on the call)." +
            " When summarizing, adhere to these guidelines:\n\n" +
            "- Language: Write the whole summary in the same language as the conversation, in" +
            " natural, fluent and grammatical sentences.\n" +
            "- Perspective: Address the owner of this phone directly in the second person. Refer" +
            " to the other person as \"the other person\"; never use the first person.\n" +
            "- Prioritize useful details: purpose of the call, decisions, plans, requests, dates," +
            " times, places, amounts and names.\n" +
            "- Maintain accuracy: Do not add information that is not explicitly stated in the" +
            " conversation. Speech recognition may contain errors; do not repeat words that make" +
            " no sense in context.\n" +
            "- Respect privacy: Do not guess the speakers' genders or other personal attributes.\n" +
            "- No repetition: Every bullet point must state a different point.\n" +
            "- Plain text format: No headers, bold text, markdown or other additional" +
            " information.<ctrl100>\n" +
            "<ctrl99>user\n%1\$s\n\n" +
            "First write one short sentence, addressed to the owner of this phone, that states" +
            " why the call was made (for example \"You called to ask …\" or \"You were called" +
            " about …\", in the language of the conversation). Then summarize the call in at" +
            " most 5 bullet points, using no more than 2 sentences per bullet; use fewer bullet" +
            " points for a short call. Begin each bullet point with \"* \" and do not use the" +
            " \"*\" character anywhere else.<ctrl100>\n" +
            "<ctrl99>model\n"

    @Volatile
    private var loggedRewrite = false

    override fun hook(loadPackageParam: LoadPackageParam) {
        if (!SystemProperties_getBoolean(AICORE_CALL_NOTES_LANGUAGE, false)) return
        val classLoader = loadPackageParam.classLoader
        val dexKit = loadDexKit(loadPackageParam.appInfo.sourceDir)
        val caller = dexKit.findMethod {
            matcher { usingStrings(PARSE_FAILURE_LOG) }
        }.singleOrNull() ?: run {
            log("Unable to find FeatureExperiment parsing")
            return
        }
        // parseFrom(T defaultInstance, ByteString, ExtensionRegistryLite): T. The same method also
        // parses the wrapping Any from a byte array, which this skips.
        val parseFrom = caller.invokes.filter {
            it.isMethod && it.paramTypeNames.size == 3 &&
                    it.returnTypeName == it.paramTypeNames[0] &&
                    it.paramTypeNames[1] != "byte[]"
        }.map { it.getMethodInstance(classLoader) as Method }
            .filter { Modifier.isStatic(it.modifiers) }
            .distinct()
            .singleOrNull() ?: run {
                log("Unable to find protobuf parseFrom(ByteString) in ${caller.className}")
                return
            }
        // Default instances read by the caller: FeatureExperiment (and possibly Any). parseFrom is
        // shared by every message type, so only these are considered.
        val messageTypes = caller.usingFields.map { it.field }.filter {
            it.typeName == it.className
        }.mapNotNull {
            runCatching { it.getClassInstance(classLoader) }.getOrNull()
        }.toSet()
        if (messageTypes.isEmpty()) {
            log("Unable to find FeatureExperiment type in ${caller.className}")
            return
        }
        val byteStringClass = parseFrom.parameterTypes[1]
        val toByteArray = byteStringClass.findToByteArray() ?: run {
            log("Unable to find ${byteStringClass.name}.toByteArray")
            return
        }
        val copyFrom = byteStringClass.findCopyFrom() ?: run {
            log("Unable to find ${byteStringClass.name}.copyFrom")
            return
        }
        XposedBridge.hookMethod(parseFrom, object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val type = param.args[0]?.javaClass ?: return
                val data = param.args[1] ?: return
                if (type !in messageTypes) return
                try {
                    val original = toByteArray.invoke(data) as ByteArray
                    val rewritten = rewriteExperiment(original) ?: return
                    param.args[1] = copyFrom.invoke(null, rewritten)
                    if (!loggedRewrite) {
                        loggedRewrite = true
                        log("Feature $FEATURE_ID: removed English adapter and replaced prompt")
                    }
                } catch (e: Throwable) {
                    log("Unable to rewrite feature $FEATURE_ID, using original: $e")
                }
            }
        })
        log("Hooked ${parseFrom.declaringClass.name}.${parseFrom.name} for feature $FEATURE_ID")
    }

    /**
     *  FeatureExperiment: 1 = control Feature, 2 = treatment Feature (repeated). Feature: 1 = id,
     *  3 = model (10 = files: 3 = adapter file, 4 = base model), 8 = inference config
     *  (1 = prompt template). Returns null when feature [FEATURE_ID] is not present or does not
     *  have the expected shape, leaving the original bytes untouched.
     */
    internal fun rewriteExperiment(experiment: ByteArray): ByteArray? {
        val fields = ProtoField.parse(experiment) ?: return null
        var changed = false
        fields.filter { (it.number == 1 || it.number == 2) && it.wire == 2 }.forEach { field ->
            rewriteFeature(field.bytes)?.let {
                field.bytes = it
                changed = true
            }
        }
        return if (changed) ProtoField.write(fields) else null
    }

    private fun rewriteFeature(feature: ByteArray): ByteArray? {
        val fields = ProtoField.parse(feature) ?: return null
        if (fields.firstOrNull { it.number == 1 && it.wire == 0 }?.varint != FEATURE_ID) return null
        var changed = false
        for (field in fields) {
            if (field.number == 3 && field.wire == 2) {
                withoutAdapter(field.bytes)?.let {
                    field.bytes = it
                    changed = true
                }
            } else if (field.number == 8 && field.wire == 2) {
                val config = ProtoField.parse(field.bytes) ?: return null
                val template = config.firstOrNull { it.number == 1 && it.wire == 2 } ?: return null
                val wanted = TEMPLATE.toByteArray()
                if (!template.bytes.contentEquals(wanted)) {
                    template.bytes = wanted
                    field.bytes = ProtoField.write(config)
                    changed = true
                }
            }
        }
        return if (changed) ProtoField.write(fields) else null
    }

    private fun withoutAdapter(model: ByteArray): ByteArray? {
        val fields = ProtoField.parse(model) ?: return null
        var changed = false
        for (field in fields) {
            if (field.number != 10 || field.wire != 2) continue
            val files = ProtoField.parse(field.bytes) ?: return null
            // Without a base model name this is not the layout this was written for
            if (files.none { it.number == 4 && it.wire == 2 }) return null
            if (files.removeAll { it.number == 3 }) {
                field.bytes = ProtoField.write(files)
                changed = true
            }
        }
        return if (changed) ProtoField.write(fields) else null
    }

    /** ByteString.toByteArray(): the only no-argument instance method returning a byte array */
    private fun Class<*>.findToByteArray(): Method? {
        val methods = generateSequence(this) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredMethods.asSequence() }
            .filter {
                !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) &&
                        it.parameterCount == 0 && it.returnType == ByteArray::class.java
            }.toList()
        return methods.takeIf { it.map { method -> method.name }.distinct().size == 1 }
            ?.first()?.apply { isAccessible = true }
    }

    /** ByteString.copyFrom(byte[]): the only public static byte array to ByteString factory */
    private fun Class<*>.findCopyFrom(): Method? {
        return declaredMethods.filter {
            Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) &&
                    it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java)) &&
                    isAssignableFrom(it.returnType)
        }.singleOrNull()?.apply { isAccessible = true }
    }

    /**
     *  A single field of a protobuf message in wire format. Unknown fields are written back
     *  unchanged, so only the fields edited above differ from the original message.
     */
    internal class ProtoField(val number: Int, val wire: Int) {

        var varint = 0L
        var bytes = ByteArray(0)

        companion object {
            /** Returns null if the bytes are not a valid message (groups, truncation, overflow) */
            fun parse(data: ByteArray): MutableList<ProtoField>? {
                val fields = ArrayList<ProtoField>()
                val reader = Reader(data)
                while (reader.position < data.size) {
                    val tag = reader.varint() ?: return null
                    val field = ProtoField((tag ushr 3).toInt(), (tag and 7).toInt())
                    if (field.number <= 0) return null
                    when (field.wire) {
                        0 -> field.varint = reader.varint() ?: return null
                        1 -> field.bytes = reader.bytes(8) ?: return null
                        5 -> field.bytes = reader.bytes(4) ?: return null
                        2 -> {
                            val length = reader.varint() ?: return null
                            if (length < 0 || length > Int.MAX_VALUE) return null
                            field.bytes = reader.bytes(length.toInt()) ?: return null
                        }
                        else -> return null
                    }
                    fields.add(field)
                }
                return fields
            }

            fun write(fields: List<ProtoField>): ByteArray {
                val out = ByteArrayOutputStream()
                for (field in fields) {
                    out.writeVarint((field.number.toLong() shl 3) or field.wire.toLong())
                    when (field.wire) {
                        0 -> out.writeVarint(field.varint)
                        2 -> {
                            out.writeVarint(field.bytes.size.toLong())
                            out.write(field.bytes)
                        }
                        else -> out.write(field.bytes)
                    }
                }
                return out.toByteArray()
            }

            private fun ByteArrayOutputStream.writeVarint(value: Long) {
                var remaining = value
                while (remaining and 0x7fL.inv() != 0L) {
                    write(((remaining and 0x7f) or 0x80).toInt())
                    remaining = remaining ushr 7
                }
                write(remaining.toInt())
            }
        }

        private class Reader(private val data: ByteArray) {
            var position = 0

            fun varint(): Long? {
                var result = 0L
                var shift = 0
                while (shift < 64) {
                    if (position >= data.size) return null
                    val byte = data[position++].toInt() and 0xff
                    result = result or ((byte and 0x7f).toLong() shl shift)
                    if (byte and 0x80 == 0) return result
                    shift += 7
                }
                return null
            }

            fun bytes(count: Int): ByteArray? {
                if (count < 0 || position + count > data.size) return null
                return data.copyOfRange(position, position + count).also { position += count }
            }
        }
    }

}
