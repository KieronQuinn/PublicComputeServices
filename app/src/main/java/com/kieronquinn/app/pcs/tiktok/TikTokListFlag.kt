package com.kieronquinn.app.pcs.tiktok

import com.kieronquinn.app.pcs.voicepack.ProtoWire
import com.kieronquinn.app.pcs.voicepack.ProtoWire.Field
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED

/**
 *  A TikTok flag whose value is a list of strings (repeated string, field 1). The server's entries
 *  are kept and only the phone's entry is added, so later server changes still come through.
 */
object TikTokListFlag {

    private const val ENTRY = 1

    /** [list] with [entry] added, or null when it is already listed. */
    fun withEntry(list: ByteArray, entry: String): ByteArray? {
        val fields = ProtoWire.parse(list)
        val present = fields.any {
            it.number == ENTRY && it.wireType == LENGTH_DELIMITED && String(it.bytes) == entry
        }
        if (present) return null
        return ProtoWire.encode(fields + Field(ENTRY, LENGTH_DELIMITED, entry.toByteArray()))
    }

}
