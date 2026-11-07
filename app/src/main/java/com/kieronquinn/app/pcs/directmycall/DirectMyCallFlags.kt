package com.kieronquinn.app.pcs.directmycall

import com.kieronquinn.app.pcs.voicepack.ProtoWire
import com.kieronquinn.app.pcs.voicepack.ProtoWire.Field
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.voicepack.ProtoWire.VARINT

/**
 *  The Direct My Call flag values that let it listen in the phone's language. Values are changed
 *  on the protobuf wire format, keeping every other field as it is.
 */
object DirectMyCallFlags {

    /** DataFileGroup.locale: the languages the model group may be used for. */
    private const val GROUP_LOCALE = 25
    /** SupportedVersions.version: a range of speech pack versions. */
    private const val SUPPORTED_VERSION = 1
    private const val MIN_VERSION = 1
    private const val MAX_VERSION = 2

    /**
     *  The model group with [languageTag] added to its languages. Phone downloads the group for
     *  en-US only and refuses it for a session in another language.
     */
    fun groupWithLocale(group: ByteArray, languageTag: String): ByteArray {
        val fields = ProtoWire.parse(group)
        val present = fields.any {
            it.number == GROUP_LOCALE && it.wireType == LENGTH_DELIMITED && String(it.bytes) == languageTag
        }
        if (present) return group
        return ProtoWire.encode(fields + Field(GROUP_LOCALE, LENGTH_DELIMITED, languageTag.toByteArray()))
    }

    /**
     *  The speech pack versions Direct My Call accepts, with every version added. The server lists
     *  the versions of the current English packs; the pack installed for another language may be
     *  outside them, and Phone then reports the models as missing
     *  (`XatuPipelineRunner not ready: FAILURE_MODELS_NOT_AVAILABLE`).
     */
    fun supportedVersionsWithAny(supported: ByteArray): ByteArray {
        val any = ProtoWire.encode(listOf(
            Field(MIN_VERSION, VARINT, 0L),
            Field(MAX_VERSION, VARINT, Int.MAX_VALUE.toLong())
        ))
        val fields = ProtoWire.parse(supported)
        if (fields.any { it.number == SUPPORTED_VERSION && it.wireType == LENGTH_DELIMITED && it.bytes.contentEquals(any) }) {
            return supported
        }
        return ProtoWire.encode(fields + Field(SUPPORTED_VERSION, LENGTH_DELIMITED, any))
    }

}
