package com.example.executorchdemo.domain

import com.example.executorchdemo.data.ExecuTorchRunner
import com.example.executorchdemo.domain.model.ChatMessage

object ContextBudgeter {

    /** Maximum KV cache capacity compiled into the Llama 3.2 PTE model */
    const val MAX_CONTEXT_TOKENS = ExecuTorchRunner.MODEL_MAX_CONTEXT_LEN // 2048

    /** Maximum new tokens to decode per generation */
    const val MAX_NEW_TOKENS = 512

    /** Maximum budget allowed for input prompt tokens (2048 - 512 = 1536) */
    const val MAX_PROMPT_BUDGET = MAX_CONTEXT_TOKENS - MAX_NEW_TOKENS

    /**
     * Calculates a safe total sequence length budget (seqLen) for LlmModule.generate().
     *
     * As documented in research.md Trap 3, dividing characters by 3 is dangerous for code,
     * punctuation, and non-Latin text. We use promptLengthChars as a safe upper bound
     * to ensure seqLen is never smaller than actual prompt tokens.
     */
    fun calculateSafeBudget(promptLengthChars: Int, maxNewTokens: Int = MAX_NEW_TOKENS): Int {
        val estimatedPromptTokens = maxOf(promptLengthChars, 64)
        return minOf(MAX_CONTEXT_TOKENS, estimatedPromptTokens + maxNewTokens)
    }

    /**
     * Prunes older conversation turns so the resulting prompt fits safely
     * within MAX_PROMPT_BUDGET, ensuring multi-turn conversations never crash ExecuTorch.
     */
    fun pruneHistoryToFit(
        messages: List<ChatMessage>,
        maxBudgetTokens: Int = MAX_PROMPT_BUDGET
    ): List<ChatMessage> {
        if (messages.isEmpty()) return emptyList()

        val pruned = mutableListOf<ChatMessage>()
        var accumulatedChars = 0

        // Traverse backwards from the most recent turn
        for (msg in messages.reversed()) {
            val estimatedCost = msg.content.length + 32 // content + delimiter overhead
            if (accumulatedChars + estimatedCost > maxBudgetTokens && pruned.isNotEmpty()) {
                break
            }
            accumulatedChars += estimatedCost
            pruned.add(0, msg)
        }
        return pruned
    }
}
