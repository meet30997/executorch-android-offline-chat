package com.example.executorchdemo.domain.repository

import com.example.executorchdemo.data.ExecuTorchRunner
import com.example.executorchdemo.data.GenerationEvent
import com.example.executorchdemo.data.ModelFileStatus
import com.example.executorchdemo.data.ModelManager
import com.example.executorchdemo.domain.ContextBudgeter
import com.example.executorchdemo.domain.PromptFormatter
import com.example.executorchdemo.domain.model.ChatMessage
import com.example.executorchdemo.domain.model.ModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatRepositoryImpl(
    private val modelManager: ModelManager,
    private val runner: ExecuTorchRunner
) : ChatRepository {

    private val _statusFlow = MutableStateFlow<ModelStatus>(ModelStatus.Uninitialized)

    override fun observeModelStatus(): Flow<ModelStatus> = _statusFlow.asStateFlow()

    override suspend fun checkModelAvailability(): ModelStatus {
        val fileStatus = modelManager.checkStatus()
        val status = when (fileStatus) {
            is ModelFileStatus.Ready -> {
                if (runner.isReady()) ModelStatus.Ready else ModelStatus.Uninitialized
            }
            is ModelFileStatus.Missing -> {
                ModelStatus.MissingFiles(
                    missingFiles = fileStatus.missingFiles,
                    targetDir = fileStatus.expectedDirectory
                )
            }
        }
        _statusFlow.value = status
        return status
    }

    override suspend fun initializeModel(): Result<Unit> {
        val check = checkModelAvailability()
        if (check is ModelStatus.MissingFiles) {
            return Result.failure(IllegalStateException("Missing model files: ${check.missingFiles}"))
        }

        return try {
            _statusFlow.value = ModelStatus.Loading
            val paths = modelManager.getVerifiedPaths()
            runner.loadModel(paths)
            _statusFlow.value = ModelStatus.Ready
            Result.success(Unit)
        } catch (e: Exception) {
            _statusFlow.value = ModelStatus.Error(e.message ?: "Failed to load ExecuTorch model")
            Result.failure(e)
        }
    }

    override fun generateReply(conversationHistory: List<ChatMessage>): Flow<GenerationEvent> {
        // Prune older conversation turns to fit within the compiled KV cache budget
        val safeHistory = ContextBudgeter.pruneHistoryToFit(conversationHistory)
        val formattedPrompt = PromptFormatter.formatChatPrompt(safeHistory)

        return runner.generateStream(
            prompt = formattedPrompt,
            maxNewTokens = ContextBudgeter.MAX_NEW_TOKENS,
            temperature = 0.7f
        )
    }

    override fun stopGeneration() {
        runner.stop()
    }

    override fun release() {
        runner.close()
        _statusFlow.value = ModelStatus.Uninitialized
    }
}
