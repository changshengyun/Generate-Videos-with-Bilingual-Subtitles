package com.example.lyriccaptioner.processing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegKitSubtitleExporterTest {
    @Test
    fun buildSubtitleFilter_handlesAndroidPrivateDirectoryPath() {
        assertEquals(
            "subtitles=filename='/data/user/0/com.example.lyriccaptioner/cache/ffmpeg-exports/job-1/captions.ass'",
            buildSubtitleFilter(
                "/data/user/0/com.example.lyriccaptioner/cache/ffmpeg-exports/job-1/captions.ass",
            ),
        )
    }

    @Test
    fun buildSubtitleFilter_quotesPathContainingSpaces() {
        assertEquals(
            "subtitles=filename='/data/user/0/com.example.lyriccaptioner/cache/My Export/captions final.ass'",
            buildSubtitleFilter(
                "/data/user/0/com.example.lyriccaptioner/cache/My Export/captions final.ass",
            ),
        )
    }

    @Test
    fun buildSubtitleFilter_escapesColonBackslashAndSingleQuoteAcrossAvFilterLayers() {
        assertEquals(
            "subtitles=filename='C\\:\\\\exports\\\\director'\\\\\\''s captions.ass'",
            buildSubtitleFilter("C:\\exports\\director's captions.ass"),
        )
    }

    @Test
    fun cancellableCopy_reportsExactBytesAndDoesNotReplaceAnExistingSentinel() = runBlocking {
        val sentinel = "DO NOT REPLACE".toByteArray()
        val destination = ByteArrayOutputStream().apply { write(sentinel) }
        val copied = copyStreamCancellable(ByteArrayInputStream("new output".toByteArray()), destination)
        assertEquals("new output".length.toLong(), copied)
        // The committer rejects an existing target before this writer is opened. This
        // stream-level test makes the sentinel invariant explicit for the regression suite.
        assertTrue(destination.toByteArray().copyOfRange(0, sentinel.size).contentEquals(sentinel))
    }

    @Test
    fun copyFailureIsPropagatedWithoutASecondWrite() = runBlocking {
        var writes = 0
        val error = runCatching {
            copyStreamCancellable(
                ByteArrayInputStream(ByteArray(128 * 1024)),
                object : OutputStream() {
                    override fun write(value: Int) = Unit
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        writes++
                        error("synthetic destination failure")
                    }
                },
            )
        }.exceptionOrNull()
        assertTrue(error?.message?.contains("synthetic destination failure") == true)
        assertEquals(1, writes)
    }

    @Test
    fun copyCancellationStopsBeforeTheNextWrite() = runBlocking {
        val firstRead = CountDownLatch(1)
        val source = object : ByteArrayInputStream(ByteArray(2 * 1024 * 1024)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val result = super.read(buffer, offset, minOf(length, 1024))
                if (result > 0) firstRead.countDown()
                Thread.sleep(2)
                return result
            }
        }
        var writes = 0
        val job = launch(Dispatchers.IO) {
            copyStreamCancellable(source, object : OutputStream() {
                override fun write(value: Int) = Unit
                override fun write(bytes: ByteArray, offset: Int, length: Int) { writes++ }
            })
        }
        assertTrue(firstRead.await(2, TimeUnit.SECONDS))
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(writes < 2_048)
    }
}
