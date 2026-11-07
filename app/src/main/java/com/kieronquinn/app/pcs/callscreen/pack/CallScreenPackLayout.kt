package com.kieronquinn.app.pcs.callscreen.pack

import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.VARINT
import java.io.File
import java.util.Locale
import java.util.Properties

/**
 *  Where localised Call Screen packs live inside Phone's data directory, shared by the app (which
 *  builds them) and the Phone hook (which points Call Screen at them).
 *
 *  A pack is built from the English zip Phone's download manager (MDD) fetched for the
 *  "dobby-duplex-files" group. The zip is identified by the group's version and the file's SHA-1,
 *  since two zips can share a version. `files/pcs-call-screen/<locale>/<version>_<sha1>/` holds the
 *  zip's contents with the text and recordings in the phone's language. Phone's download manager
 *  never touches this directory, so it does not undo the pack.
 */
object CallScreenPackLayout {

    const val PHONE_PACKAGE = "com.google.android.dialer"
    const val GROUP_NAME = "dobby-duplex-files"
    /** Phone unzips the group's file into `files/dobby-duplex-files/<version>/`. */
    const val SOURCE_DIRECTORY = GROUP_NAME
    const val PACK_DIRECTORY = "pcs-call-screen"
    const val SPECS = "agenticcallscreen_robot_intent_specs.pb"
    const val MARKER = "pcs_call_screen.properties"

    private const val GROUP_FIELD_NAME = 1
    private const val GROUP_FIELD_FILE = 2
    private const val GROUP_FIELD_VERSION = 10
    private const val FILE_FIELD_SIZE = 4
    private const val FILE_FIELD_CHECKSUM = 5

    private const val KEY_VERSION = "version"
    private const val KEY_CHECKSUM = "checksum"
    private const val KEY_LOCALE = "locale"
    private const val KEY_TRANSLATION = "translation"
    private const val KEY_VOICE = "voice"

    /** The parts of an MDD "dobby-duplex-files" group that identify its zip. */
    data class Group(val version: Long, val checksum: String, val size: Long) {
        val sourceId get() = sourceId(version, checksum)
    }

    /**
     *  Reads an MDD DataFileGroup (1 = name, 2 = files, 10 = version; file: 4 = size,
     *  5 = SHA-1). Returns null for other groups, and for groups without exactly one file.
     */
    fun parseGroup(bytes: ByteArray): Group? {
        val fields = ProtoWire.parseOrNull(bytes) ?: return null
        val name = fields.firstOrNull { it.number == GROUP_FIELD_NAME }?.let(ProtoWire::text)
        if (name != GROUP_NAME) return null
        val version = fields.firstOrNull {
            it.number == GROUP_FIELD_VERSION && it.wireType == VARINT
        }?.value as? Long ?: return null
        val file = fields.filter { it.number == GROUP_FIELD_FILE && it.wireType == LENGTH_DELIMITED }
            .singleOrNull()?.let { ProtoWire.parseOrNull(it.bytes) } ?: return null
        val checksum = file.firstOrNull { it.number == FILE_FIELD_CHECKSUM }?.let(ProtoWire::text)
            ?.lowercase(Locale.ROOT)?.takeIf { it.matches(SHA1) } ?: return null
        val size = file.firstOrNull {
            it.number == FILE_FIELD_SIZE && it.wireType == VARINT
        }?.value as? Long ?: return null
        return Group(version, checksum, size)
    }

    private val SHA1 = Regex("[0-9a-f]{40}")

    fun sourceId(version: Long, checksum: String) = "${version}_$checksum"

    data class Marker(
        val version: Long,
        val checksum: String,
        val locale: String,
        val translation: String,
        val voice: String
    ) {
        val sourceId get() = sourceId(version, checksum)

        fun write(): String = buildString {
            append("$KEY_VERSION=$version\n")
            append("$KEY_CHECKSUM=$checksum\n")
            append("$KEY_LOCALE=$locale\n")
            append("$KEY_TRANSLATION=$translation\n")
            append("$KEY_VOICE=$voice\n")
        }

        companion object {
            /** Null for a marker without every field, such as one from an earlier layout. */
            fun read(text: String): Marker? {
                val properties = Properties().apply { load(text.reader()) }
                return Marker(
                    version = properties.getProperty(KEY_VERSION)?.toLongOrNull() ?: return null,
                    checksum = properties.getProperty(KEY_CHECKSUM) ?: return null,
                    locale = properties.getProperty(KEY_LOCALE) ?: return null,
                    translation = properties.getProperty(KEY_TRANSLATION) ?: return null,
                    voice = properties.getProperty(KEY_VOICE).orEmpty()
                )
            }
        }
    }

    fun packRoot(filesDir: File) = File(filesDir, PACK_DIRECTORY)

    fun packDirectory(filesDir: File, locale: Locale, sourceId: String) =
        File(File(packRoot(filesDir), locale.toLanguageTag()), sourceId)

    class Pack(val locale: Locale, val version: Long, val directory: File)

    /**
     *  The localised pack in the phone's language built from the zip of [group], if there is one.
     *  Read inside Phone's process; the pack's own locale tag (tr-TR) is used even if the phone's
     *  region differs.
     */
    fun findReadyPack(filesDir: File, phoneLocale: Locale, group: Group): Pack? {
        val locales = packRoot(filesDir).listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { Locale.forLanguageTag(it.name) }
            .filter { it.language == phoneLocale.language }
            .sortedByDescending { it.country == phoneLocale.country }
        return locales.firstNotNullOfOrNull { locale ->
            readyPack(filesDir, locale, group)?.let { Pack(locale, group.version, it) }
        }
    }

    fun readyPack(filesDir: File, locale: Locale, group: Group): File? {
        val directory = packDirectory(filesDir, locale, group.sourceId)
        val marker = File(directory, MARKER).takeIf { it.isFile }?.readText()?.let(Marker::read)
            ?: return null
        if (marker.version != group.version || marker.checksum != group.checksum) return null
        if (marker.locale != locale.toLanguageTag()) return null
        return directory.takeIf { File(it, SPECS).isFile }
    }

}
