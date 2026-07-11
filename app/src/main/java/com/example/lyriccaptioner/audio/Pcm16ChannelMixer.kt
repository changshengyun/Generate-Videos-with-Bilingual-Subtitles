package com.example.lyriccaptioner.audio

/**
 * Downmixes interleaved signed 16-bit PCM to mono while preserving incomplete
 * channel frames between calls.
 */
class Pcm16ChannelMixer(
    private val channelCount: Int,
) {
    private val pendingFrame = ShortArray(channelCount.coerceAtLeast(1))
    private var pendingSampleCount = 0

    init {
        require(channelCount > 0) { "channelCount must be positive" }
    }

    fun process(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)

        val output = ShortArray((pendingSampleCount + samples.size) / channelCount)
        var inputIndex = 0
        var outputIndex = 0

        while (inputIndex < samples.size) {
            pendingFrame[pendingSampleCount++] = samples[inputIndex++]
            if (pendingSampleCount == channelCount) {
                var sum = 0L
                for (sample in pendingFrame) {
                    sum += sample.toLong()
                }
                output[outputIndex++] = (sum / channelCount).toShort()
                pendingSampleCount = 0
            }
        }

        return output
    }

    /**
     * Completes the stream. A partial interleaved frame is malformed PCM.
     */
    fun finish() {
        check(pendingSampleCount == 0) {
            "PCM stream ended with $pendingSampleCount of $channelCount channel samples"
        }
    }
}
