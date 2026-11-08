package com.kieronquinn.app.pcs.model.phone

import com.kieronquinn.app.pcs.voicepack.ProtoWire
import com.kieronquinn.app.pcs.voicepack.ProtoWire.Field
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberAllowlistTest {

    private fun list(vararg entries: String) =
        ProtoWire.encode(entries.map { Field(1, LENGTH_DELIMITED, it.toByteArray()) })

    private fun entries(bytes: ByteArray) = ProtoWire.parse(bytes).map { String(it.bytes) }

    @Test
    fun `every number of the country is added`() {
        val server = list("US_+1800", "GB_+44800")
        assertEquals(listOf("US_+1800", "GB_+44800", "TR_"), entries(NumberAllowlist.withEveryNumber(server, "tr")))
    }

    @Test
    fun `empty allowlist`() {
        assertEquals(listOf("TR_"), entries(NumberAllowlist.withEveryNumber(ByteArray(0), "TR")))
    }

    @Test
    fun `allowlist already listing the country is unchanged`() {
        val server = list("TR_")
        assertArrayEquals(server, NumberAllowlist.withEveryNumber(server, "TR"))
    }

}
