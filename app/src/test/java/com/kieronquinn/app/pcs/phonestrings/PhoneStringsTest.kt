package com.kieronquinn.app.pcs.phonestrings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class PhoneStringsTest {

    private fun parse(json: String) = PhoneStrings.parse(json.reader())

    private val assets = File("src/main/assets")

    private fun openAsset(path: String) = File(assets, path).takeIf { it.isFile }?.inputStream()

    @Test
    fun `valid translation`() {
        val strings = parse("""{"version":1,"locale":"tr-TR","strings":{"a":"Bir","b":"<b>İki</b>"}}""")
        assertEquals("tr", strings.locale.language)
        assertEquals(mapOf("a" to "Bir", "b" to "<b>İki</b>"), strings.strings)
    }

    @Test
    fun `invalid translations are refused`() {
        listOf(
            """{"version":2,"locale":"tr-TR","strings":{"a":"Bir"}}""",
            """{"version":1,"strings":{"a":"Bir"}}""",
            """{"version":1,"locale":"tr-TR","strings":{}}""",
            """{"version":1,"locale":"tr-TR","strings":{"a":" "}}"""
        ).forEach { json ->
            assertThrows(PhoneStrings.InvalidException::class.java) { parse(json) }
        }
    }

    @Test
    fun `shipped Turkish translation is found for the language`() {
        val strings = PhoneStrings.forLocale(Locale.forLanguageTag("tr-TR"), ::openAsset)!!
        assertEquals("tr", strings.locale.language)
        assertTrue(strings.strings.size > 100)
        // Placeholders Phone fills in are kept.
        assertTrue(strings.strings.values.any { "%1\$s" in it || "^1" in it })
    }

    @Test
    fun `no translation for a language PCS does not ship`() {
        assertNull(PhoneStrings.forLocale(Locale.forLanguageTag("xx-YY"), ::openAsset))
    }

    @Test
    fun `translation for another language is not used`() {
        val strings = PhoneStrings.forLocale(Locale.forLanguageTag("de-DE")) {
            """{"version":1,"locale":"tr-TR","strings":{"a":"Bir"}}""".byteInputStream()
        }
        assertNull(strings)
    }

}
