package com.example.executorchdemo.ui

import com.example.executorchdemo.domain.model.ChatMessage
import com.example.executorchdemo.domain.model.InferenceMetrics
import com.example.executorchdemo.domain.model.ModelStatus

data class ChatUiState(
    val modelStatus: ModelStatus = ModelStatus.Uninitialized,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val currentMetrics: InferenceMetrics? = null,
    val errorMessage: String? = null
)
