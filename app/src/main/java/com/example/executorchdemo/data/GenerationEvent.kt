package com.example.executorchdemo.data

sealed interface GenerationEvent {
    /** Emitted for each decoded token fragment */
    data class Token(val text: String) : GenerationEvent

    /** Emitted on completion with prompt and generation telemetry */
    data class Stats(
        val promptTokens: Int,
        val generatedTokens: Int,
        val tokensPerSec: Double,
        val decodeDurationMs: Long,
        val timeToFirstTokenMs: Long
    ) : GenerationEvent

    /** Emitted if a native C++ runtime error occurs */
    data class Error(val message: String) : GenerationEvent

    /** Emitted when generation concludes normally */
    data object Complete : GenerationEvent
}
