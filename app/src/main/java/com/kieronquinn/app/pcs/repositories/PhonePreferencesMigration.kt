package com.kieronquinn.app.pcs.repositories

import android.content.SharedPreferences
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_get

/** Import properties supported by upstream releases once. Explicit UI settings always win. */
internal object PhonePreferencesMigration {
    internal const val MARKER = "phone_properties_migrated_v1"

    fun migrate(preferences: SharedPreferences) {
        val values = pendingValues(preferences.all, ::SystemProperties_get)
        if (values.isEmpty()) return
        val editor = preferences.edit()
        values.forEach { (key, value) ->
            editor.putBoolean(key, value)
        }
        // Values and marker are persisted together. A failed write is retried on the next open.
        check(editor.commit()) { "Unable to migrate legacy Phone properties" }
    }

    internal fun pendingValues(
        existing: Map<String, *>,
        readProperty: (String) -> String?
    ): Map<String, Boolean> {
        if (existing[MARKER] == true) return emptyMap()
        val values = mutableMapOf<String, Boolean>()
        fun import(key: String, value: () -> Boolean) {
            if (!existing.containsKey(key)) values[key] = value()
        }
        import("phone_manifest_handling_enabled") {
            readProperty("persist.phone.pcs_enabled").legacyBoolean()
        }
        import("phone_debug_logging") {
            readProperty("persist.pcs.debug").legacyBoolean()
        }
        values[MARKER] = true
        return values
    }

    private fun String?.legacyBoolean() = this in setOf("1", "y", "yes", "on", "true")
}
