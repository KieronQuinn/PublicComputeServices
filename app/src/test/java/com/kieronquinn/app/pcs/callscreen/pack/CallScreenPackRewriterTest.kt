package com.kieronquinn.app.pcs.callscreen.pack

import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.Field
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.LENGTH_DELIMITED
import com.kieronquinn.app.pcs.callscreen.pack.ProtoWire.VARINT
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

class CallScreenPackRewriterTest {

    private val translation = CallScreenTranslation.parse(StringReader("""
        {
          "version": 1,
          "locale": "de-DE",
          "tts": {"rate": 1.1},
          "prompts": {"disclosure/o1": "Hallo, wer ist da?"},
          "texts": {"Please hold, I'm connecting you.": "Bitte warten, ich verbinde Sie."},
          "chips": {"Call back later": "Später zurückrufen"},
          "voiceTriggers": {"CallMeBack": ["ruf mich zurück"]}
        }
    """.trimIndent()))

    private val rewriter = CallScreenPackRewriter(translation)

    private fun text(number: Int, value: String) = Field(number, LENGTH_DELIMITED, value.toByteArray())
    private fun message(number: Int, vararg fields: Field) =
        Field(number, LENGTH_DELIMITED, ProtoWire.encode(fields.toList()))

    @Test
    fun `wire format round trips unknown fields byte for byte`() {
        val original = ProtoWire.encode(listOf(
            Field(1, VARINT, 300L),
            Field(2, ProtoWire.FIXED64, ByteArray(8) { it.toByte() }),
            text(3, "text"),
            Field(4, ProtoWire.FIXED32, ByteArray(4) { 7 })
        ))
        assertArrayEquals(original, ProtoWire.encode(ProtoWire.parse(original)))
    }

    @Test
    fun `intent prompt, locale and chips are translated`() {
        val intent = message(3,
            text(1, "Disclosure"),
            message(11,
                message(2, text(1, "disclosure/o1"), text(2, "Hi, who is calling?")),
                text(15, "en-US")
            ),
            message(12, text(1, "en-GB")),
            text(26, "Call back later")
        )
        val result = rewriter.rewrite("agenticcallscreen_robot_intent_specs.pb", ProtoWire.encode(listOf(intent)))
        assertTrue(result.untranslated.isEmpty())
        val rewritten = ProtoWire.parse(ProtoWire.parse(result.bytes).single().bytes)
        val say = ProtoWire.parse(rewritten.first { it.number == 11 }.bytes)
        val prompt = ProtoWire.parse(say.first { it.number == 2 }.bytes)
        assertEquals("Hallo, wer ist da?", ProtoWire.text(prompt.first { it.number == 2 }))
        assertEquals("de-DE", ProtoWire.text(say.first { it.number == 15 }))
        val locales = ProtoWire.parse(rewritten.first { it.number == 12 }.bytes)
        assertEquals("de-DE", ProtoWire.text(locales.single()))
        assertEquals("Später zurückrufen", ProtoWire.text(rewritten.first { it.number == 26 }))
    }

    @Test
    fun `prompt without audio uses text translation and reports missing ones`() {
        val intent = message(3,
            text(1, "Connect"),
            message(11, message(2, text(2, "Please hold; I'm  connecting you."))),
            message(11, message(2, text(2, "Unknown sentence"))),
            message(11, message(2, text(2, "5")))
        )
        val result = rewriter.rewrite("bam_robot_intent_specs.pb", ProtoWire.encode(listOf(intent)))
        assertEquals(listOf("Connect: Unknown sentence"), result.untranslated)
        assertTrue(String(result.bytes).contains("Bitte warten, ich verbinde Sie."))
    }

    @Test
    fun `voice triggers are appended once`() {
        val config = message(101, message(14, message(1,
            text(1, "CallMeBack"),
            message(2, text(1, "call me back"), text(1, "ruf mich zurück"))
        )))
        val result = rewriter.rewrite("agenticcallscreen_bot_config.pb", ProtoWire.encode(listOf(config)))
        val phrases = ProtoWire.parse(result.bytes).single().bytes
            .let(ProtoWire::parse).single().bytes
            .let(ProtoWire::parse).single().bytes
            .let(ProtoWire::parse).first { it.number == 2 }.bytes
            .let(ProtoWire::parse).mapNotNull(ProtoWire::text)
        assertEquals(listOf("call me back", "ruf mich zurück"), phrases)
    }

    /**
     *  Compares against the pack built by the desktop builder this port replaces (Root Flags
     *  `packs/dobby.py`), which is in use on devices. Needs local files that are not part of the
     *  repository: CALL_SCREEN_SOURCE_ZIP (Google's en-US pack), CALL_SCREEN_REFERENCE_ZIP (the
     *  desktop build from it) and CALL_SCREEN_TRANSLATION (the matching translation JSON).
     */
    @Test
    fun `matches the desktop builder on the real pack`() {
        val source = System.getenv("CALL_SCREEN_SOURCE_ZIP")?.let(::File)
        val reference = System.getenv("CALL_SCREEN_REFERENCE_ZIP")?.let(::File)
        val translationFile = System.getenv("CALL_SCREEN_TRANSLATION")?.let(::File)
        assumeTrue(source?.isFile == true && reference?.isFile == true && translationFile?.isFile == true)
        val real = CallScreenPackRewriter(translationFile!!.reader().use(CallScreenTranslation::parse))
        ZipFile(source!!).use { src ->
            ZipFile(reference!!).use { ref ->
                val names = src.entries().toList().map { it.name }.filter(real::isRewritten)
                assertTrue(names.size >= 4)
                for (name in names) {
                    val result = real.rewrite(name, src.getInputStream(src.getEntry(name)).readBytes())
                    assertEquals("$name untranslated", emptyList<String>(), result.untranslated)
                    val expected = ref.getInputStream(ref.getEntry(name)).readBytes()
                    assertArrayEquals(name, expected, result.bytes)
                }
            }
        }
    }

}
