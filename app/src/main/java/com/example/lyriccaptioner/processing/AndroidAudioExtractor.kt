package com.example.lyriccaptioner.processing

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.lyriccaptioner.audio.Pcm16ToMono16kProcessor
import com.example.lyriccaptioner.audio.Pcm16WavWriter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidAudioExtractor(
    context: Context,
) : AudioExtractor {
    private val appContext = context.applicationContext

    override suspend fun extract(videoUri: Uri): ExtractedAudio = withContext(Dispatchers.IO) {
        val outputFile = createOutputFile()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var codecStarted = false
        var writer: Pcm16WavWriter? = null

        try {
            extractor.setDataSource(appContext, videoUri, null)
            val trackIndex = findAudioTrack(extractor)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = requireNotNull(trackFormat.getString(MediaFormat.KEY_MIME)) {
                "Audio track has no MIME type."
            }
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(trackFormat, null, null, 0)
            codec.start()
            codecStarted = true

            writer = Pcm16WavWriter(outputFile, TARGET_SAMPLE_RATE, channelCount = 1)
            decodeToWav(extractor, codec, writer)
            writer.close()
            writer = null

            ExtractedAudio(
                uri = Uri.fromFile(outputFile),
                sampleRate = TARGET_SAMPLE_RATE,
                channels = 1,
                filePath = outputFile.absolutePath,
                deleteFileAfterUse = true,
            )
        } catch (error: Throwable) {
            runCatching { writer?.close() }
            outputFile.delete()
            throw error
        } finally {
            if (codecStarted) {
                runCatching { codec?.stop() }
            }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private suspend fun decodeToWav(
        extractor: MediaExtractor,
        codec: MediaCodec,
        writer: Pcm16WavWriter,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var processor: Pcm16ToMono16kProcessor? = null
        var activeFormat: DecodedPcmFormat? = null
        var idleOutputPolls = 0

        while (!outputEnded) {
            coroutineContext.ensureActive()

            if (!inputEnded) {
                val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime.coerceAtLeast(0L),
                            extractor.sampleFlags,
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val updatedFormat = DecodedPcmFormat.from(codec.outputFormat)
                    check(activeFormat == null || activeFormat == updatedFormat) {
                        "Audio format changed during decoding: $activeFormat to $updatedFormat"
                    }
                    if (activeFormat == null) {
                        activeFormat = updatedFormat
                        processor = Pcm16ToMono16kProcessor(
                            channelCount = updatedFormat.channelCount,
                            inputSampleRate = updatedFormat.sampleRate,
                        )
                    }
                    idleOutputPolls = 0
                }

                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (inputEnded && ++idleOutputPolls > MAX_IDLE_OUTPUT_POLLS) {
                        error("Audio decoder did not finish after end of input.")
                    }
                }

                else -> if (outputIndex >= 0) {
                    idleOutputPolls = 0
                    if (bufferInfo.size > 0) {
                        val format = activeFormat ?: DecodedPcmFormat.from(codec.outputFormat)
                            .also {
                                activeFormat = it
                                processor = Pcm16ToMono16kProcessor(
                                    channelCount = it.channelCount,
                                    inputSampleRate = it.sampleRate,
                                )
                            }
                        val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                        val samples = outputBuffer.readPcm16(bufferInfo, format.pcmEncoding)
                        writer.write(requireNotNull(processor).process(samples))
                    }
                    outputEnded =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }

        val finalProcessor = requireNotNull(processor) {
            "Audio decoder produced no PCM output."
        }
        writer.write(finalProcessor.finish())
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        return (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: error("The selected video contains no supported audio track.")
    }

    private fun createOutputFile(): File {
        val directory = File(appContext.cacheDir, "asr-audio")
        check(directory.exists() || directory.mkdirs()) {
            "Could not create audio extraction directory."
        }
        return File(directory, "audio-${System.currentTimeMillis()}.wav")
    }

    private data class DecodedPcmFormat(
        val sampleRate: Int,
        val channelCount: Int,
        val pcmEncoding: Int,
    ) {
        companion object {
            fun from(format: MediaFormat): DecodedPcmFormat {
                return DecodedPcmFormat(
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    },
                )
            }
        }
    }

    private fun ByteBuffer.readPcm16(
        info: MediaCodec.BufferInfo,
        pcmEncoding: Int,
    ): ShortArray {
        val source = duplicate()
            .order(ByteOrder.nativeOrder())
            .apply {
                position(info.offset)
                limit(info.offset + info.size)
            }

        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> {
                check(source.remaining() % Short.SIZE_BYTES == 0) {
                    "PCM16 output contains a partial sample."
                }
                ShortArray(source.remaining() / Short.SIZE_BYTES) {
                    source.short
                }
            }

            AudioFormat.ENCODING_PCM_FLOAT -> {
                check(source.remaining() % Float.SIZE_BYTES == 0) {
                    "Float PCM output contains a partial sample."
                }
                ShortArray(source.remaining() / Float.SIZE_BYTES) {
                    val sample = source.float.coerceIn(-1f, 1f)
                    if (sample <= -1f) {
                        Short.MIN_VALUE
                    } else {
                        (sample * Short.MAX_VALUE).roundToInt().toShort()
                    }
                }
            }

            else -> error("Unsupported decoded PCM encoding: $pcmEncoding")
        }
    }

    private companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_IDLE_OUTPUT_POLLS = 1_000
    }
}
