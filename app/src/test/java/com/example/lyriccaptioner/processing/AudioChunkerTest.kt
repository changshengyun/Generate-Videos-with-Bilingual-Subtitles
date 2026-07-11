package com.example.lyriccaptioner.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioChunkerTest {
    @Test
    fun createsOverlappingChunksUntilDurationEnd() {
        val chunks = AudioChunker(chunkDurationMs = 30_000L, overlapMs = 1_000L)
            .planChunks(durationMs = 65_000L)

        assertEquals(
            listOf(
                AudioChunk(0L, 30_000L),
                AudioChunk(29_000L, 59_000L),
                AudioChunk(58_000L, 65_000L),
            ),
            chunks,
        )
    }
}
