package com.example.lyriccaptioner.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * Streaming little-endian PCM WAV writer. RIFF and data sizes are patched on close.
 */
class Pcm16WavWriter(
    file: File,
    private val sampleRate: Int,
    private val channelCount: Int = 1,
) : Closeable {
    private val output = RandomAccessFile(file, "rw")
    private var dataByteCount = 0L
    private var closed = false

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channelCount > 0) { "channelCount must be positive" }
        output.setLength(0L)
        writeHeader(dataSize = 0L)
    }

    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size - offset) {
        check(!closed) { "Writer is closed" }
        require(offset >= 0 && length >= 0 && offset + length <= samples.size) {
            "Invalid offset or length"
        }
        check(dataByteCount + length * BYTES_PER_SAMPLE <= UINT32_MAX) {
            "WAV data exceeds the 4 GiB RIFF limit"
        }

        val bytes = ByteArray(length * BYTES_PER_SAMPLE)
        var byteIndex = 0
        for (index in offset until offset + length) {
            val value = samples[index].toInt()
            bytes[byteIndex++] = value.toByte()
            bytes[byteIndex++] = (value ushr 8).toByte()
        }
        output.write(bytes)
        dataByteCount += bytes.size
    }

    override fun close() {
        if (closed) return
        closed = true
        output.seek(0L)
        writeHeader(dataByteCount)
        output.close()
    }

    private fun writeHeader(dataSize: Long) {
        output.writeAscii("RIFF")
        output.writeUInt32LittleEndian(36L + dataSize)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeUInt32LittleEndian(16L)
        output.writeUInt16LittleEndian(PCM_FORMAT)
        output.writeUInt16LittleEndian(channelCount)
        output.writeUInt32LittleEndian(sampleRate.toLong())
        output.writeUInt32LittleEndian(
            sampleRate.toLong() * channelCount * BYTES_PER_SAMPLE,
        )
        output.writeUInt16LittleEndian(channelCount * BYTES_PER_SAMPLE)
        output.writeUInt16LittleEndian(BITS_PER_SAMPLE)
        output.writeAscii("data")
        output.writeUInt32LittleEndian(dataSize)
    }

    private fun RandomAccessFile.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun RandomAccessFile.writeUInt16LittleEndian(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun RandomAccessFile.writeUInt32LittleEndian(value: Long) {
        write((value and 0xff).toInt())
        write((value ushr 8 and 0xff).toInt())
        write((value ushr 16 and 0xff).toInt())
        write((value ushr 24 and 0xff).toInt())
    }

    companion object {
        private const val PCM_FORMAT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        private const val UINT32_MAX = 0xffff_ffffL
    }
}
