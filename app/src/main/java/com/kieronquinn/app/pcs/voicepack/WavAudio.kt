package com.kieronquinn.app.pcs.voicepack

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 *  16-bit PCM WAV reading, resampling and writing. Voice packs hold mono 16-bit PCM recordings at
 *  the pack's rate ([VoicePack.sampleRate]); text-to-speech engines write other rates (Google TTS
 *  writes 24 kHz), so recordings are converted to the pack's rate.
 */
internal object WavAudio {

    class InvalidException(message: String) : Exception(message)

    class Pcm(val sampleRate: Int, val samples: ShortArray)

    /** Reads a PCM WAV file; multi-channel audio is mixed down to mono. */
    fun read(bytes: ByteArray): Pcm {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 12 || tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE") {
            throw InvalidException("Not a WAV file")
        }
        var offset = 12
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var format = 0
        while (offset + 8 <= bytes.size) {
            val id = tag(bytes, offset)
            val size = buffer.getInt(offset + 4)
            val body = offset + 8
            // Streaming writers leave the data size unset (0 or -1): the data runs to the end.
            val end = if (id == "data" && (size <= 0 || body + size > bytes.size)) bytes.size
                else body + size
            if (id != "data" && (size < 0 || end > bytes.size)) throw InvalidException("Truncated chunk $id")
            when (id) {
                "fmt " -> {
                    format = buffer.getShort(body).toInt() and 0xFFFF
                    channels = buffer.getShort(body + 2).toInt()
                    sampleRate = buffer.getInt(body + 4)
                    bits = buffer.getShort(body + 14).toInt()
                }
                "data" -> {
                    if (format != 1 || bits != 16 || channels < 1 || sampleRate <= 0) {
                        throw InvalidException("Unsupported WAV: format $format, $bits bit, $channels channels")
                    }
                    val frames = (end - body) / (2 * channels)
                    val samples = ShortArray(frames)
                    for (frame in 0 until frames) {
                        var sum = 0
                        for (channel in 0 until channels) {
                            sum += buffer.getShort(body + (frame * channels + channel) * 2)
                        }
                        samples[frame] = (sum / channels).toShort()
                    }
                    return Pcm(sampleRate, samples)
                }
            }
            offset = end + (end - body) % 2
        }
        throw InvalidException("No data chunk")
    }

    /** Writes mono 16-bit PCM with a plain 44-byte header and no metadata chunks. */
    fun write(pcm: Pcm): ByteArray {
        val dataSize = pcm.samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(pcm.sampleRate).putInt(pcm.sampleRate * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataSize)
        pcm.samples.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    /**
     *  Band-limited resampling with a Kaiser-windowed sinc filter. When downsampling, the cutoff
     *  moves below the new Nyquist frequency so nothing aliases.
     */
    fun resample(pcm: Pcm, targetRate: Int): Pcm {
        if (pcm.sampleRate == targetRate || pcm.samples.isEmpty()) return Pcm(targetRate, pcm.samples)
        val ratio = targetRate.toDouble() / pcm.sampleRate
        val cutoff = minOf(1.0, ratio) * 0.97
        val halfWidth = ceil(HALF_TAPS / cutoff).toInt()
        val outputLength = floor(pcm.samples.size * ratio).toInt()
        val output = ShortArray(outputLength)
        val besselBeta = bessel0(KAISER_BETA)
        for (n in 0 until outputLength) {
            val position = n / ratio
            val centre = floor(position).toInt()
            var sum = 0.0
            var weight = 0.0
            for (k in centre - halfWidth + 1..centre + halfWidth) {
                if (k < 0 || k >= pcm.samples.size) continue
                val distance = position - k
                val window = kaiser(distance / (halfWidth + 1), besselBeta)
                if (window == 0.0) continue
                val tap = cutoff * sinc(cutoff * distance) * window
                sum += pcm.samples[k] * tap
                weight += tap
            }
            // Normalising by the tap sum keeps the gain flat at the edges of the clip.
            val value = if (abs(weight) > 1e-9) sum / weight else 0.0
            output[n] = value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return Pcm(targetRate, output)
    }

    private const val HALF_TAPS = 16.0
    private const val KAISER_BETA = 8.6

    private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

    private fun kaiser(x: Double, besselBeta: Double): Double {
        if (abs(x) >= 1.0) return 0.0
        return bessel0(KAISER_BETA * sqrt(1 - x * x)) / besselBeta
    }

    private fun bessel0(x: Double): Double {
        var sum = 1.0
        var term = 1.0
        var k = 1
        while (term > 1e-12 * sum) {
            term *= (x / (2 * k)) * (x / (2 * k))
            sum += term
            k++
        }
        return sum
    }

    private fun tag(bytes: ByteArray, offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)

}
