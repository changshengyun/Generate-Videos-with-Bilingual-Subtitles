package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.processing.TranslationBatchException
import com.example.lyriccaptioner.processing.TranslationModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Orchestrates one complete cloud enhancement attempt and its deterministic local fallback.
 *
 * The coordinator deliberately keeps the original ASR batch as the only fallback input.  A
 * provider response is never merged cue-by-cue: it is validated as a complete batch first and
 * only then exposed as a [CaptionEnhancementOutcome].
 */
class CaptionEnhancementCoordinator(
    private val provider: CaptionEnhancementProvider,
    private val localTranslation: TranslationModule,
    private val validator: CaptionEnhancementResponseValidator = CaptionEnhancementResponseValidator(),
    private val mapper: CaptionEnhancementRequestMapper = CaptionEnhancementRequestMapper(),
) : CaptionEnhancementService {
    suspend fun enhance(
        jobId: String,
        captions: List<CaptionCue>,
    ): CaptionEnhancementOutcome = enhance(jobId, captions, {})

    override
    suspend fun enhance(
        jobId: String,
        captions: List<CaptionCue>,
        onStateChanged: (CaptionEnhancementState) -> Unit,
    ): CaptionEnhancementOutcome {
        // Keep this snapshot untouched throughout the operation. TranslationModule already
        // performs an atomic batch commit, and passing this list prevents cloud corrections from
        // becoming fallback input after a recoverable provider failure.
        val originalCaptions = captions.toList()
        emit(CaptionEnhancementState.RAW_ASR_READY, onStateChanged)
        val request = mapper.map(jobId = jobId, captions = originalCaptions)
        emit(CaptionEnhancementState.CLOUD_PENDING, onStateChanged)

        try {
            val response = try {
                // Exactly one provider invocation belongs to one enhancement job.
                provider.enhance(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: CaptionEnhancementProviderException) {
                throw error
            } catch (error: CaptionEnhancementException) {
                throw error
            } catch (error: Throwable) {
                // Provider implementations are allowed to surface transport-specific errors;
                // keep those details out of the public error text while mapping them to fallback.
                throw CaptionEnhancementProviderException(
                    kind = CaptionEnhancementErrorKind.UNKNOWN,
                    safeDetail = "Caption enhancement provider request failed.",
                    cause = error,
                )
            }

            emit(CaptionEnhancementState.CLOUD_VALIDATING, onStateChanged)
            val validated = validator.validate(
                request = request,
                response = response,
                rawCaptions = originalCaptions,
            )
            emit(CaptionEnhancementState.CLOUD_APPLIED, onStateChanged)
            return CaptionEnhancementOutcome(
                captions = validated.captions,
                source = CaptionResultSource.CLOUD_AI,
                state = CaptionEnhancementState.CLOUD_APPLIED,
                processingVersion = validated.processingVersion,
                songMatch = validated.songMatch,
            )
        } catch (error: CancellationException) {
            emit(CaptionEnhancementState.CANCELLED, onStateChanged)
            throw error
        } catch (error: CaptionEnhancementException) {
            if (!error.recoverable) throw error
            return applyLocalFallback(
                originalCaptions = originalCaptions,
                providerError = error,
                onStateChanged = onStateChanged,
            )
        }
    }

    private suspend fun applyLocalFallback(
        originalCaptions: List<CaptionCue>,
        providerError: CaptionEnhancementException,
        onStateChanged: (CaptionEnhancementState) -> Unit,
    ): CaptionEnhancementOutcome {
        try {
            currentCoroutineContext().ensureActive()
            // TranslationModule translates only missing Chinese fields, but always uses the
            // original Whisper English from originalCaptions. Its result is committed only after
            // every cue succeeds, so no partial local batch can escape this method.
            val translated = localTranslation.translateMissingChinese(originalCaptions)
            currentCoroutineContext().ensureActive()
            emit(CaptionEnhancementState.LOCAL_FALLBACK_APPLIED, onStateChanged)
            return CaptionEnhancementOutcome(
                captions = translated.captions,
                source = CaptionResultSource.LOCAL_FALLBACK,
                state = CaptionEnhancementState.LOCAL_FALLBACK_APPLIED,
                errorKind = providerError.kind,
            )
        } catch (error: CancellationException) {
            emit(CaptionEnhancementState.CANCELLED, onStateChanged)
            throw error
        } catch (error: Throwable) {
            val cause = if (error is TranslationBatchException) error else error
            throw CaptionEnhancementException(
                kind = CaptionEnhancementErrorKind.LOCAL_TRANSLATION,
                recoverable = true,
                message = "Local caption translation failed.",
                cause = cause,
            )
        }
    }

    private fun emit(
        state: CaptionEnhancementState,
        onStateChanged: (CaptionEnhancementState) -> Unit,
    ) {
        onStateChanged(state)
    }
}
