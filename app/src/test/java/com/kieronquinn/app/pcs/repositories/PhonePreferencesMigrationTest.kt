package com.kieronquinn.app.pcs.repositories

import org.junit.Assert.*
import org.junit.Test

class PhonePreferencesMigrationTest {
    private fun migrate(properties: Map<String, String?> = emptyMap(), existing: Map<String, Any> = emptyMap()) =
        PhonePreferencesMigration.pendingValues(existing, properties::get)

    @Test fun unsetPropertiesKeepUpstreamDefaults() {
        assertEquals(
            mapOf(
                "phone_manifest_handling_enabled" to false,
                "phone_debug_logging" to false,
                PhonePreferencesMigration.MARKER to true
            ),
            migrate()
        )
    }

    @Test fun importsOnlyUpstreamProperties() {
        val values = PhonePreferencesMigration.pendingValues(emptyMap<String, Any>()) {
            when (it) {
                "persist.phone.pcs_enabled" -> "1"
                "persist.pcs.debug" -> "true"
                else -> error("Unexpected property: $it")
            }
        }
        assertEquals(
            mapOf(
                "phone_manifest_handling_enabled" to true,
                "phone_debug_logging" to true,
                PhonePreferencesMigration.MARKER to true
            ),
            values
        )
    }

    @Test fun existingPreferencesWinWithoutBeingRewritten() {
        val saved = mapOf<String, Any>(
            "phone_manifest_handling_enabled" to false,
            "phone_debug_logging" to false,
            "phone_dobby_region" to "GB"
        )
        val pending = PhonePreferencesMigration.pendingValues(saved) { error("Unexpected legacy read: $it") }
        assertEquals(mapOf(PhonePreferencesMigration.MARKER to true), pending)
        assertEquals("GB", (saved + pending)["phone_dobby_region"])
    }

    @Test fun changedPropertiesAreNeverReadAfterMigration() {
        val saved = migrate(mapOf("persist.phone.pcs_enabled" to "true"))
        assertTrue(PhonePreferencesMigration.pendingValues(saved) { error("Read after migration") }.isEmpty())
        assertEquals(true, saved["phone_manifest_handling_enabled"])
    }

    @Test fun booleanParsingKeepsUpstreamSemantics() {
        val enabled = setOf("1", "y", "yes", "on", "true")
        val disabled = listOf(null, "", "0", "n", "no", "off", "false", "TRUE", " true ")
        (enabled + disabled).forEach { value ->
            val values = migrate(mapOf("persist.phone.pcs_enabled" to value, "persist.pcs.debug" to value))
            assertEquals(value, value in enabled, values["phone_manifest_handling_enabled"])
            assertEquals(value, value in enabled, values["phone_debug_logging"])
        }
    }
}
