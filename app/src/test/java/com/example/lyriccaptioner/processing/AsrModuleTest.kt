package com.example.lyriccaptioner.processing

import android.net.TestUri
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.SpeechMode
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModuleTest {
    @Test
    fun runtimeResolverSeparatesLocalAndUnavailable() {
        assertEquals(SpeechMode.LOCAL, WhisperRuntimeStatusResolver.resolve(true, true).mode)
        assertEquals(SpeechMode.UNAVAILABLE, WhisperRuntimeStatusResolver.resolve(false, true).mode)
        assertEquals(SpeechMode.UNAVAILABLE, WhisperRuntimeStatusResolver.resolve(true, false).mode)
        assertEquals(
            SpeechMode.UNAVAILABLE,
            WhisperRuntimeStatusResolver.resolve(false, false).mode,
        )
    }

    @Test
    fun segmentConversionPreservesTextAndValidOrderedTimestamps() {
        val captions = WhisperSegmentConverter.toCaptions(
            listOf(
                WhisperSegment(0, 1_200, " hello ", 0.7f),
                WhisperSegment(1_200, 2_500, "world", 0.8f),
            ),
        )

        assertEquals("hello", captions[0].english)
        assertEquals(1_200L, captions[0].endMs)
        assertEquals("whisper-1-1200", captions[1].id)
    }

    @Test
    fun segmentConversionRejectsInvalidOrderingAndEmptyOutput() {
        assertThrows(AsrOutputFormatException::class.java) {
            WhisperSegmentConverter.toCaptions(listOf(WhisperSegment(1_000, 900, "bad", 0.5f)))
        }
        assertThrows(AsrOutputFormatException::class.java) {
            WhisperSegmentConverter.toCaptions(listOf(WhisperSegment(0, 100, "   ", 0.5f)))
        }
    }

    @Test
    fun recognizerRejectsMissingJniAndPropagatesJniFailure() = runBlocking {
        val audio = ExtractedAudio(TestUri("file:///tmp/audio.wav"), 16_000, 1, "/tmp/audio.wav")
        val unavailableRuntime = WhisperSessionRuntime(FakeNativeClient(false))
        val unavailable = WhisperLocalSpeechRecognizer("model", unavailableRuntime)
        assertThrows(WhisperJniUnavailableException::class.java) {
            runBlocking { unavailable.recognize(audio) }
        }

        val model = File.createTempFile("whisper-model", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val failingRuntime = WhisperSessionRuntime(
            FakeNativeClient(true, failure = IllegalStateException("native boom")),
        )
        try {
            val failing = WhisperLocalSpeechRecognizer(model.absolutePath, failingRuntime)
            val thrown = assertThrows(IllegalStateException::class.java) {
                runBlocking { failing.recognize(audio) }
            }
            assertEquals("native boom", thrown.message)
        } finally {
            unavailableRuntime.close()
            failingRuntime.close()
            model.delete()
        }
    }

    @Test
    fun moduleCleansExtractedAudioAfterSuccessAndFailure() = runBlocking {
        val successFile = File.createTempFile("asr-success", ".wav")
        val failureFile = File.createTempFile("asr-failure", ".wav")
        try {
            val success = testModule(successFile, FakeRecognizer())
            val captions = success.recognize(TestUri("content://video/success"))
            assertEquals("", captions.single().chinese)
            assertFalse(successFile.exists())

            val failure = testModule(failureFile, FakeRecognizer(IllegalStateException("recognition failed")))
            assertThrows(IllegalStateException::class.java) {
                runBlocking { failure.recognize(TestUri("content://video/failure")) }
            }
            assertFalse(failureFile.exists())
        } finally {
            successFile.delete()
            failureFile.delete()
        }
    }

    @Test
    fun moduleCleansExtractedAudioWhenCancelled() = runBlocking {
        val file = File.createTempFile("asr-cancel", ".wav")
        val module = testModule(file, BlockingRecognizer())
        val job = launch { module.recognize(TestUri("content://video/cancel")) }
        delay(50)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(file.exists())
    }

    @Test
    fun unavailableModuleNeverRunsRecognition() = runBlocking<Unit> {
        val status = WhisperRuntimeStatusResolver.resolve(false, false)
        val module = UnavailableAsrModule(status)

        assertThrows(AsrUnavailableException::class.java) {
            runBlocking { module.recognize(TestUri("content://video/unavailable")) }
        }
    }

    @Test
    fun productRouteUsesLocalOnlyAndFailsExplicitlyOtherwise() {
        val local = object : AsrModule {
            override val runtimeStatus = WhisperRuntimeStatusResolver.resolve(true, true)
            override suspend fun recognize(
                videoUri: android.net.Uri,
                onStatus: (String) -> Unit,
            ): List<CaptionCue> = emptyList()
        }

        assertEquals(
            local,
            AppPipelineFactory.routeAsr(local.runtimeStatus) { local },
        )
        listOf(
            WhisperRuntimeStatusResolver.resolve(false, true),
            WhisperRuntimeStatusResolver.resolve(false, false),
        ).forEach { status ->
            assertTrue(AppPipelineFactory.routeAsr(status) { local } is UnavailableAsrModule)
        }
    }

    private fun testModule(file: File, recognizer: LocalSpeechRecognizer): AsrModule {
        val runtime = WhisperRuntimeStatusResolver.resolve(true, true)
        return WhisperAsrModule(
            runtimeStatus = runtime,
            audioExtractor = FakeExtractor(file),
            speechRecognizer = recognizer,
        )
    }

    private class FakeExtractor(private val file: File) : AudioExtractor {
        override suspend fun extract(videoUri: android.net.Uri): ExtractedAudio {
            return ExtractedAudio(TestUri("file://${file.absolutePath}"), 16_000, 1, file.absolutePath, true)
        }
    }

    private class FakeRecognizer(private val failure: Throwable? = null) : LocalSpeechRecognizer {
        override suspend fun recognize(audio: ExtractedAudio): List<CaptionCue> {
            failure?.let { throw it }
            return listOf(CaptionCue("cue", 0, 100, "hello", "should not be translated", 0.9f))
        }
    }

    private class BlockingRecognizer : LocalSpeechRecognizer {
        override suspend fun recognize(audio: ExtractedAudio): List<CaptionCue> {
            while (true) {
                currentCoroutineContext().ensureActive()
                delay(10)
            }
        }
    }

    private class FakeNativeClient(
        override val isAvailable: Boolean,
        private val failure: Throwable? = null,
    ) : WhisperSessionNativeClient {
        override fun createContext(modelPath: String): Long = 1L

        override fun transcribe(
            contextHandle: Long,
            audioPath: String,
            sampleRate: Int,
            channels: Int,
            cancellationToken: WhisperCancellationToken,
        ): List<WhisperSegment> {
            failure?.let { throw it }
            return listOf(WhisperSegment(0, 100, "ok", 0.9f))
        }

        override fun requestAbort(contextHandle: Long) = Unit

        override fun freeContext(contextHandle: Long) = Unit
    }
}
