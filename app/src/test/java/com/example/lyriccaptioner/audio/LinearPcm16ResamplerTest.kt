package com.example.lyriccaptioner.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LinearPcm16ResamplerTest {
    @Test
    fun keepsIdentityRateAndFlushesLastSample() {
        val resampler = LinearPcm16Resampler(16_000)

        val streamed = resampler.process(shortArrayOf(1, 2, 3))
        val tail = resampler.finish()

        assertArrayEquals(shortArrayOf(1, 2), streamed)
        assertArrayEquals(shortArrayOf(3), tail)
    }

    @Test
    fun linearlyUpsamplesAcrossChunkBoundary() {
        val resampler = LinearPcm16Resampler(inputSampleRate = 8_000)

        val first = resampler.process(shortArrayOf(0))
        val second = resampler.process(shortArrayOf(1_000))
        val tail = resampler.finish()

        assertArrayEquals(shortArrayOf(), first)
        assertArrayEquals(shortArrayOf(0, 500), second)
        assertArrayEquals(shortArrayOf(1_000, 1_000), tail)
    }

    @Test
    fun downsamplesToExpectedDuration() {
        val input = ShortArray(48_000) { (it % 1_000).toShort() }
        val resampler = LinearPcm16Resampler(inputSampleRate = 48_000)

        val output = resampler.process(input) + resampler.finish()

        assertEquals(16_000, output.size)
        assertEquals(input[0], output[0])
        assertEquals(input[3], output[1])
    }

    @Test
    fun chunkingDoesNotChangeResult() {
        val input = ShortArray(101) { (it * 123 - 5_000).toShort() }
        val whole = resample(input, listOf(input.size))
        val chunked = resample(input, listOf(1, 2, 17, 3, 29, 49))

        assertArrayEquals(whole, chunked)
    }

    @Test
    fun emptyInputProducesEmptyOutputAndCannotBeReusedAfterFinish() {
        val resampler = LinearPcm16Resampler(inputSampleRate = 44_100)

        assertArrayEquals(shortArrayOf(), resampler.finish())
        assertThrows(IllegalStateException::class.java) {
            resampler.process(shortArrayOf(1))
        }
    }

    private fun resample(input: ShortArray, chunkSizes: List<Int>): ShortArray {
        val resampler = LinearPcm16Resampler(inputSampleRate = 44_100)
        val output = ArrayList<Short>()
        var offset = 0
        for (size in chunkSizes) {
            resampler.process(input.copyOfRange(offset, offset + size)).forEach(output::add)
            offset += size
        }
        resampler.finish().forEach(output::add)
        return output.toShortArray()
    }
}
