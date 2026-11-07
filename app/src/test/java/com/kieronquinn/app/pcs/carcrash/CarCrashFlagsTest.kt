package com.kieronquinn.app.pcs.carcrash

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CarCrashFlagsTest {

    private fun locales(locale: Locale) =
        CarCrashFlags.entriesFor(CarCrashFlags.SUPPORTED_LOCALES, locale, emptyList())

    private fun countries(vararg countries: String?) =
        CarCrashFlags.entriesFor(CarCrashFlags.SUPPORTED_COUNTRIES, Locale.ROOT, countries.toList())

    @Test
    fun `locale flag gets the phone's locale without extensions`() {
        assertEquals(listOf("tr-TR"), locales(Locale.forLanguageTag("tr-TR")))
        assertEquals(listOf("pl-PL"), locales(Locale.forLanguageTag("pl-PL-u-fw-mon")))
        assertEquals(listOf("sr-Latn-RS"), locales(Locale.forLanguageTag("sr-Latn-RS")))
        assertEquals(listOf("tr"), locales(Locale.forLanguageTag("tr")))
    }

    @Test
    fun `locale entry builds back into the compared locale`() {
        val phone = Locale.forLanguageTag("de-AT-u-ca-gregory")
        val entry = locales(phone).single()
        assertEquals(phone.stripExtensions(), Locale.Builder().setLanguageTag(entry).build())
    }

    @Test
    fun `undetermined locale is left alone`() {
        assertEquals(emptyList<String>(), locales(Locale.ROOT))
    }

    @Test
    fun `country flag gets the network and SIM countries in upper case`() {
        assertEquals(listOf("TR"), countries("tr", "TR"))
        assertEquals(listOf("DE", "TR"), countries("de", "tr"))
        assertEquals(listOf("TR"), countries("", "tr"))
        assertEquals(listOf("TR"), countries(null, " tr "))
    }

    @Test
    fun `invalid countries are left out`() {
        assertEquals(emptyList<String>(), countries(null, ""))
        assertEquals(emptyList<String>(), countries("tur", "1"))
    }

    @Test
    fun `other flags are left alone`() {
        assertEquals(
            emptyList<String>(),
            CarCrashFlags.entriesFor("12", Locale.forLanguageTag("tr-TR"), listOf("tr"))
        )
    }

}
