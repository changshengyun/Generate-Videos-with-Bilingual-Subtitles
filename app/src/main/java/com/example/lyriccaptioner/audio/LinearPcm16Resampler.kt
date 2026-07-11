package com.example.lyriccaptioner.audio

import kotlin.math.roundToLong

/**
 * Stateful linear resampler for mono signed 16-bit PCM.
 *
 * Results do not depend on how input is split across calls. Call [finish] once
 * to emit the final samples whose interpolation clamps to the last input value.
 */
class LinearPcm16Resampler(
    private val inputSampleRate: Int,
    private val outputSampleRate: Int = TARGET_SAMPLE_RATE,
) {
    private val bufferedSamples = ArrayList<Short>()
    private var bufferStartIndex = 0L
    private var totalInputSamples = 0L
    private var nextOutputPositionNumerator = 0L
    private var finished = false

    init {
        require(inputSampleRate > 0) { "inputSampleRate must be positive" }
        require(outputSampleRate > 0) { "outputSampleRate must be positive" }
    }

    fun process(samples: ShortArray): ShortArray {
        check(!finished) { "Resampler is already finished" }
        if (samples.isEmpty()) return ShortArray(0)

        samples.forEach(bufferedSamples::add)
        totalInputSamples += samples.size
        return emitAvailable(allowLastSampleClamp = false, targetOutputCount = Long.MAX_VALUE)
    }

    fun finish(): ShortArray {
        check(!finished) { "Resampler is already finished" }
        finished = true
        if (totalInputSamples == 0L) return ShortArray(0)

        val targetOutputCount =
            (totalInputSamples.toDouble() * outputSampleRate / inputSampleRate)
                .roundToLong()
                .coerceAtLeast(1L)
        return emitAvailable(
            allowLastSampleClamp = true,
            targetOutputCount = targetOutputCount,
        )
    }

    private fun emitAvailable(
        allowLastSampleClamp: Boolean,
        targetOutputCount: Long,
    ): ShortArray {
        val result = ArrayList<Short>()
        while (nextOutputIndex() < targetOutputCount) {
            val leftIndex = nextOutputPositionNumerator / outputSampleRate
            val rightIndex = leftIndex + 1L
            val lastInputIndex = totalInputSamples - 1L
            if (leftIndex > lastInputIndex) break
            if (!allowLastSampleClamp && rightIndex > lastInputIndex) break

            val left = sampleAt(leftIndex).toInt()
            val right = sampleAt(rightIndex.coerceAtMost(lastInputIndex)).toInt()
            val fractionNumerator =
                (nextOutputPositionNumerator % outputSampleRate).toDouble()
            val fraction = fractionNumerator / outputSampleRate
            val interpolated = left + (right - left) * fraction
            result += interpolated.roundToLong()
                .coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
                .toShort()

            nextOutputPositionNumerator += inputSampleRate.toLong()
        }
        discardSamplesBefore(nextOutputPositionNumerator / outputSampleRate)
        return result.toShortArray()
    }

    private fun nextOutputIndex(): Long =
        nextOutputPositionNumerator / inputSampleRate

    private fun sampleAt(absoluteIndex: Long): Short {
        val localIndex = (absoluteIndex - bufferStartIndex).toInt()
        return bufferedSamples[localIndex]
    }

    private fun discardSamplesBefore(absoluteIndex: Long) {
        val removable = (absoluteIndex - bufferStartIndex)
            .coerceAtLeast(0L)
            .coerceAtMost((bufferedSamples.size - 1).coerceAtLeast(0).toLong())
            .toInt()
        if (removable > 0) {
            bufferedSamples.subList(0, removable).clear()
            bufferStartIndex += removable
        }
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
    }
}
