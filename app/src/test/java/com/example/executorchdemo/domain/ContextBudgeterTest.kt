package com.example.executorchdemo.domain

import com.example.executorchdemo.domain.model.ChatMessage
import com.example.executorchdemo.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgeterTest {

    @Test
    fun `calculateSafeBudget clamps to MAX_CONTEXT_TOKENS`() {
        // Exceeding capacity should clamp to 2048
        val budget = ContextBudgeter.calculateSafeBudget(promptLengthChars = 3000, maxNewTokens = 512)
        assertEquals(ContextBudgeter.MAX_CONTEXT_TOKENS, budget)
        assertEquals(2048, budget)
    }

    @Test
    fun `calculateSafeBudget respects minimum prompt token estimate`() {
        // Short prompt (10 chars) should floor at 64 tokens + 512 = 576
        val budget = ContextBudgeter.calculateSafeBudget(promptLengthChars = 10, maxNewTokens = 512)
        assertEquals(64 + 512, budget)
    }

    @Test
    fun `pruneHistoryToFit returns empty list when given empty list`() {
        val result = ContextBudgeter.pruneHistoryToFit(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pruneHistoryToFit keeps all messages when within budget`() {
        val messages = listOf(
            ChatMessage(role = MessageRole.USER, content = "Hello"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "Hi there!"),
            ChatMessage(role = MessageRole.USER, content = "How are you?")
        )

        val result = ContextBudgeter.pruneHistoryToFit(messages, maxBudgetTokens = 1536)
        assertEquals(3, result.size)
        assertEquals("Hello", result[0].content)
        assertEquals("Hi there!", result[1].content)
        assertEquals("How are you?", result[2].content)
    }

    @Test
    fun `pruneHistoryToFit drops oldest messages when budget exceeded`() {
        val msg1 = ChatMessage(role = MessageRole.USER, content = "A".repeat(100))
        val msg2 = ChatMessage(role = MessageRole.ASSISTANT, content = "B".repeat(100))
        val msg3 = ChatMessage(role = MessageRole.USER, content = "C".repeat(100))

        // Total cost per message = 100 + 32 = 132
        // If maxBudgetTokens = 300:
        // Traversing backwards:
        // msg3: 132 (accum = 132) -> kept
        // msg2: 132 (accum = 264) -> kept
        // msg1: 132 (accum 264 + 132 = 396 > 300) -> pruned!
        val result = ContextBudgeter.pruneHistoryToFit(listOf(msg1, msg2, msg3), maxBudgetTokens = 300)

        assertEquals(2, result.size)
        assertEquals(msg2.content, result[0].content)
        assertEquals(msg3.content, result[1].content)
    }

    @Test
    fun `pruneHistoryToFit keeps at least the most recent message even if it alone exceeds budget`() {
        val msg1 = ChatMessage(role = MessageRole.USER, content = "Old message")
        val msg2 = ChatMessage(role = MessageRole.USER, content = "X".repeat(500))

        // maxBudgetTokens = 200, msg2 alone is 532 tokens
        val result = ContextBudgeter.pruneHistoryToFit(listOf(msg1, msg2), maxBudgetTokens = 200)

        assertEquals(1, result.size)
        assertEquals(msg2.content, result[0].content)
    }
}
