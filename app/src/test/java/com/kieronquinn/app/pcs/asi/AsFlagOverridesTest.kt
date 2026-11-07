package com.kieronquinn.app.pcs.asi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AsFlagOverridesTest {

    private val turkey = AsFlagOverrides.notificationOrganizer(Locale.forLanguageTag("tr-TR"), "TR").build()

    @Test
    fun `notification organizer is turned on`() {
        assertEquals(true, turkey.boolean("SmartNotification__enable_notification_bundle"))
        assertEquals(true, turkey.boolean("SmartNotification__enable_default_bundle_feature"))
        assertNull(turkey.boolean("SmartNotification__enable_language_detector"))
    }

    @Test
    fun `language and country are added to Google's lists`() {
        assertEquals(listOf("en", "tr"), turkey.list("SmartNotification__bundle_supported_languages", listOf("en")))
        assertEquals(listOf("en", "tr"), turkey.list("SmartNotification__supported_languages", listOf("en")))
        assertEquals(
            listOf("GB", "US", "AU", "CA", "TR"),
            turkey.list("SmartNotification__bundle_supported_countries", listOf("GB", "US", "AU", "CA"))
        )
    }

    @Test
    fun `listed entries are not added twice`() {
        assertNull(turkey.list("SmartNotification__bundle_supported_countries", listOf("tr")))
        assertNull(turkey.list("SmartNotification__supported_languages", listOf("EN", "TR")))
    }

    @Test
    fun `other flags are not changed`() {
        assertNull(turkey.boolean("SmartNotification__enable_notification_summaries"))
        assertNull(turkey.list("AutofillPersonalContext__languages_supported", listOf("en")))
    }

    @Test
    fun `no SIM country adds only the language`() {
        val overrides = AsFlagOverrides.notificationOrganizer(Locale.GERMANY, null).build()
        assertNull(overrides.list("SmartNotification__bundle_supported_countries", listOf("US")))
        assertEquals(listOf("en", "de"), overrides.list("SmartNotification__supported_languages", listOf("en")))
    }

    @Test
    fun `builders combine`() {
        val combined = AsFlagOverrides.Builder().boolean("A", true).add("L", "x")
            .plus(AsFlagOverrides.Builder().add("L", "y").boolean("B", false)).build()
        assertEquals(3, combined.size)
        assertEquals(false, combined.boolean("B"))
        assertEquals(listOf("x", "y"), combined.list("L", emptyList()))
        assertTrue(AsFlagOverrides.Builder().build().isEmpty())
    }

    @Test
    fun `daily brief is turned on and combines with notification organizer`() {
        val both = AsFlagOverrides.notificationOrganizer(Locale.forLanguageTag("tr-TR"), "TR")
            .plus(AsFlagOverrides.geminiDailyBrief()).build()
        assertEquals(true, both.boolean("EchoSmartspace__enable_gemini_ambient_data"))
        assertEquals(true, both.boolean("SmartNotification__enable_notification_bundle"))
    }

}
