package com.example.lyriccaptioner.audio

/**
 * Convenience pipeline for interleaved multi-channel PCM16 to 16 kHz mono.
 */
class Pcm16ToMono16kProcessor(
    channelCount: Int,
    inputSampleRate: Int,
) {
    private val mixer = Pcm16ChannelMixer(channelCount)
    private val resampler = LinearPcm16Resampler(inputSampleRate)
    private var finished = false

    fun process(interleavedSamples: ShortArray): ShortArray {
        check(!finished) { "Processor is already finished" }
        return resampler.process(mixer.process(interleavedSamples))
    }

    fun finish(): ShortArray {
        check(!finished) { "Processor is already finished" }
        finished = true
        mixer.finish()
        return resampler.finish()
    }
}
