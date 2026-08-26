package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.processing.TranslationBatchException
import com.example.lyriccaptioner.processing.TranslationModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CaptionEnhancementService {
    /**
     * Only transport/provider failures in this explicit allowlist may use the local path.
     * Do not consult [CaptionEnhancementException.recoverable] here: that property is part of
     * the public exception type and a provider implementation must not be able to opt an
     * authentication, unknown, or programming failure into a silent downgrade.
     */
    private val fallbackErrorKinds = setOf(
        CaptionEnhancementErrorKind.OFFLINE,
        CaptionEnhancementErrorKind.CONNECTION,
        CaptionEnhancementErrorKind.TIMEOUT,
        CaptionEnhancementErrorKind.RETRYABLE_SERVER,
        CaptionEnhancementErrorKind.INVALID_RESPONSE,
    )

    suspend fun enhance(
        jobId: String,
        captions: List<CaptionCue>,
    ): CaptionEnhancementOutcome = enhance(jobId, captions, {}, null)

    override
    suspend fun enhance(
        jobId: String,
        captions: List<CaptionCue>,
        onStateChanged: (CaptionEnhancementState) -> Unit,
        mediaDurationMs: Long?,
    ): CaptionEnhancementOutcome {
        // Keep this snapshot untouched throughout the operation. TranslationModule already
        // performs an atomic batch commit, and passing this list prevents cloud corrections from
        // becoming fallback input after a recoverable provider failure.
        emit(CaptionEnhancementState.RAW_ASR_READY, onStateChanged)
        val (originalCaptions, request) = try {
            withContext(workerDispatcher) {
                val snapshot = captions.toList()
                snapshot to mapper.map(
                    jobId = jobId,
                    captions = snapshot,
                    mediaDurationMs = mediaDurationMs,
                )
            }
        } catch (_: CancellationException) {
            emit(CaptionEnhancementState.CANCELLED, onStateChanged)
            throw safeCancellationException()
        }
        emit(CaptionEnhancementState.CLOUD_PENDING, onStateChanged)

        try {
            val response = try {
                // Exactly one provider invocation belongs to one enhancement job.
                withContext(workerDispatcher) {
                    provider.enhance(request)
                }
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
            val validated = withContext(workerDispatcher) {
                validator.validate(
                    request = request,
                    response = response,
                    rawCaptions = originalCaptions,
                )
            }
            emit(CaptionEnhancementState.CLOUD_APPLIED, onStateChanged)
            return CaptionEnhancementOutcome(
                captions = validated.captions,
                source = CaptionResultSource.CLOUD_AI,
                state = CaptionEnhancementState.CLOUD_APPLIED,
                processingVersion = validated.processingVersion,
                songMatch = validated.songMatch,
            )
        } catch (_: CancellationException) {
            emit(CaptionEnhancementState.CANCELLED, onStateChanged)
            // Do not re-expose a provider/coroutine cancellation message or cause: cancellation
            // is intentionally observable only through the state and cancellation type.
            throw safeCancellationException()
        } catch (error: CaptionEnhancementException) {
            if (error.kind !in fallbackErrorKinds) {
                // A provider may have put arbitrary detail in its exception message. Rebuild
                // non-fallback failures with a fixed, non-sensitive message before exposing
                // them to the caller. The original cause is already discarded by the contract.
                throw safeNonFallbackException(error)
            }
            return applyLocalFallback(
                originalCaptions = originalCaptions,
                providerError = error,
                onStateChanged = onStateChanged,
            )
        } catch (_: Throwable) {
            // Validation/provider implementations can still throw an unchecked programming or
            // transport exception. Keep the failure visible as UNKNOWN, never start fallback,
            // and do not leak its message, cause, wire payload, credentials, or paths.
            throw CaptionEnhancementProviderException(
                kind = CaptionEnhancementErrorKind.UNKNOWN,
                safeDetail = safeMessageFor(CaptionEnhancementErrorKind.UNKNOWN),
            )
        }
    }

    private fun safeNonFallbackException(error: CaptionEnhancementException): CaptionEnhancementException =
        if (error is CaptionEnhancementProviderException) {
            CaptionEnhancementProviderException(
                kind = error.kind,
                safeDetail = safeMessageFor(error.kind),
            )
        } else {
            CaptionEnhancementException(
                kind = error.kind,
                recoverable = false,
                message = safeMessageFor(error.kind),
            )
        }

    private fun safeMessageFor(kind: CaptionEnhancementErrorKind): String = when (kind) {
        CaptionEnhancementErrorKind.AUTHENTICATION -> "Provider authentication failed."
        CaptionEnhancementErrorKind.UNKNOWN -> "Caption enhancement provider request failed."
        CaptionEnhancementErrorKind.LOCAL_TRANSLATION -> "Local caption translation failed."
        CaptionEnhancementErrorKind.OFFLINE -> "Caption enhancement is offline."
        CaptionEnhancementErrorKind.CONNECTION -> "Caption enhancement connection failed."
        CaptionEnhancementErrorKind.TIMEOUT -> "Caption enhancement request timed out."
        CaptionEnhancementErrorKind.RETRYABLE_SERVER -> "Caption enhancement service is temporarily unavailable."
        CaptionEnhancementErrorKind.INVALID_RESPONSE -> "Caption enhancement response was invalid."
    }

    private fun safeCancellationException(): CancellationException =
        CancellationException("Caption enhancement cancelled.")

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
            val translated = withContext(workerDispatcher) {
                localTranslation.translateMissingChinese(originalCaptions)
            }
            currentCoroutineContext().ensureActive()
            emit(CaptionEnhancementState.LOCAL_FALLBACK_APPLIED, onStateChanged)
            return CaptionEnhancementOutcome(
                captions = translated.captions,
                source = CaptionResultSource.LOCAL_FALLBACK,
                state = CaptionEnhancementState.LOCAL_FALLBACK_APPLIED,
                errorKind = providerError.kind,
            )
        } catch (_: CancellationException) {
            emit(CaptionEnhancementState.CANCELLED, onStateChanged)
            throw safeCancellationException()
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
