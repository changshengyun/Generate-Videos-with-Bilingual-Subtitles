package com.example.lyriccaptioner.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class Pcm16ToMono16kProcessorTest {
    @Test
    fun convertsChunkedStereoEightKhzToMonoSixteenKhz() {
        val processor = Pcm16ToMono16kProcessor(
            channelCount = 2,
            inputSampleRate = 8_000,
        )

        val output =
            processor.process(shortArrayOf(0, 0, 1_000)) +
                processor.process(shortArrayOf(1_000)) +
                processor.finish()

        assertArrayEquals(shortArrayOf(0, 500, 1_000, 1_000), output)
    }
}
