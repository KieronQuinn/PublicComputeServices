package com.kieronquinn.app.pcs.callscreen.pack

import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.Field
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.LENGTH_DELIMITED

/**
 *  Rewrites the text files of Phone's agentic Call Screen pack ("dobby-duplex-files") into another
 *  language. Only the fields below change; every other byte is preserved.
 *
 *  - `*_robot_intent_specs.pb`: top field 3 is an intent (1 = name). Intent field 11 is what the
 *    bot says: its field 2 is a prompt (1 = audio path, 2 = text) and field 15 the locale. Intent
 *    field 12 holds locales, fields 26 and 32 chip labels.
 *  - `*_bot_config.pb`: field 101 → 14 → 1 are voice command entries (1 = name, 2 → 1 = trigger
 *    phrases), 101 → 15 → 2 → 1 chip labels.
 *  - In every file, English locale tags are replaced wherever they appear as a whole string
 *    field. Lengths are re-encoded, so target tags of any length are safe.
 *
 *  Audio is not handled here: [CallScreenPackBuilder] synthesises one file per prompt path.
 */
internal class CallScreenPackRewriter(private val translation: CallScreenTranslation) {

    class Result(val bytes: ByteArray, val untranslated: List<String>)

    private val localeTag = translation.locale.toLanguageTag()
    private val localeBytes = localeTag.toByteArray()

    fun isRewritten(name: String): Boolean = name.endsWith(SPECS) || name.endsWith(BOT_CONFIG) ||
            name.endsWith(MODEL_CONFIG) || name.endsWith(ASSET_CONFIG)

    fun rewrite(name: String, bytes: ByteArray): Result {
        val untranslated = ArrayList<String>()
        val output = when {
            name.endsWith(SPECS) -> rewriteSpecs(bytes, untranslated)
            name.endsWith(BOT_CONFIG) -> rewriteBotConfig(bytes)
            name.endsWith(MODEL_CONFIG) || name.endsWith(ASSET_CONFIG) -> replaceLocales(bytes)
            else -> bytes
        }
        return Result(output, untranslated)
    }

    private fun rewriteSpecs(bytes: ByteArray, untranslated: MutableList<String>): ByteArray {
        return ProtoWire.encode(ProtoWire.parse(bytes).map { field ->
            if (field.number == 3 && field.wireType == LENGTH_DELIMITED) {
                field.withBytes(rewriteIntent(field.bytes, untranslated))
            } else field
        })
    }

    private fun rewriteIntent(bytes: ByteArray, untranslated: MutableList<String>): ByteArray {
        val fields = ProtoWire.parse(bytes)
        val name = fields.firstOrNull { it.number == 1 }?.let(ProtoWire::text).orEmpty()
        return ProtoWire.encode(fields.map { field ->
            if (field.wireType != LENGTH_DELIMITED) return@map field
            when (field.number) {
                11 -> field.withBytes(rewriteSay(field.bytes, name, untranslated))
                12 -> field.withBytes(replaceLocales(field.bytes))
                26, 32 -> translateChip(field)
                else -> field
            }
        })
    }

    private fun rewriteSay(bytes: ByteArray, intent: String, untranslated: MutableList<String>): ByteArray {
        return ProtoWire.encode(ProtoWire.parse(bytes).map { field ->
            when {
                field.number == 2 && field.wireType == LENGTH_DELIMITED -> {
                    val prompt = ProtoWire.parse(field.bytes)
                    val path = prompt.firstOrNull {
                        it.number == 1 && it.wireType == LENGTH_DELIMITED
                    }?.let(ProtoWire::text)
                    field.withBytes(ProtoWire.encode(prompt.map { promptField ->
                        if (promptField.number == 2 && promptField.wireType == LENGTH_DELIMITED) {
                            translatePrompt(promptField, path, intent, untranslated)
                        } else promptField
                    }))
                }
                field.number == 15 && field.wireType == LENGTH_DELIMITED -> field.withBytes(localeBytes)
                else -> field
            }
        })
    }

    private fun translatePrompt(
        field: Field,
        path: String?,
        intent: String,
        untranslated: MutableList<String>
    ): Field {
        val english = ProtoWire.text(field) ?: return field
        val translated = translation.translate(path, english)
        if (translated == null) {
            if (!isKeypad(english)) untranslated += "$intent: $english"
            return field
        }
        return field.withBytes(translated.toByteArray())
    }

    private fun translateChip(field: Field): Field {
        val label = ProtoWire.text(field) ?: return field
        return translation.chips[label]?.let { field.withBytes(it.toByteArray()) } ?: field
    }

    /** Replaces English locale tags anywhere inside a message, recursing into sub-messages. */
    private fun replaceLocales(bytes: ByteArray): ByteArray {
        val fields = ProtoWire.parseOrNull(bytes) ?: return bytes
        return ProtoWire.encode(fields.map { field ->
            if (field.wireType != LENGTH_DELIMITED) return@map field
            val text = ProtoWire.text(field)
            when {
                text in ENGLISH_LOCALES -> field.withBytes(localeBytes)
                text == null -> field.withBytes(replaceLocales(field.bytes))
                else -> field
            }
        })
    }

    private fun rewriteBotConfig(bytes: ByteArray): ByteArray {
        val fields = ProtoWire.parseOrNull(replaceLocales(bytes)) ?: return bytes
        return ProtoWire.encode(fields.map { field ->
            if (field.number == 101 && field.wireType == LENGTH_DELIMITED) {
                field.withBytes(rewriteBotSettings(field.bytes))
            } else field
        })
    }

    private fun rewriteBotSettings(bytes: ByteArray): ByteArray {
        return ProtoWire.encode(ProtoWire.parse(bytes).map { field ->
            if (field.wireType != LENGTH_DELIMITED) return@map field
            when (field.number) {
                14 -> field.withBytes(rewriteVoiceCommands(field.bytes))
                15 -> field.withBytes(rewriteChipGroups(field.bytes))
                else -> field
            }
        })
    }

    private fun rewriteVoiceCommands(bytes: ByteArray): ByteArray {
        return ProtoWire.encode(ProtoWire.parse(bytes).map { item ->
            if (item.number != 1 || item.wireType != LENGTH_DELIMITED) return@map item
            val entry = ProtoWire.parse(item.bytes)
            val name = entry.firstOrNull {
                it.number == 1 && it.wireType == LENGTH_DELIMITED
            }?.let(ProtoWire::text)
            val triggers = translation.voiceTriggers[name] ?: return@map item
            item.withBytes(ProtoWire.encode(entry.map { entryField ->
                if (entryField.number != 2 || entryField.wireType != LENGTH_DELIMITED) {
                    return@map entryField
                }
                val phrases = ProtoWire.parse(entryField.bytes).toMutableList()
                val existing = phrases.filter { it.number == 1 }.mapNotNull(ProtoWire::text).toSet()
                triggers.filterNot { it in existing }.forEach {
                    phrases += Field(1, LENGTH_DELIMITED, it.toByteArray())
                }
                entryField.withBytes(ProtoWire.encode(phrases))
            }))
        })
    }

    private fun rewriteChipGroups(bytes: ByteArray): ByteArray {
        return ProtoWire.encode(ProtoWire.parse(bytes).map { group ->
            if (group.number != 2 || group.wireType != LENGTH_DELIMITED) return@map group
            group.withBytes(ProtoWire.encode(ProtoWire.parse(group.bytes).map { chip ->
                if (chip.number == 1 && chip.wireType == LENGTH_DELIMITED) translateChip(chip) else chip
            }))
        })
    }

    private fun isKeypad(text: String) = text.length <= 2 && text.all { it in "0123456789#*" }

    companion object {
        const val SPECS = "robot_intent_specs.pb"
        const val BOT_CONFIG = "_bot_config.pb"
        const val MODEL_CONFIG = "_model_config.pb"
        const val ASSET_CONFIG = "_asset_config.pb"
        private val ENGLISH_LOCALES = setOf("en-US", "en-GB", "en-AU", "en-CA", "en-IE", "en-IN")
    }

}
