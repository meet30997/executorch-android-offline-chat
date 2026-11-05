package com.example.executorchdemo.domain.model

import java.util.Locale

data class InferenceMetrics(
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val tokensPerSec: Double = 0.0,
    val decodeDurationMs: Long = 0L,
    val timeToFirstTokenMs: Long = 0L
) {
    val formattedTokensPerSec: String
        get() = String.format(Locale.US, "%.1f", tokensPerSec)

    val formattedDurationSec: String
        get() = String.format(Locale.US, "%.2f s", decodeDurationMs / 1000.0)
}
