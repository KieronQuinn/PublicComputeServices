package com.kieronquinn.app.pcs.model.phone

import com.kieronquinn.app.pcs.voicepack.ProtoWire
import com.kieronquinn.app.pcs.voicepack.ProtoWire.Field
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED
import java.util.Locale

/**
 *  Hold for Me and Direct My Call are offered on toll-free numbers of their countries, on numbers
 *  Google's business list has, and on numbers matching a client-side allowlist of
 *  `<COUNTRY>_<prefix>` entries (`AtlasBusinessFinderImpl`, `XatuSupportedNumbers`): a number
 *  matches when its region is the country and it starts with the prefix. An entry with an empty
 *  prefix matches every number of the country.
 */
object NumberAllowlist {

    /** StringList.value */
    private const val VALUE = 1

    /** The entry that matches every number of [country] (an ISO 3166 code). */
    fun everyNumber(country: String) = "${country.uppercase(Locale.ROOT)}_"

    /** The allowlist (a serialised StringList) with every number of [country] added. */
    fun withEveryNumber(allowlist: ByteArray, country: String): ByteArray {
        val entry = everyNumber(country)
        val fields = ProtoWire.parse(allowlist)
        if (fields.any { it.number == VALUE && it.wireType == LENGTH_DELIMITED && String(it.bytes) == entry }) {
            return allowlist
        }
        return ProtoWire.encode(fields + Field(VALUE, LENGTH_DELIMITED, entry.toByteArray()))
    }

}
