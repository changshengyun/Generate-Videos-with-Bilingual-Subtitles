package com.example.lyriccaptioner.processing

import android.net.Uri
import com.example.lyriccaptioner.model.CaptionCue
import kotlinx.coroutines.delay

class DemoAudioExtractor : AudioExtractor {
    override suspend fun extract(videoUri: Uri): ExtractedAudio {
        delay(450)
        return ExtractedAudio(uri = videoUri, sampleRate = 16_000, channels = 1)
    }
}

class DemoSpeechRecognizer : LocalSpeechRecognizer {
    override suspend fun recognize(audio: ExtractedAudio): List<CaptionCue> {
        delay(900)
        return listOf(
            CaptionCue(
                id = "cue-1",
                startMs = 0,
                endMs = 3_200,
                english = "I found a love for me",
                chinese = "",
                confidence = 0.91f,
            ),
            CaptionCue(
                id = "cue-2",
                startMs = 3_200,
                endMs = 6_800,
                english = "Darling just dive right inn",
                chinese = "",
                confidence = 0.64f,
                correctionCandidates = listOf("Darling just dive right in"),
            ),
            CaptionCue(
                id = "cue-3",
                startMs = 6_800,
                endMs = 10_400,
                english = "Follow my lead",
                chinese = "",
                confidence = 0.88f,
            ),
        )
    }
}

class DemoCaptionCorrector : CaptionCorrector {
    override suspend fun correct(captions: List<CaptionCue>): List<CaptionCue> {
        delay(350)
        return captions
    }
}

class DemoTranslator : LocalTranslator {
    override suspend fun translateEnglishToChinese(text: String): String {
        delay(120)
        return when (text.lowercase()) {
            "i found a love for me" -> "\u6211\u627e\u5230\u4e86\u5c5e\u4e8e\u6211\u7684\u7231"
            "darling just dive right in" -> "\u4eb2\u7231\u7684\uff0c\u5c31\u52c7\u6562\u6295\u5165\u5427"
            "follow my lead" -> "\u8ddf\u968f\u6211\u7684\u8282\u594f"
            else -> "\u5f85\u7ffb\u8bd1"
        }
    }
}
