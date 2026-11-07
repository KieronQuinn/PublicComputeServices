package com.kieronquinn.app.pcs.callscreen.pack

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class WavAudioTest {

    private fun sine(rate: Int, frequency: Double, seconds: Double, amplitude: Int = 12000) =
        ShortArray((rate * seconds).toInt()) {
            (amplitude * sin(2 * PI * frequency * it / rate)).roundToInt().toShort()
        }

    private fun zeroCrossings(samples: ShortArray) =
        (1 until samples.size).count { (samples[it - 1] < 0) != (samples[it] < 0) }

    private fun rms(samples: ShortArray, from: Int, to: Int) =
        sqrt((from until to).sumOf { samples[it].toDouble() * samples[it] } / (to - from))

    @Test
    fun `write then read keeps samples`() {
        val pcm = WavAudio.Pcm(24_000, sine(24_000, 440.0, 0.1))
        val bytes = WavAudio.write(pcm)
        assertEquals(44 + pcm.samples.size * 2, bytes.size)
        val read = WavAudio.read(bytes)
        assertEquals(24_000, read.sampleRate)
        assertArrayEquals(pcm.samples, read.samples)
    }

    @Test
    fun `stereo with extra chunks and unset data size is mixed to mono`() {
        val frames = 100
        val data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { data.putShort(1000).putShort(3000) }
        val wav = ByteBuffer.allocate(12 + 26 + 24 + 8 + data.capacity()).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(0).put("WAVE".toByteArray())
        wav.put("LIST".toByteArray()).putInt(18).put(ByteArray(18))
        wav.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(22_050)
            .putInt(22_050 * 4).putShort(4).putShort(16)
        wav.put("data".toByteArray()).putInt(-1).put(data.array())
        val read = WavAudio.read(wav.array())
        assertEquals(22_050, read.sampleRate)
        assertEquals(frames, read.samples.size)
        assertTrue(read.samples.all { it.toInt() == 2000 })
    }

    @Test
    fun `upsampling keeps pitch, length and level`() {
        val input = WavAudio.Pcm(24_000, sine(24_000, 1000.0, 1.0))
        val output = WavAudio.resample(input, WavAudio.CALL_SCREEN_SAMPLE_RATE)
        assertEquals(32_000, output.sampleRate)
        assertEquals(32_000, output.samples.size)
        // 1 kHz has 2000 zero crossings per second at any sample rate.
        assertTrue(abs(zeroCrossings(output.samples) - 2000) <= 2)
        val inputLevel = rms(input.samples, 2400, 21600)
        val outputLevel = rms(output.samples, 3200, 28800)
        assertTrue("level $outputLevel vs $inputLevel", abs(outputLevel - inputLevel) / inputLevel < 0.01)
    }

    @Test
    fun `downsampling removes content above the new Nyquist frequency`() {
        // 20 kHz at 48 kHz cannot be represented at 32 kHz (Nyquist 16 kHz); it must be filtered
        // out rather than fold back as a 12 kHz alias.
        val input = WavAudio.Pcm(48_000, sine(48_000, 20_000.0, 0.5))
        val output = WavAudio.resample(input, 32_000)
        val level = rms(output.samples, 1000, output.samples.size - 1000)
        assertTrue("alias level $level", level < 12000 / sqrt(2.0) * 0.01)
    }

    @Test(expected = WavAudio.InvalidException::class)
    fun `non WAV input is rejected`() {
        WavAudio.read(ByteArray(64))
    }

}
