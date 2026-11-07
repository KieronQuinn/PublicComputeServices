package com.kieronquinn.app.pcs.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WeatherSummaryTest {

    private val germanPrompt = "<ctrl99>user\nWrite a short and engaging weather summary in German " +
        "based on the following facts:\n\nHigh 21°\n<ctrl100>\n<ctrl99>model"

    @Test
    fun `prompt asks for the language and opens the model turn on a new line`() {
        assertEquals(
            "<ctrl99>user\nWrite a short and engaging weather summary in Turkish based on the " +
                "following facts:\n\nHigh 21°\n<ctrl100>\n<ctrl99>model\n",
            WeatherSummary.promptFor(germanPrompt, "Turkish")
        )
    }

    @Test
    fun `prompt without a model turn only gets the language`() {
        assertEquals(
            "weather summary in Polish, facts",
            WeatherSummary.promptFor("weather summary in German, facts", "Polish")
        )
    }

    @Test
    fun `other text is not a summary prompt`() {
        assertNull(WeatherSummary.promptFor("Use German and return the results", "Turkish"))
    }

    @Test
    fun `language names are English`() {
        assertEquals("Turkish", WeatherSummary.languageName(Locale.forLanguageTag("tr-TR")))
        assertEquals("Portuguese", WeatherSummary.languageName(Locale.forLanguageTag("pt-BR")))
        assertNull(WeatherSummary.languageName(Locale.ROOT))
    }

    @Test
    fun `only the app's language refusals are recognised`() {
        assertTrue(WeatherSummary.isUnsupportedLanguage(IllegalStateException("Device language: tr is not supported")))
        assertFalse(WeatherSummary.isUnsupportedLanguage(IllegalStateException("Unsupported request type: X")))
        assertFalse(WeatherSummary.isUnsupportedLanguage(IllegalArgumentException("Device language: tr is not supported")))
        assertFalse(WeatherSummary.isUnsupportedLanguage(null))
        assertTrue(WeatherSummary.isUnsupportedLanguageName(IllegalStateException("Language tr is not supported")))
        assertFalse(WeatherSummary.isUnsupportedLanguageName(IllegalStateException("Device language: tr is not supported")))
    }

}
