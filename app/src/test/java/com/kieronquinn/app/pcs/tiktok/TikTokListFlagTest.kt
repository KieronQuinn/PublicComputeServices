package com.kieronquinn.app.pcs.tiktok

import com.kieronquinn.app.pcs.voicepack.ProtoWire
import com.kieronquinn.app.pcs.voicepack.ProtoWire.Field
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.voicepack.ProtoWire.VARINT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TikTokListFlagTest {

    private fun list(vararg entries: String) = ProtoWire.encode(
        entries.map { Field(1, LENGTH_DELIMITED, it.toByteArray()) }
    )

    private fun entries(bytes: ByteArray) = ProtoWire.parse(bytes)
        .filter { it.number == 1 && it.wireType == LENGTH_DELIMITED }
        .map { String(it.bytes) }

    @Test
    fun `entry is added after the server's entries`() {
        val served = list("en-US", "de-DE", "ja-JP", "ja")
        assertEquals(
            listOf("en-US", "de-DE", "ja-JP", "ja", "tr-TR"),
            entries(TikTokListFlag.withEntry(served, "tr-TR")!!)
        )
    }

    @Test
    fun `listed entry is not added twice`() {
        assertNull(TikTokListFlag.withEntry(list("en", "tr", "de"), "tr"))
    }

    @Test
    fun `empty list gets the entry`() {
        assertEquals(listOf("tr"), entries(TikTokListFlag.withEntry(ByteArray(0), "tr")!!))
    }

    @Test
    fun `other fields are kept`() {
        val served = ProtoWire.encode(listOf(Field(1, LENGTH_DELIMITED, "en".toByteArray()), Field(2, VARINT, 5L)))
        val fields = ProtoWire.parse(TikTokListFlag.withEntry(served, "tr")!!)
        assertEquals(5L, fields.single { it.number == 2 }.value)
        assertEquals(listOf("en", "tr"), entries(ProtoWire.encode(fields)))
    }

    @Test
    fun `only missing entries are added, in order`() {
        assertEquals(
            listOf("DE", "US", "TR", "AZ"),
            entries(TikTokListFlag.withEntries(list("DE", "US"), listOf("TR", "US", "AZ", "TR"))!!)
        )
    }

    @Test
    fun `listed entries are not added again`() {
        assertNull(TikTokListFlag.withEntries(list("TR", "AZ"), listOf("AZ", "TR")))
        assertNull(TikTokListFlag.withEntries(list("TR"), emptyList()))
    }

}
