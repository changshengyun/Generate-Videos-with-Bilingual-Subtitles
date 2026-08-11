package com.example.lyriccaptioner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductUiContractTest {
    @Test
    fun productSectionsExposeOnlyImportProcessingEditorAndExport() {
        assertEquals(
            listOf(ProductUiSection.IMPORT, ProductUiSection.PROCESSING, ProductUiSection.EDITOR, ProductUiSection.EXPORT),
            ProductUiSection.values().toList(),
        )
    }

    @Test
    fun recognitionSuccessStaysInCurrentSectionUntilExplicitEditAction() {
        val processing = ProductUiState(section = ProductUiSection.PROCESSING)
        val running = ProductUiContract.reduce(processing, ProductUiEvent.StartRecognition)
        val success = ProductUiContract.reduce(running, ProductUiEvent.RecognitionSucceeded(2))

        assertEquals(ProductUiSection.PROCESSING, success.section)
        assertTrue(success.canEnterEditor)
        assertFalse(success.captionsVisible)
        val entered = ProductUiContract.reduce(success, ProductUiEvent.EnterEditor)
        assertEquals(ProductUiSection.EDITOR, entered.section)
        assertTrue(entered.captionsVisible)
    }

    @Test
    fun emptyRecognitionDoesNotOfferEditorEntry() {
        val running = ProductUiContract.reduce(ProductUiState(ProductUiSection.PROCESSING), ProductUiEvent.StartRecognition)
        val result = ProductUiContract.reduce(running, ProductUiEvent.RecognitionSucceeded(0))
        assertFalse(result.canEnterEditor)
        assertFalse(result.captionsVisible)
        assertEquals(result, ProductUiContract.reduce(result, ProductUiEvent.EnterEditor))
    }

    @Test
    fun cancellationAndFailureKeepSectionAndExposeRetry() {
        val running = ProductUiContract.reduce(ProductUiState(ProductUiSection.IMPORT), ProductUiEvent.StartImport)
        val cancelled = ProductUiContract.reduce(running, ProductUiEvent.ImportCancelled)
        assertEquals(ProductUiSection.IMPORT, cancelled.section)
        assertTrue(cancelled.canRetry)
        assertEquals(ProductUiStatus.CANCELLED, cancelled.status)

        val retrying = ProductUiContract.reduce(cancelled, ProductUiEvent.Retry)
        assertEquals(ProductUiOperation.IMPORT, retrying.operation)
        assertEquals(ProductUiStatus.RUNNING, retrying.status)

        val failed = ProductUiContract.reduce(retrying, ProductUiEvent.ImportFailed)
        assertEquals(ProductUiStatus.FAILED, failed.status)
        assertTrue(failed.canRetry)
    }

    @Test
    fun conflictingActionsAreBlockedWhileWorkIsActive() {
        val running = ProductUiContract.reduce(ProductUiState(), ProductUiEvent.StartRecognition)
        assertTrue(running.blocksConflictingActions)
        assertEquals(running, ProductUiContract.reduce(running, ProductUiEvent.StartImport))
        assertEquals(running, ProductUiContract.reduce(running, ProductUiEvent.EnterEditor))
        assertEquals(running, ProductUiContract.reduce(running, ProductUiEvent.SelectSection(ProductUiSection.EXPORT)))
    }

    @Test
    fun saveAndExportHaveVisibleTerminalStatesAndDoNotLeakPrivateMedia() {
        val editor = ProductUiState(section = ProductUiSection.EDITOR)
        val save = ProductUiContract.reduce(editor, ProductUiEvent.StartSave)
        val saved = ProductUiContract.reduce(save, ProductUiEvent.SaveSucceeded)
        assertEquals(ProductUiStatus.SAVED, saved.status)
        assertEquals(ProductUiSection.EDITOR, saved.section)

        val export = ProductUiContract.reduce(saved, ProductUiEvent.StartExport)
        val exported = ProductUiContract.reduce(export, ProductUiEvent.ExportSucceeded)
        assertEquals(ProductUiStatus.EXPORTED, exported.status)
        assertEquals(ProductUiSection.EXPORT, exported.section)
        assertFalse(exported.toString().contains("content://"))
        assertFalse(exported.toString().contains("/sdcard"))
    }
}
