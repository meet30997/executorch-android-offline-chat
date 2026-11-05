package com.example.executorchdemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.executorchdemo.data.GenerationEvent
import com.example.executorchdemo.domain.model.ChatMessage
import com.example.executorchdemo.domain.model.InferenceMetrics
import com.example.executorchdemo.domain.model.MessageRole
import com.example.executorchdemo.domain.model.ModelStatus
import com.example.executorchdemo.domain.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        checkAndInitialize()
    }

    fun checkAndInitialize() {
        viewModelScope.launch {
            _uiState.update { it.copy(modelStatus = ModelStatus.Loading) }
            val status = repository.checkModelAvailability()
            if (status is ModelStatus.MissingFiles) {
                _uiState.update { it.copy(modelStatus = status) }
                return@launch
            }

            val result = repository.initializeModel()
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(modelStatus = ModelStatus.Ready, errorMessage = null) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            modelStatus = ModelStatus.Error(error.message ?: "Failed to initialize model"),
                            errorMessage = error.message
                        )
                    }
                }
            )
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun sendMessage() {
        val prompt = _uiState.value.inputText.trim()
        if (prompt.isBlank() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(role = MessageRole.USER, content = prompt)
        val assistantPlaceholder = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )

        // Clear input and append messages to state
        _uiState.update { current ->
            current.copy(
                messages = current.messages + userMessage + assistantPlaceholder,
                inputText = "",
                isGenerating = true,
                currentMetrics = null,
                errorMessage = null
            )
        }

        val assistantMessageId = assistantPlaceholder.id

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            // Provide complete conversation history including the new user message
            val history = _uiState.value.messages.dropLast(1)

            repository.generateReply(history).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        appendAssistantToken(assistantMessageId, event.text)
                    }
                    is GenerationEvent.Stats -> {
                        val metrics = InferenceMetrics(
                            promptTokens = event.promptTokens,
                            generatedTokens = event.generatedTokens,
                            tokensPerSec = event.tokensPerSec,
                            decodeDurationMs = event.decodeDurationMs,
                            timeToFirstTokenMs = event.timeToFirstTokenMs
                        )
                        _uiState.update { it.copy(currentMetrics = metrics) }
                    }
                    is GenerationEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                errorMessage = event.message
                            )
                        }
                        finalizeAssistantMessage(assistantMessageId, isError = true)
                    }
                    is GenerationEvent.Complete -> {
                        _uiState.update { it.copy(isGenerating = false) }
                        finalizeAssistantMessage(assistantMessageId, isError = false)
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        repository.stopGeneration()
        generationJob?.cancel()
        _uiState.update { current ->
            val updatedMessages = current.messages.map { msg ->
                if (msg.isStreaming) {
                    msg.copy(isStreaming = false, metrics = current.currentMetrics)
                } else {
                    msg
                }
            }
            current.copy(isGenerating = false, messages = updatedMessages)
        }
    }

    fun clearChat() {
        stopGeneration()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                currentMetrics = null,
                errorMessage = null
            )
        }
    }

    fun sendSuggestedPrompt(prompt: String) {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(inputText = prompt) }
        sendMessage()
    }

    private fun appendAssistantToken(messageId: String, token: String) {
        _uiState.update { current ->
            val updatedMessages = current.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(content = msg.content + token)
                } else {
                    msg
                }
            }
            current.copy(messages = updatedMessages)
        }
    }

    private fun finalizeAssistantMessage(messageId: String, isError: Boolean) {
        _uiState.update { current ->
            val updatedMessages = current.messages.map { msg ->
                if (msg.id == messageId) {
                    val finalContent = if (isError && msg.content.isBlank()) {
                        "⚠️ Generation failed: ${current.errorMessage ?: "Unknown error"}"
                    } else {
                        msg.content
                    }
                    msg.copy(
                        content = finalContent,
                        isStreaming = false,
                        metrics = current.currentMetrics
                    )
                } else {
                    msg
                }
            }
            current.copy(messages = updatedMessages)
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        repository.release()
    }

    class Factory(private val repositoryProvider: () -> ChatRepository) : ViewModelProvider.Factory {
        constructor(repository: ChatRepository) : this({ repository })

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repositoryProvider()) as T
        }
    }
}
