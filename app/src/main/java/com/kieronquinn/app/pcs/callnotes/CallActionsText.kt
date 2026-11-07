package com.kieronquinn.app.pcs.callnotes

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 *  The text a user sees in Call Notes' suggested tasks, in the model's output.
 *
 *  The suggested-tasks model writes a JSON array such as
 *  `[{"title": "ReminderSmartAction", "properties": {"reminder_text": "…"}}]`, which Phone's
 *  `CallActionsParser` turns into tasks. `title` is the task type and stays as it is; the user sees
 *  [TEXT_KEYS]. Text around the array (a code fence, say) is kept.
 */
class CallActionsText private constructor(
    private val prefix: String,
    private val actions: JsonArray,
    private val suffix: String,
    private val slots: List<Pair<JsonObject, String>>
) {

    companion object {
        /** Fields of a task's `properties` that Phone shows to the user. */
        val TEXT_KEYS = listOf("reminder_text", "calendar_invite_name", "description")

        /** The tasks' text in [output], or null if it is not a task array or has no text. */
        fun parse(output: String): CallActionsText? {
            val start = output.indexOf('[')
            val end = output.lastIndexOf(']')
            if (start < 0 || end <= start) return null
            val actions = try {
                JsonParser.parseString(output.substring(start, end + 1)) as? JsonArray
            } catch (e: Exception) {
                null
            } ?: return null
            val slots = actions.mapNotNull { (it as? JsonObject)?.get("properties") as? JsonObject }
                .flatMap { properties ->
                    TEXT_KEYS.filter { key ->
                        (properties.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString
                            ?.isNotBlank() == true
                    }.map { properties to it }
                }
            if (slots.isEmpty()) return null
            return CallActionsText(output.substring(0, start), actions, output.substring(end + 1), slots)
        }
    }

    /** The texts, in order. */
    val texts: List<String> = slots.map { (properties, key) -> properties.get(key).asString }

    /**
     *  The output with [replacements] (one per [texts] entry, in order) in place of the texts, or
     *  null if none changed.
     */
    fun replace(replacements: List<String>): String? {
        require(replacements.size == slots.size) { "Expected ${slots.size} texts, got ${replacements.size}" }
        var changed = false
        slots.forEachIndexed { index, (properties, key) ->
            val replacement = replacements[index]
            if (replacement.isNotBlank() && replacement != texts[index]) {
                properties.addProperty(key, replacement)
                changed = true
            }
        }
        return if (changed) prefix + actions.toString() + suffix else null
    }

}
