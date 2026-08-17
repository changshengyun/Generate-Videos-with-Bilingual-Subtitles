package com.example.lyriccaptioner.processing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SentencePieceTokenizerTest {
    @Test
    fun encodeUsesLanguagePrefixBestUnigramPathAndEos() {
        val tokenizer = SentencePieceTokenizer.forTest(
            listOf(
                "</s>" to 0f,
                "<unk>" to -100f,
                "x" to -10f,
                "y" to -10f,
                "z" to -10f,
                ">>cmn_Hans<<" to 0f,
                "\u2581hello" to -1f,
                "\u2581world" to -1f,
                "\u2581" to -20f,
                "hello" to -20f,
                "world" to -20f,
            ),
        )

        assertArrayEquals(longArrayOf(5L, 6L, 7L, 0L), tokenizer.encode("hello   world"))
    }

    @Test
    fun decodeRemovesSpecialTokensAndRestoresSpaces() {
        val tokenizer = SentencePieceTokenizer.forTest(
            listOf(
                "</s>" to 0f,
                "<unk>" to -100f,
                "x" to -10f,
                "y" to -10f,
                "z" to -10f,
                ">>cmn_Hans<<" to 0f,
                "\u2581你好" to -1f,
                "\u2581世界" to -1f,
            ),
        )

        assertEquals("你好 世界", tokenizer.decode(longArrayOf(5L, 6L, 7L, 0L, 65000L)))
    }

    @Test
    fun blankInputStillTerminatesDeterministically() {
        val tokenizer = SentencePieceTokenizer.forTest(
            listOf(
                "</s>" to 0f,
                "<unk>" to -100f,
                "x" to -10f,
                "y" to -10f,
                "z" to -10f,
                ">>cmn_Hans<<" to 0f,
            ),
        )

        assertArrayEquals(longArrayOf(5L, 0L), tokenizer.encode(" "))
    }
}
