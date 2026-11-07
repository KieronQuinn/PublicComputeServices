package com.kieronquinn.app.pcs.voicepack

import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer

/**
 *  Schema-less protobuf wire format. Phone's voice pack files have no published schema, so they
 *  are read as a list of fields in their original order and only the text and locale fields are
 *  changed. Everything else is written back byte for byte.
 */
internal object ProtoWire {

    const val VARINT = 0
    const val FIXED64 = 1
    const val LENGTH_DELIMITED = 2
    const val FIXED32 = 5

    /**
     *  One field. [value] holds the decoded varint for [VARINT], and the raw bytes for the other
     *  wire types.
     */
    data class Field(val number: Int, val wireType: Int, val value: Any) {
        val bytes: ByteArray get() = value as ByteArray
        fun withBytes(bytes: ByteArray) = copy(value = bytes)
    }

    class MalformedException(message: String) : Exception(message)

    fun parse(buffer: ByteArray): List<Field> {
        val fields = ArrayList<Field>()
        var index = 0
        while (index < buffer.size) {
            val (key, afterKey) = readVarint(buffer, index)
            index = afterKey
            val number = (key ushr 3).toInt()
            val wireType = (key and 7).toInt()
            if (number == 0) throw MalformedException("Field number 0 at $index")
            when (wireType) {
                VARINT -> {
                    val (value, next) = readVarint(buffer, index)
                    fields += Field(number, wireType, value)
                    index = next
                }
                FIXED64 -> {
                    fields += Field(number, wireType, slice(buffer, index, 8))
                    index += 8
                }
                LENGTH_DELIMITED -> {
                    val (length, next) = readVarint(buffer, index)
                    if (length < 0 || length > buffer.size - next) {
                        throw MalformedException("Length $length out of bounds at $next")
                    }
                    fields += Field(number, wireType, slice(buffer, next, length.toInt()))
                    index = next + length.toInt()
                }
                FIXED32 -> {
                    fields += Field(number, wireType, slice(buffer, index, 4))
                    index += 4
                }
                else -> throw MalformedException("Unsupported wire type $wireType (field $number)")
            }
        }
        return fields
    }

    /** Parses [buffer] as a message, or returns null if it is not one. */
    fun parseOrNull(buffer: ByteArray): List<Field>? = try {
        parse(buffer)
    } catch (e: MalformedException) {
        null
    }

    fun encode(fields: List<Field>): ByteArray {
        val out = ByteArrayOutputStream()
        for (field in fields) {
            writeVarint(out, (field.number.toLong() shl 3) or field.wireType.toLong())
            when (field.wireType) {
                VARINT -> writeVarint(out, field.value as Long)
                LENGTH_DELIMITED -> {
                    writeVarint(out, field.bytes.size.toLong())
                    out.write(field.bytes)
                }
                else -> out.write(field.bytes)
            }
        }
        return out.toByteArray()
    }

    /** The field's bytes as text, if they are valid UTF-8 made only of printable characters. */
    fun text(field: Field): String? {
        if (field.wireType != LENGTH_DELIMITED) return null
        return text(field.bytes)
    }

    fun text(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val decoded = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            return null
        }
        return decoded.takeIf { it.all(::isPrintable) }
    }

    /** Same rule as Python's str.isprintable(), which the desktop pack builder used. */
    private fun isPrintable(char: Char): Boolean {
        if (char == ' ') return true
        return when (Character.getType(char).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE,
            Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
            Character.SPACE_SEPARATOR -> false
            else -> true
        }
    }

    private fun readVarint(buffer: ByteArray, start: Int): Pair<Long, Int> {
        var shift = 0
        var value = 0L
        var index = start
        while (true) {
            if (index >= buffer.size) throw MalformedException("Truncated varint at $start")
            if (shift > 63) throw MalformedException("Varint too long at $start")
            val byte = buffer[index++].toInt() and 0xFF
            value = value or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return value to index
            shift += 7
        }
    }

    private fun writeVarint(out: ByteArrayOutputStream, input: Long) {
        var value = input
        while (true) {
            val byte = (value and 0x7F).toInt()
            value = value ushr 7
            if (value != 0L) {
                out.write(byte or 0x80)
            } else {
                out.write(byte)
                return
            }
        }
    }

    private fun slice(buffer: ByteArray, start: Int, length: Int): ByteArray {
        if (start + length > buffer.size) throw MalformedException("Truncated field at $start")
        return buffer.copyOfRange(start, start + length)
    }

}
