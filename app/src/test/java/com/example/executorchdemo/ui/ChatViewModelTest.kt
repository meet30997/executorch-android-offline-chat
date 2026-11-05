package com.example.executorchdemo.ui

import com.example.executorchdemo.data.GenerationEvent
import com.example.executorchdemo.domain.model.ChatMessage
import com.example.executorchdemo.domain.model.MessageRole
import com.example.executorchdemo.domain.model.ModelStatus
import com.example.executorchdemo.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeChatRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeChatRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_whenMissingFiles_updatesStatusToMissingFiles() = runTest {
        val missingFiles = listOf("model.pte", "tokenizer.model")
        fakeRepository.checkAvailabilityResult = ModelStatus.MissingFiles(missingFiles, "/fake/dir")

        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        val status = viewModel.uiState.value.modelStatus
        assertTrue(status is ModelStatus.MissingFiles)
        assertEquals(missingFiles, (status as ModelStatus.MissingFiles).missingFiles)
    }

    @Test
    fun init_whenModelReady_initializesSuccessfully() = runTest {
        fakeRepository.checkAvailabilityResult = ModelStatus.Ready
        fakeRepository.initializeResult = Result.success(Unit)

        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        assertEquals(ModelStatus.Ready, viewModel.uiState.value.modelStatus)
    }

    @Test
    fun init_whenModelFailsToInitialize_setsErrorStatus() = runTest {
        fakeRepository.checkAvailabilityResult = ModelStatus.Uninitialized
        fakeRepository.initializeResult = Result.failure(RuntimeException("Corrupted PTE file"))

        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        val status = viewModel.uiState.value.modelStatus
        assertTrue(status is ModelStatus.Error)
        assertEquals("Corrupted PTE file", (status as ModelStatus.Error).message)
        assertEquals("Corrupted PTE file", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onInputTextChanged_updatesStateInputText() {
        val viewModel = ChatViewModel(fakeRepository)
        viewModel.onInputTextChanged("Hello on-device AI")
        assertEquals("Hello on-device AI", viewModel.uiState.value.inputText)
    }

    @Test
    fun sendMessage_whenInputIsBlank_doesNothing() = runTest {
        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.onInputTextChanged("   ")
        viewModel.sendMessage()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun sendMessage_whenAlreadyGenerating_doesNotSendDuplicate() = runTest {
        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.onInputTextChanged("First message")
        viewModel.sendMessage()

        viewModel.onInputTextChanged("Second message while busy")
        viewModel.sendMessage()

        // Messages should only contain the 1st prompt + assistant placeholder
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals("First message", viewModel.uiState.value.messages[0].content)
    }

    @Test
    fun sendMessage_streamsTokens_updatesMetrics_andCompletes() = runTest {
        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.onInputTextChanged("Explain Quantum Computing")
        viewModel.sendMessage()

        // 1. Check immediate UI state updates
        assertEquals("", viewModel.uiState.value.inputText)
        assertTrue(viewModel.uiState.value.isGenerating)
        assertEquals(2, viewModel.uiState.value.messages.size)

        val userMsg = viewModel.uiState.value.messages[0]
        val assistantMsg = viewModel.uiState.value.messages[1]

        assertEquals(MessageRole.USER, userMsg.role)
        assertEquals("Explain Quantum Computing", userMsg.content)
        assertEquals(MessageRole.ASSISTANT, assistantMsg.role)
        assertTrue(assistantMsg.isStreaming)

        // Advance dispatcher to allow the coroutine to start collecting repository flow
        advanceUntilIdle()

        // Verify history passed to repository only includes user message (placeholder excluded)
        assertNotNull(fakeRepository.lastConversationHistory)
        assertEquals(1, fakeRepository.lastConversationHistory?.size)
        assertEquals("Explain Quantum Computing", fakeRepository.lastConversationHistory?.first()?.content)

        // 2. Stream tokens
        fakeRepository.generationEventsFlow.emit(GenerationEvent.Token("Quantum "))
        advanceUntilIdle()
        assertEquals("Quantum ", viewModel.uiState.value.messages[1].content)

        fakeRepository.generationEventsFlow.emit(GenerationEvent.Token("physics is "))
        advanceUntilIdle()
        assertEquals("Quantum physics is ", viewModel.uiState.value.messages[1].content)

        // 3. Emit stats
        fakeRepository.generationEventsFlow.emit(
            GenerationEvent.Stats(
                promptTokens = 12,
                generatedTokens = 4,
                tokensPerSec = 18.5,
                decodeDurationMs = 216L,
                timeToFirstTokenMs = 45L
            )
        )
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.currentMetrics)
        assertEquals(18.5, viewModel.uiState.value.currentMetrics?.tokensPerSec ?: 0.0, 0.01)

        // 4. Conclude generation
        fakeRepository.generationEventsFlow.emit(GenerationEvent.Complete)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.messages[1].isStreaming)
        assertEquals("Quantum physics is ", viewModel.uiState.value.messages[1].content)
        assertEquals(18.5, viewModel.uiState.value.messages[1].metrics?.tokensPerSec ?: 0.0, 0.01)
    }

    @Test
    fun sendMessage_whenErrorOccurs_recordsErrorAndFinalizesMessage() = runTest {
        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.onInputTextChanged("Trigger crash")
        viewModel.sendMessage()
        advanceUntilIdle()

        fakeRepository.generationEventsFlow.emit(GenerationEvent.Error("KV cache full"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals("KV cache full", viewModel.uiState.value.errorMessage)
        val assistantMsg = viewModel.uiState.value.messages[1]
        assertFalse(assistantMsg.isStreaming)
        assertTrue(assistantMsg.content.contains("Generation failed: KV cache full"))
    }

    @Test
    fun stopGeneration_haltsGenerationAndResetsStreamingState() = runTest {
        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.onInputTextChanged("Long running prompt")
        viewModel.sendMessage()

        assertTrue(viewModel.uiState.value.isGenerating)

        viewModel.stopGeneration()
        advanceUntilIdle()

        assertTrue(fakeRepository.stopGenerationCalled)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.messages[1].isStreaming)
    }

    @Test
    fun clearChat_clearsAllMessagesAndResetsState() = runTest {
        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.onInputTextChanged("Hello world")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.messages.size)

        viewModel.clearChat()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun sendSuggestedPrompt_setsInputAndSendsImmediately() = runTest {
        val viewModel = ChatViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.sendSuggestedPrompt("Suggested test prompt")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals("Suggested test prompt", viewModel.uiState.value.messages[0].content)
        assertTrue(viewModel.uiState.value.isGenerating)
    }

    private class FakeChatRepository : ChatRepository {
        var checkAvailabilityResult: ModelStatus = ModelStatus.Ready
        var initializeResult: Result<Unit> = Result.success(Unit)
        val generationEventsFlow = MutableSharedFlow<GenerationEvent>(replay = 10, extraBufferCapacity = 10)
        var stopGenerationCalled = false
        var releaseCalled = false
        var lastConversationHistory: List<ChatMessage>? = null

        private val _statusFlow = MutableStateFlow<ModelStatus>(ModelStatus.Uninitialized)
        override fun observeModelStatus(): Flow<ModelStatus> = _statusFlow.asStateFlow()

        override suspend fun checkModelAvailability(): ModelStatus = checkAvailabilityResult

        override suspend fun initializeModel(): Result<Unit> = initializeResult

        override fun generateReply(conversationHistory: List<ChatMessage>): Flow<GenerationEvent> {
            lastConversationHistory = conversationHistory
            return generationEventsFlow
        }

        override fun stopGeneration() {
            stopGenerationCalled = true
        }

        override fun release() {
            releaseCalled = true
        }
    }
}
