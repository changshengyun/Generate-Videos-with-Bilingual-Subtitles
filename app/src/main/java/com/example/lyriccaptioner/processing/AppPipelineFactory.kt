package com.example.lyriccaptioner.processing

import android.content.Context
import com.example.lyriccaptioner.model.SpeechMode

object AppPipelineFactory {
    fun createDefault(context: Context): CaptionPipeline {
        return CaptionPipeline(exportEngine = FfmpegKitSubtitleExporter(context))
    }

    fun createAsrDefault(context: Context): AsrModule {
        val store = WhisperModelStore(context)
        val status = store.status()
        return when (status.mode) {
            SpeechMode.LOCAL -> createLocalAsr(
                context = context,
                whisperModelPath = store.modelFile.absolutePath,
                runtimeStatus = status,
            )
            SpeechMode.DEMO -> WhisperAsrModule(
                runtimeStatus = status,
                audioExtractor = DemoAudioExtractor(),
                speechRecognizer = DemoSpeechRecognizer(),
            )
            SpeechMode.UNAVAILABLE -> UnavailableAsrModule(status)
        }
    }

    fun createLocalAsr(
        context: Context,
        whisperModelPath: String,
        runtimeStatus: WhisperRuntimeStatus = WhisperRuntimeStatusResolver.resolve(true, true),
    ): AsrModule {
        return WhisperAsrModule(
            runtimeStatus = runtimeStatus,
            audioExtractor = AndroidAudioExtractor(context),
            speechRecognizer = WhisperLocalSpeechRecognizer(modelPath = whisperModelPath),
        )
    }
}
