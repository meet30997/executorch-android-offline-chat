package com.example.executorchdemo.domain.repository

import com.example.executorchdemo.data.GenerationEvent
import com.example.executorchdemo.domain.model.ChatMessage
import com.example.executorchdemo.domain.model.ModelStatus
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeModelStatus(): Flow<ModelStatus>
    suspend fun checkModelAvailability(): ModelStatus
    suspend fun initializeModel(): Result<Unit>
    fun generateReply(conversationHistory: List<ChatMessage>): Flow<GenerationEvent>
    fun stopGeneration()
    fun release()
}
