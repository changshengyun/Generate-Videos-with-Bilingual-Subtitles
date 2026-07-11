package com.example.lyriccaptioner.processing

import android.content.Context

enum class PipelineMode {
    Demo,
    Local,
}

object AppPipelineFactory {
    fun createDefault(context: Context): CaptionPipeline {
        val modelFile = WhisperModelStore(context).modelFile
        return if (WhisperModelStore(context).status().localRecognitionReady) {
            createLocal(context, modelFile.absolutePath)
        } else {
            createDemo(context)
        }
    }

    fun createDemo(context: Context): CaptionPipeline {
        return CaptionPipeline(
            audioExtractor = DemoAudioExtractor(),
            speechRecognizer = DemoSpeechRecognizer(),
            corrector = DemoCaptionCorrector(),
            translator = DemoTranslator(),
            exportEngine = Media3SubtitleExporter(context),
        )
    }

    fun createLocal(
        context: Context,
        whisperModelPath: String,
    ): CaptionPipeline {
        return createLocal(
            audioExtractor = AndroidAudioExtractor(context),
            whisperModelPath = whisperModelPath,
            exportEngine = Media3SubtitleExporter(context),
        )
    }

    fun createLocal(
        audioExtractor: AudioExtractor,
        whisperModelPath: String,
        exportEngine: ExportEngine,
    ): CaptionPipeline {
        return CaptionPipeline(
            audioExtractor = audioExtractor,
            speechRecognizer = WhisperLocalSpeechRecognizer(modelPath = whisperModelPath),
            corrector = DemoCaptionCorrector(),
            translator = MlKitLocalTranslator(),
            exportEngine = exportEngine,
        )
    }
}
