package com.kieronquinn.app.pcs.voicepack

import com.kieronquinn.app.pcs.voicepack.ProtoWire.Field
import com.kieronquinn.app.pcs.voicepack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.voicepack.ProtoWire.VARINT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

/**
 *  Field values from Dialer 246's "dobby-duplex-files" MDD group on a Pixel 10 Pro
 *  (gms_icing_mdd_groups.xml, November 2026) and its UK "beesly-duplex-files" group.
 */
class VoicePackLayoutTest {

    @get:Rule
    val folder = TemporaryFolder()

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

    private fun group(
        name: String = "dobby-duplex-files",
        files: List<Field> = listOf(file()),
        version: Long = 1791597920L
    ) = ProtoWire.encode(listOf(text(1, name)) + files + listOf(
        Field(3, VARINT, 604800L),
        text(6, "com.google.android.dialer"),
        Field(10, VARINT, version),
        text(25, "en-US")
    ))

    private val beeslyChecksum = "9b377016822d002dd25193c535aec51d78188958"

    private fun beeslyFile() = Field(2, LENGTH_DELIMITED, ProtoWire.encode(listOf(
        Field(4, VARINT, 32360858L),
        text(5, beeslyChecksum)
    )))

    @Test
    fun `reads version size and checksum`() {
        val group = VoicePackLayout.parseGroup(VoicePack.CALL_SCREEN, group())!!
        assertEquals(1791597920L, group.version)
        assertEquals(144413409L, group.size)
        assertEquals(checksum, group.checksum)
        assertEquals("1791597920_$checksum", group.sourceId)
    }

    @Test
    fun `normalises an upper case checksum`() {
        val group = VoicePackLayout.parseGroup(VoicePack.CALL_SCREEN, group(files = listOf(file(checksum.uppercase()))))!!
        assertEquals(checksum, group.checksum)
    }

    @Test
    fun `ignores other groups and groups with several files`() {
        assertNull(VoicePackLayout.parseGroup(VoicePack.CALL_SCREEN, group(name = "beesly-duplex-files")))
        assertNull(VoicePackLayout.parseGroup(VoicePack.TAKE_A_MESSAGE, group()))
        assertNull(VoicePackLayout.parseGroup(VoicePack.CALL_SCREEN, group(files = listOf(file(), file()))))
        assertNull(VoicePackLayout.parseGroup(VoicePack.CALL_SCREEN, group(files = listOf(file("not-a-sha1")))))
    }

    @Test
    fun `marker round trip and earlier layout`() {
        val marker = VoicePackLayout.Marker(1791597920L, checksum, "tr-TR", "abc", "tr-tr-x-tmc-local")
        assertEquals(marker, VoicePackLayout.Marker.read(marker.write()))
        assertNull(VoicePackLayout.Marker.read("source=1791597920\nlocale=tr-TR\ntranslation=abc\n"))
    }

    @Test
    fun `reads the Take a Message group and ignores its empty region group`() {
        val group = VoicePackLayout.parseGroup(VoicePack.TAKE_A_MESSAGE,
            group(name = "beesly-duplex-files", files = listOf(beeslyFile()), version = 1757403523L))!!
        assertEquals("1757403523_$beeslyChecksum", group.sourceId)
        assertEquals(32360858L, group.size)
        // Outside the supported regions Phone returns the group with no files.
        assertNull(VoicePackLayout.parseGroup(VoicePack.TAKE_A_MESSAGE,
            group(name = "beesly-duplex-files", files = emptyList())))
    }

    @Test
    fun `finds a ready pack only for its own feature and zip`() {
        val filesDir = folder.root
        val group = VoicePackLayout.Group(1757403523L, beeslyChecksum, 32360858L)
        val locale = Locale.forLanguageTag("tr-TR")
        val directory = VoicePackLayout.packDirectory(filesDir, VoicePack.TAKE_A_MESSAGE, locale, group.sourceId)
        assertEquals(File(filesDir, "pcs-take-a-message/tr-TR/${group.sourceId}"), directory)
        directory.mkdirs()
        File(directory, VoicePack.TAKE_A_MESSAGE.specs).writeText("")
        File(directory, VoicePackLayout.MARKER).writeText(
            VoicePackLayout.Marker(group.version, group.checksum, "tr-TR", "abc", "tr-tr-x-tmc-local").write()
        )

        val ready = VoicePackLayout.findReadyPack(filesDir, VoicePack.TAKE_A_MESSAGE, locale, group)
        assertNotNull(ready)
        assertEquals(directory, ready!!.directory)
        assertEquals(1757403523L, ready.version)
        assertNull(VoicePackLayout.findReadyPack(filesDir, VoicePack.CALL_SCREEN, locale, group))
        assertNull(VoicePackLayout.findReadyPack(filesDir, VoicePack.TAKE_A_MESSAGE, locale,
            group.copy(version = 1747122540L)))
        assertNull(VoicePackLayout.findReadyPack(filesDir, VoicePack.TAKE_A_MESSAGE,
            Locale.forLanguageTag("de-DE"), group))
    }

    @Test
    fun `keeps source recordings that are not speech`() {
        assertEquals(true, VoicePack.keepsSourceRecording("srdtmf/1.wav"))
        assertEquals(true, VoicePack.keepsSourceRecording("prompts/silence-500ms.wav"))
        assertEquals(false, VoicePack.keepsSourceRecording("prompts/greeting.wav"))
    }

    @Test
    fun `reads groups from Phone's MDD metadata`() {
        val encoder = java.util.Base64.getEncoder()
        val beesly = group(name = "beesly-duplex-files", files = listOf(beeslyFile()), version = 1757403523L)
        val xml = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            |<map>
            |    <string name="beesly-duplex-files|com.google.android.dialer">${encoder.encodeToString(beesly)}</string>
            |    <string name="dobby-duplex-files|com.google.android.dialer">${encoder.encodeToString(group())}</string>
            |    <string name="other">not base64!</string>
            |</map>""".trimMargin()
        assertEquals(listOf("1757403523_$beeslyChecksum"),
            VoicePackLayout.readMddGroups(VoicePack.TAKE_A_MESSAGE, xml).map { it.sourceId })
        assertEquals(listOf("1791597920_$checksum"),
            VoicePackLayout.readMddGroups(VoicePack.CALL_SCREEN, xml).map { it.sourceId })
    }

    @Test
    fun `Phone runs the requested zip or a newer download`() {
        // Dialer 246 asks for the US zip (1747122540) and runs the UK v5_4 download instead.
        val requested = VoicePackLayout.Group(1747122540L, "5bc9185544754e0fba73801d09384a57fd4f15ee", 32695219L)
        val newer = VoicePackLayout.Group(1757403523L, beeslyChecksum, 32360858L)
        val older = VoicePackLayout.Group(1747000000L, "0".repeat(40), 1L)
        assertEquals(requested, VoicePackLayout.runningGroup(requested, listOf(newer, requested)))
        assertEquals(newer, VoicePackLayout.runningGroup(requested, listOf(older, newer)))
        assertNull(VoicePackLayout.runningGroup(requested, listOf(older)))
    }

}
