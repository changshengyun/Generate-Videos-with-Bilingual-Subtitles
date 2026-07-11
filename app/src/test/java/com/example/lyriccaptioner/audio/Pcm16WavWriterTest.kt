package com.example.lyriccaptioner.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Pcm16WavWriterTest {
    @Test
    fun writesLittleEndianSamplesAndPatchesHeaderSizes() {
        val file = Files.createTempFile("pcm16-writer", ".wav").toFile()
        try {
            Pcm16WavWriter(file, sampleRate = 16_000).use { writer ->
                writer.write(shortArrayOf(0x1234, -2))
                writer.write(shortArrayOf(7, 8, 9), offset = 1, length = 1)
            }

            val bytes = file.readBytes()
            assertArrayEquals("RIFF".toByteArray(), bytes.copyOfRange(0, 4))
            assertEquals(42L, uint32(bytes, 4))
            assertArrayEquals("WAVE".toByteArray(), bytes.copyOfRange(8, 12))
            assertEquals(1, uint16(bytes, 20))
            assertEquals(1, uint16(bytes, 22))
            assertEquals(16_000L, uint32(bytes, 24))
            assertEquals(32_000L, uint32(bytes, 28))
            assertEquals(2, uint16(bytes, 32))
            assertEquals(16, uint16(bytes, 34))
            assertEquals(6L, uint32(bytes, 40))
            assertArrayEquals(
                byteArrayOf(0x34, 0x12, 0xfe.toByte(), 0xff.toByte(), 0x08, 0x00),
                bytes.copyOfRange(44, bytes.size),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptyWavHasValidHeader() {
        val file = Files.createTempFile("empty-pcm16", ".wav").toFile()
        try {
            Pcm16WavWriter(file, sampleRate = 44_100, channelCount = 2).close()

            val bytes = file.readBytes()
            assertEquals(44, bytes.size)
            assertEquals(36L, uint32(bytes, 4))
            assertEquals(0L, uint32(bytes, 40))
            assertEquals(176_400L, uint32(bytes, 28))
            assertEquals(4, uint16(bytes, 32))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsInvalidSliceAndWritesAfterClose() {
        val file = File.createTempFile("closed-pcm16", ".wav")
        try {
            val writer = Pcm16WavWriter(file, sampleRate = 16_000)
            assertThrows(IllegalArgumentException::class.java) {
                writer.write(shortArrayOf(1), offset = 1, length = 1)
            }
            writer.close()
            writer.close()
            assertThrows(IllegalStateException::class.java) {
                writer.write(shortArrayOf(1))
            }
        } finally {
            file.delete()
        }
    }

    private fun uint16(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffff_ffffL
}
