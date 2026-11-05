package com.example.executorchdemo.domain

import com.example.executorchdemo.domain.model.InferenceMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceMetricsTest {

    @Test
    fun `formattedTokensPerSec formats correctly to one decimal place`() {
        val metrics = InferenceMetrics(tokensPerSec = 24.567)
        assertEquals("24.6", metrics.formattedTokensPerSec)
    }

    @Test
    fun `formattedDurationSec formats decode milliseconds to seconds with two decimals`() {
        val metrics = InferenceMetrics(decodeDurationMs = 2345L)
        assertEquals("2.35 s", metrics.formattedDurationSec)
    }
}
