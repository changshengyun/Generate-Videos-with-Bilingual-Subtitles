package com.example.lyriccaptioner.processing.enhancement.byok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekKeyUiContractTest {
    @Test
    fun unconfiguredUiShowsSaveOnlyAndNoSecret() {
        val model = DeepSeekKeyUiMapper.from(DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED))
        assertEquals(DEEPSEEK_PROVIDER, model.provider)
        assertEquals(DEEPSEEK_BASE_URL, model.baseUrl)
        assertTrue(model.showSave)
        assertFalse(model.showReplace)
        assertFalse(model.showDelete)
    }

    @Test
    fun configuredUiShowsOnlyMaskedSuffixAndReplaceDeleteActions() {
        val key = "sk-test-sensitive-sentinel-never-real"
        val model = DeepSeekKeyUiMapper.from(
            DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, DeepSeekKeyMasker.mask(key)),
        )
        assertEquals("real", model.maskedKey?.takeLast(4))
        assertFalse(model.showSave)
        assertTrue(model.showReplace)
        assertTrue(model.showDelete)
        assertFalse(model.maskedKey!!.contains(key))
    }
}
