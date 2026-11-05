package com.example.executorchdemo.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import org.pytorch.executorch.extension.llm.LlmModuleConfig
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class ExecuTorchRunner : Closeable {

    companion object {
        private const val TAG = "ExecuTorchRunner"

        /**
         * Maximum compiled context length in the exported Llama 3.2 PTE model.
         * Exceeding this value will cause an unrecoverable native assertion crash.
         */
        const val MODEL_MAX_CONTEXT_LEN = 2048

        /** Turn and conversation stop tokens emitted by Llama 3.2 */
        private const val STOP_TOKEN_EOT = "<|eot_id|>"
        private const val STOP_TOKEN_EOS = "<|end_of_text|>"
    }

    private var llmModule: LlmModule? = null
    private val isLoaded = AtomicBoolean(false)
    private val isGenerating = AtomicBoolean(false)

    /**
     * Loads the model into virtual memory using LOAD_MODE_MMAP.
     * Must be executed on Dispatchers.IO.
     */
    suspend fun loadModel(modelPaths: ModelPaths) = withContext(Dispatchers.IO) {
        if (isLoaded.get()) {
            Log.d(TAG, "Model already loaded")
            return@withContext
        }

        Log.i(TAG, "Configuring LlmModule with MMAP...")
        val config = LlmModuleConfig.create()
            .modulePath(modelPaths.modelPtePath)
            .tokenizerPath(modelPaths.tokenizerPath)
            .modelType(LlmModuleConfig.MODEL_TYPE_TEXT)
            .loadMode(LlmModuleConfig.LOAD_MODE_MMAP) // Prevents out-of-memory crashes on 4-6 GB devices
            .temperature(0.7f)
            .build()

        val module = LlmModule(config)
        Log.i(TAG, "Invoking native module.load()...")
        module.load()

        llmModule = module
        isLoaded.set(true)
        Log.i(TAG, "LlmModule loaded and ready for inference")
    }

    fun isReady(): Boolean = isLoaded.get() && llmModule != null

    /**
     * Executes streaming inference on Dispatchers.IO.
     *
     * @param prompt Fully formatted prompt including chat template delimiters.
     * @param maxNewTokens Upper bound on generated tokens.
     * @param temperature Sampling temperature.
     */
    fun generateStream(
        prompt: String,
        maxNewTokens: Int = 512,
        temperature: Float = 0.7f
    ): Flow<GenerationEvent> = callbackFlow {
        val module = llmModule
        if (module == null || !isLoaded.get()) {
            trySendBlocking(GenerationEvent.Error("ExecuTorch model is not initialized"))
            close()
            return@callbackFlow
        }

        // Prevent concurrent inference attempts on the stateful native runtime
        if (!isGenerating.compareAndSet(false, true)) {
            trySendBlocking(GenerationEvent.Error("Inference is already in progress"))
            close()
            return@callbackFlow
        }

        var tokenCount = 0
        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var hasError = false
        var isHalted = false

        val callback = object : LlmCallback {
            override fun onResult(result: String) {
                if (isHalted) return
                if (firstTokenTime == null) {
                    firstTokenTime = System.currentTimeMillis()
                }

                // Intercept Llama 3 turn-stop tokens
                if (result.contains(STOP_TOKEN_EOT) || result.contains(STOP_TOKEN_EOS)) {
                    val cleaned = result
                        .replace(STOP_TOKEN_EOT, "")
                        .replace(STOP_TOKEN_EOS, "")
                    if (cleaned.isNotEmpty()) {
                        trySendBlocking(GenerationEvent.Token(cleaned))
                    }
                    isHalted = true
                    module.stop()
                    return
                }

                tokenCount++
                trySendBlocking(GenerationEvent.Token(result))

                // Enforce generation token ceiling
                if (tokenCount >= maxNewTokens) {
                    isHalted = true
                    module.stop()
                }
            }

            override fun onStats(statsJson: String) {
                var promptTokens = 0
                var generatedTokens = tokenCount

                try {
                    if (statsJson.isNotBlank()) {
                        val json = JSONObject(statsJson)
                        promptTokens = json.optInt("prompt_tokens", 0)
                        generatedTokens = json.optInt("generated_tokens", tokenCount)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse telemetry JSON: $statsJson", e)
                }

                val now = System.currentTimeMillis()
                val decodeDurationMs = now - (firstTokenTime ?: startTime)
                val decodeDurationSec = decodeDurationMs / 1000.0
                val tokensPerSec = if (decodeDurationSec > 0.0) generatedTokens / decodeDurationSec else 0.0
                val timeToFirstTokenMs = (firstTokenTime ?: now) - startTime

                trySendBlocking(
                    GenerationEvent.Stats(
                        promptTokens = promptTokens,
                        generatedTokens = generatedTokens,
                        tokensPerSec = tokensPerSec,
                        decodeDurationMs = decodeDurationMs,
                        timeToFirstTokenMs = timeToFirstTokenMs
                    )
                )
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "Native ExecuTorch error ($errorCode): $errorMessage")
                hasError = true
                isHalted = true
                trySendBlocking(GenerationEvent.Error("ExecuTorch error ($errorCode): $errorMessage"))
            }
        }

        // Attach cancellation listener to the collecting coroutine job
        // so that cancelling the coroutine halts the blocking C++ native loop immediately
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion {
            module.stop()
        }

        try {
            // Safe context budgeting: prevent KV cache buffer overflow (Trap 3)
            val budgetedSeqLen = minOf(
                MODEL_MAX_CONTEXT_LEN,
                maxOf(prompt.length, 64) + maxNewTokens
            )

            // Reset KV cache before each run to avoid context duplication (Trap 6)
            module.resetContext()

            // Invoke direct 7-parameter generation overload to avoid multimodal prefill bugs (Trap 1)
            module.generate(
                prompt,
                budgetedSeqLen,
                callback,
                /* echo = */ false,
                temperature,
                /* numBos = */ 0,
                /* numEos = */ 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generation", e)
            hasError = true
            trySendBlocking(GenerationEvent.Error(e.message ?: "Generation exception"))
        } finally {
            cancellationHandle.dispose()
            isGenerating.set(false)
            if (!hasError) {
                trySendBlocking(GenerationEvent.Complete)
            }
            close()
        }

        awaitClose {
            module.stop()
            isGenerating.set(false)
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        llmModule?.stop()
        isGenerating.set(false)
    }

    override fun close() {
        try {
            isLoaded.set(false)
            isGenerating.set(false)
            llmModule?.stop()
            llmModule?.close()
            llmModule = null
            Log.i(TAG, "ExecuTorchRunner released native resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LlmModule", e)
        }
    }
}
