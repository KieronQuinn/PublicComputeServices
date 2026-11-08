package com.kieronquinn.app.pcs.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalLanguageTest {

    /** Journal's feature table key: a wrapper holding only the language code. */
    private data class LanguageKey(private val code: String)

    @Test
    fun `language set is recognised by its codes`() {
        assertTrue(JournalLanguage.isLanguageSet(setOf("en", "ja", "de")))
        assertTrue(JournalLanguage.isLanguageSet(setOf("en", "fil")))
        assertFalse(JournalLanguage.isLanguageSet(setOf("ja", "de")))
        assertFalse(JournalLanguage.isLanguageSet(setOf("en", "English")))
        assertFalse(JournalLanguage.isLanguageSet(setOf("en", 1)))
        assertFalse(JournalLanguage.isLanguageSet(listOf("en")))
    }

    @Test
    fun `language is added to the set once`() {
        assertEquals(listOf("en", "ja", "de", "tr"), JournalLanguage.withLanguage(setOf("en", "ja", "de"), "tr")!!.toList())
        assertNull(JournalLanguage.withLanguage(setOf("en", "tr"), "tr"))
    }

    @Test
    fun `name table gets the language's own name`() {
        val names = mapOf("en" to "English", "ja" to "Japanese")
        assertEquals(
            mapOf("en" to "English", "ja" to "Japanese", "tr" to "Turkish"),
            JournalLanguage.withLanguage(names, "tr", "Turkish") { _, _ -> null }
        )
    }

    @Test
    fun `other tables copy the English entry`() {
        val prompts = mapOf("en" to "template-en", "de" to "template-de")
        assertEquals("template-en", JournalLanguage.withLanguage(prompts, "pl", "Polish") { _, _ -> null }!!["pl"])
    }

    @Test
    fun `wrapped keys are built from the English key`() {
        val features = mapOf(LanguageKey("en") to 701, LanguageKey("ja") to 701)
        val changed = JournalLanguage.withLanguage(features, "tr", "Turkish") { _, code -> LanguageKey(code) }!!
        assertEquals(701, changed[LanguageKey("tr")])
        assertNull(JournalLanguage.withLanguage(changed, "tr", "Turkish") { _, code -> LanguageKey(code) })
    }

    @Test
    fun `tables without English are left alone`() {
        assertNull(JournalLanguage.withLanguage(mapOf("ja" to "x"), "tr", "Turkish") { _, _ -> null })
    }

    @Test
    fun `reflection asks for the language`() {
        assertEquals(
            "Write a reflection in **Turkish**. Keep it short.",
            JournalLanguage.reflectionPrompt("Write a reflection in **English**. Keep it short.", "Turkish")
        )
        assertEquals("Answer in Turkish.", JournalLanguage.reflectionPrompt("Answer in English.", "Turkish"))
        assertNull(JournalLanguage.reflectionPrompt("English or English", "Turkish"))
        assertNull(JournalLanguage.reflectionPrompt("No language here", "Turkish"))
    }

    @Test
    fun `safety model is asked for in English`() {
        val labels = mapOf("build_id" to "17593", "tpu" to "x", "locale" to "tr")
        assertEquals("en", JournalLanguage.safetyLabels(labels, "tr")!!["locale"])
        assertEquals("17593", JournalLanguage.safetyLabels(labels, "tr")!!["build_id"])
        assertNull(JournalLanguage.safetyLabels(mapOf("locale" to "en"), "tr"))
    }

}
