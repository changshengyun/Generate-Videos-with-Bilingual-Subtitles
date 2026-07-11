package com.example.lyriccaptioner.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Pcm16ChannelMixerTest {
    @Test
    fun downmixesStereoAndPreservesChunkBoundary() {
        val mixer = Pcm16ChannelMixer(channelCount = 2)

        assertArrayEquals(shortArrayOf(2_000), mixer.process(shortArrayOf(1_000, 3_000, 8_000)))
        assertArrayEquals(shortArrayOf(3_000), mixer.process(shortArrayOf(-2_000)))
        mixer.finish()
    }

    @Test
    fun averagesExtremeValuesWithoutOverflow() {
        val mixer = Pcm16ChannelMixer(channelCount = 2)

        assertArrayEquals(
            shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, 0),
            mixer.process(
                shortArrayOf(
                    Short.MAX_VALUE,
                    Short.MAX_VALUE,
                    Short.MIN_VALUE,
                    Short.MIN_VALUE,
                    Short.MAX_VALUE,
                    Short.MIN_VALUE,
                ),
            ),
        )
    }

    @Test
    fun rejectsIncompleteFinalFrame() {
        val mixer = Pcm16ChannelMixer(channelCount = 3)
        mixer.process(shortArrayOf(1, 2))

        assertThrows(IllegalStateException::class.java) { mixer.finish() }
    }
}
