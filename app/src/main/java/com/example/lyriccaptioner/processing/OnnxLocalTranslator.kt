package com.example.lyriccaptioner.processing

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class OnnxLocalTranslator(
    private val modelStore: LocalTranslationModelStore,
) : LocalTranslator {
    private val environment = OrtEnvironment.getEnvironment()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var tokenizer: SentencePieceTokenizer? = null
    private var prepared = false

    override suspend fun isModelReady(): Boolean = modelStore.isReady()

    override suspend fun prepareBatch() {
        if (prepared) return
        modelStore.prepare()
        try {
            val tokenizerJson = modelStore.file("tokenizer.json").readText()
            tokenizer = SentencePieceTokenizer.fromJson(tokenizerJson)
            encoder = environment.createSession(
                modelStore.file("encoder_model_quantized.onnx").absolutePath,
                OrtSession.SessionOptions(),
            )
            decoder = environment.createSession(
                modelStore.file("decoder_model_merged_quantized.onnx").absolutePath,
                OrtSession.SessionOptions(),
            )
            verifySessionContract()
            prepared = true
        } catch (error: CancellationException) {
            closeSessions()
            throw error
        } catch (error: Throwable) {
            closeSessions()
            throw IllegalStateException("The local OPUS-MT ONNX model could not be loaded.", error)
        }
    }

    override suspend fun translateEnglishToChinese(text: String): String {
        if (text.isBlank()) return ""
        prepareBatch()
        val ids = requireNotNull(tokenizer).encode(text)
        currentCoroutineContext().ensureActive()
        val hidden = runEncoder(ids)
        val outputIds = runGreedyDecoder(ids, hidden)
        return requireNotNull(tokenizer).decode(outputIds)
    }

    private fun verifySessionContract() {
        val encoderInputNames = requireNotNull(encoder).inputNames
        check("input_ids" in encoderInputNames && "attention_mask" in encoderInputNames) {
            "Unexpected OPUS-MT encoder inputs: $encoderInputNames"
        }
        check("last_hidden_state" in requireNotNull(encoder).outputNames) {
            "Unexpected OPUS-MT encoder outputs: ${encoder?.outputNames}"
        }
        val decoderInputNames = requireNotNull(decoder).inputNames
        check("input_ids" in decoderInputNames && "encoder_hidden_states" in decoderInputNames) {
            "Unexpected OPUS-MT decoder inputs: $decoderInputNames"
        }
        check("logits" in requireNotNull(decoder).outputNames) {
            "Unexpected OPUS-MT decoder outputs: ${decoder?.outputNames}"
        }
    }

    private fun runEncoder(ids: LongArray): Array<Array<FloatArray>> {
        val session = requireNotNull(encoder)
        val inputs = mutableListOf<OnnxTensor>()
        try {
            val inputIds = tensor(LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()), inputs)
            val mask = tensor(LongBuffer.wrap(LongArray(ids.size) { 1L }), longArrayOf(1, ids.size.toLong()), inputs)
            session.run(mapOf("input_ids" to inputIds, "attention_mask" to mask)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return result.get("last_hidden_state").get().value as Array<Array<FloatArray>>
            }
        } finally {
            inputs.forEach { it.close() }
        }
    }

    private suspend fun runGreedyDecoder(
        encoderIds: LongArray,
        hidden: Array<Array<FloatArray>>,
    ): LongArray {
        val session = requireNotNull(decoder)
        val encoderLength = encoderIds.size.toLong()
        val attentionMask = LongArray(encoderIds.size) { 1L }
        val generated = ArrayList<Long>()

        repeat(MAX_OUTPUT_TOKENS) {
            currentCoroutineContext().ensureActive()
            val tensors = mutableListOf<OnnxTensor>()
            try {
                val decoderInput = (listOf(65000L) + generated).toLongArray()
                val inputs = linkedMapOf<String, OnnxTensor>()
                inputs["input_ids"] = tensor(LongBuffer.wrap(decoderInput), longArrayOf(1, decoderInput.size.toLong()), tensors)
                inputs["encoder_attention_mask"] = tensor(LongBuffer.wrap(attentionMask), longArrayOf(1, encoderLength), tensors)
                inputs["encoder_hidden_states"] = tensor(hidden, tensors)
                inputs["use_cache_branch"] = tensor(booleanArrayOf(false), tensors)
                session.inputNames.filter { it.startsWith("past_key_values.") }.forEach { name ->
                    inputs[name] = tensor(FloatBuffer.wrap(FloatArray(0)), emptyPastShape(name), tensors)
                }
                session.run(inputs).use { result ->
                    val logits = result.get("logits").get().value as Array<Array<FloatArray>>
                    val token = argMax(logits[0].last())
                    if (token == 0L) return generated.toLongArray()
                    generated += token
                }
            } finally {
                tensors.forEach { it.close() }
            }
        }
        throw IllegalStateException("The local OPUS-MT decoder exceeded $MAX_OUTPUT_TOKENS tokens without EOS.")
    }

    private fun emptyPastShape(name: String): LongArray {
        return if (name.contains("encoder")) longArrayOf(1, 8, 0, 64) else longArrayOf(1, 8, 0, 64)
    }

    private fun argMax(values: FloatArray): Long {
        var index = 0
        for (candidate in 1 until values.size) {
            if (values[candidate] > values[index]) index = candidate
        }
        return index.toLong()
    }

    private fun tensor(buffer: LongBuffer, shape: LongArray, owned: MutableList<OnnxTensor>): OnnxTensor =
        OnnxTensor.createTensor(environment, buffer, shape).also(owned::add)

    private fun tensor(buffer: FloatBuffer, shape: LongArray, owned: MutableList<OnnxTensor>): OnnxTensor =
        OnnxTensor.createTensor(environment, buffer, shape).also(owned::add)

    private fun tensor(value: Any, owned: MutableList<OnnxTensor>): OnnxTensor =
        OnnxTensor.createTensor(environment, value).also(owned::add)

    private fun closeSessions() {
        encoder?.close()
        decoder?.close()
        encoder = null
        decoder = null
        tokenizer = null
        prepared = false
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 128
    }
}
