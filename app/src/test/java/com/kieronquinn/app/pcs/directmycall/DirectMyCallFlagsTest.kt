package com.kieronquinn.app.pcs.directmycall

import com.kieronquinn.app.pcs.voicepack.ProtoWire
import com.kieronquinn.app.pcs.voicepack.ProtoWire.Field
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.voicepack.ProtoWire.VARINT
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectMyCallFlagsTest {

    private fun text(number: Int, value: String) = Field(number, LENGTH_DELIMITED, value.toByteArray())

    /** The shape of the xatu-models group PCS serves: name, file, version and the en-US locale. */
    private val group = ProtoWire.encode(listOf(
        text(1, "xatu-models"),
        Field(10, VARINT, 1653253804L),
        text(25, "en-US")
    ))

    private fun range(min: Long, max: Long) = Field(1, LENGTH_DELIMITED, ProtoWire.encode(listOf(
        Field(1, VARINT, min), Field(2, VARINT, max)
    )))

    @Test
    fun `group gets the phone's locale`() {
        val locales = ProtoWire.parse(DirectMyCallFlags.groupWithLocale(group, "tr-TR"))
            .filter { it.number == 25 }.map { String(it.bytes) }
        assertEquals(listOf("en-US", "tr-TR"), locales)
    }

    @Test
    fun `group already listing the locale is unchanged`() {
        assertArrayEquals(group, DirectMyCallFlags.groupWithLocale(group, "en-US"))
    }

    @Test
    fun `every speech pack version is accepted`() {
        val server = ProtoWire.encode(listOf(range(11000, 11999)))
        val ranges = ProtoWire.parse(DirectMyCallFlags.supportedVersionsWithAny(server)).map { field ->
            ProtoWire.parse(field.bytes).map { it.value as Long }
        }
        assertEquals(listOf(listOf(11000L, 11999L), listOf(0L, Int.MAX_VALUE.toLong())), ranges)
        val twice = DirectMyCallFlags.supportedVersionsWithAny(DirectMyCallFlags.supportedVersionsWithAny(server))
        assertEquals(2, ProtoWire.parse(twice).size)
    }

}
