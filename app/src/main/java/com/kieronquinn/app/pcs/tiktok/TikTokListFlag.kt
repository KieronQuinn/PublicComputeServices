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
    fun withEntry(list: ByteArray, entry: String): ByteArray? = withEntries(list, listOf(entry))

    /** [list] with the [entries] it lacks added in order, or null when all are listed. */
    fun withEntries(list: ByteArray, entries: List<String>): ByteArray? {
        val fields = ProtoWire.parse(list)
        val listed = fields.filter { it.number == ENTRY && it.wireType == LENGTH_DELIMITED }
            .mapTo(HashSet()) { String(it.bytes) }
        val added = entries.distinct().filter { it !in listed }
        if (added.isEmpty()) return null
        return ProtoWire.encode(fields + added.map { Field(ENTRY, LENGTH_DELIMITED, it.toByteArray()) })
    }

}
