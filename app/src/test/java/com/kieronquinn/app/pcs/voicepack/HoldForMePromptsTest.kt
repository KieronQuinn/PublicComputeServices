package com.kieronquinn.app.pcs.voicepack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

/** Cache file names as Dialer 246's HoldForMeLocalAudioAssetFetcher creates them ("hfm-audio-%s-%s"). */
class HoldForMePromptsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val turkish = Locale.forLanguageTag("tr-TR")

    @Test
    fun `prompt of Phone's cache files`() {
        assertEquals("greeting_to_agent", HoldForMePrompts.promptOf("hfm-audio-greeting_to_agent-EN"))
        assertEquals("probing_prompt", HoldForMePrompts.promptOf("hfm-audio-probing_prompt-EN-US"))
        assertEquals("delayed_user_message", HoldForMePrompts.promptOf("hfm-audio-delayed_user_message-JA"))
        assertEquals("recording_refusal_ack", HoldForMePrompts.promptOf("hfm-audio-recording_refusal_ack-EN"))
    }

    @Test
    fun `other files have no prompt`() {
        assertNull(HoldForMePrompts.promptOf("hfm-audio-unknown_prompt-EN"))
        assertNull(HoldForMePrompts.promptOf("hfm-audio-greeting_to_agent"))
        assertNull(HoldForMePrompts.promptOf("greeting_to_agent.wav"))
    }

    @Test
    fun `marker round trip`() {
        val marker = HoldForMePrompts.Marker("tr-TR", "abc123", "tr-TR-language")
        assertEquals(marker, HoldForMePrompts.Marker.read(marker.write()))
        assertNull(HoldForMePrompts.Marker.read("locale=tr-TR\n"))
    }

    private fun pack(filesDir: File, tag: String, markerLocale: String = tag, prompts: List<String> = HoldForMePrompts.PROMPTS): File {
        val directory = HoldForMePrompts.packDirectory(filesDir, Locale.forLanguageTag(tag)).apply { mkdirs() }
        prompts.forEach { HoldForMePrompts.recording(directory, it).writeBytes(byteArrayOf(1)) }
        File(directory, HoldForMePrompts.MARKER).writeText(HoldForMePrompts.Marker(markerLocale, "hash", "voice").write())
        return directory
    }

    @Test
    fun `ready pack in the phone's language`() {
        val filesDir = folder.newFolder()
        val directory = pack(filesDir, "tr-TR")
        assertEquals(directory, HoldForMePrompts.findReady(filesDir, turkish))
        // The pack's own region is used when the phone's region differs.
        assertEquals(directory, HoldForMePrompts.findReady(filesDir, Locale.forLanguageTag("tr-DE")))
    }

    @Test
    fun `no pack for another language`() {
        val filesDir = folder.newFolder()
        pack(filesDir, "tr-TR")
        assertNull(HoldForMePrompts.findReady(filesDir, Locale.US))
        assertNull(HoldForMePrompts.findReady(folder.newFolder(), turkish))
    }

    @Test
    fun `incomplete or unmarked packs are not used`() {
        val missingPrompt = folder.newFolder()
        pack(missingPrompt, "tr-TR", prompts = HoldForMePrompts.PROMPTS.drop(1))
        assertNull(HoldForMePrompts.findReady(missingPrompt, turkish))

        val wrongMarker = folder.newFolder()
        pack(wrongMarker, "tr-TR", markerLocale = "en-US")
        assertNull(HoldForMePrompts.findReady(wrongMarker, turkish))

        val staging = folder.newFolder()
        File(HoldForMePrompts.packRoot(staging), ".staging").mkdirs()
        assertNull(HoldForMePrompts.findReady(staging, turkish))
    }

}
