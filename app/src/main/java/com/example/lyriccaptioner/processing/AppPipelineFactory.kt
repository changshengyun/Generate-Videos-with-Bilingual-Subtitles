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
        return routeAsr(status) {
            createLocalAsr(
                context = context,
                whisperModelPath = store.modelFile.absolutePath,
                runtimeStatus = status,
            )
        }
    }

    fun createTranslationDefault(context: Context): LocalTranslator =
        OnnxLocalTranslator(LocalTranslationModelStore(context))

    internal fun routeAsr(
        status: WhisperRuntimeStatus,
        localFactory: () -> AsrModule,
    ): AsrModule = when (status.mode) {
        SpeechMode.LOCAL -> localFactory()
        SpeechMode.UNAVAILABLE -> UnavailableAsrModule(status)
    }

    fun createLocalAsr(
        context: Context,
        whisperModelPath: String,
        runtimeStatus: WhisperRuntimeStatus = WhisperRuntimeStatusResolver.resolve(true, true),
    ): AsrModule {
        return WhisperAsrModule(
            runtimeStatus = runtimeStatus,
            audioExtractor = AndroidAudioExtractor(context),
            speechRecognizer = WhisperLocalSpeechRecognizer(
                modelPath = whisperModelPath,
                sessionRuntime = WhisperProcessSession.get(context),
            ),
        )
    }
}
