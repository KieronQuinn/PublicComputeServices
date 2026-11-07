package com.kieronquinn.app.pcs.callscreen.pack

import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.Field
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.VARINT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  Field values from Dialer 246's "dobby-duplex-files" MDD group on a Pixel 10 Pro
 *  (gms_icing_mdd_groups.xml, November 2026).
 */
class CallScreenPackLayoutTest {

    private val url = "https://dl.google.com/hfm/pca/ioPIseEU5A1CgLWwK2765QbfK1P4lfH00BlhImRxwCH8ONgQajFZ2AQvAjTpVAPi/" +
        "extraction/call_assistant_extraction-v6_5-en_us.zip"
    private val checksum = "212349afaf94d24b8b91ebb2ce6dafd5ba7bac1c"

    private fun text(number: Int, value: String) = Field(number, LENGTH_DELIMITED, value.toByteArray())

    private fun file(checksum: String = this.checksum) = Field(2, LENGTH_DELIMITED, ProtoWire.encode(listOf(
        text(2, url),
        Field(4, VARINT, 144413409L),
        text(5, checksum),
        text(7, "dobby_duplex_model")
    )))

    private fun group(name: String = "dobby-duplex-files", files: List<Field> = listOf(file())) =
        ProtoWire.encode(listOf(text(1, name)) + files + listOf(
            Field(3, VARINT, 604800L),
            text(6, "com.google.android.dialer"),
            Field(10, VARINT, 1791597920L),
            text(25, "en-US")
        ))

    @Test
    fun `reads version size and checksum`() {
        val group = CallScreenPackLayout.parseGroup(group())!!
        assertEquals(1791597920L, group.version)
        assertEquals(144413409L, group.size)
        assertEquals(checksum, group.checksum)
        assertEquals("1791597920_$checksum", group.sourceId)
    }

    @Test
    fun `normalises an upper case checksum`() {
        val group = CallScreenPackLayout.parseGroup(group(files = listOf(file(checksum.uppercase()))))!!
        assertEquals(checksum, group.checksum)
    }

    @Test
    fun `ignores other groups and groups with several files`() {
        assertNull(CallScreenPackLayout.parseGroup(group(name = "beesly-duplex-files")))
        assertNull(CallScreenPackLayout.parseGroup(group(files = listOf(file(), file()))))
        assertNull(CallScreenPackLayout.parseGroup(group(files = listOf(file("not-a-sha1")))))
    }

    @Test
    fun `marker round trip and earlier layout`() {
        val marker = CallScreenPackLayout.Marker(1791597920L, checksum, "tr-TR", "abc", "tr-tr-x-tmc-local")
        assertEquals(marker, CallScreenPackLayout.Marker.read(marker.write()))
        assertNull(CallScreenPackLayout.Marker.read("source=1791597920\nlocale=tr-TR\ntranslation=abc\n"))
    }

}
