package com.example.executorchdemo.domain

import com.example.executorchdemo.domain.model.ChatMessage
import com.example.executorchdemo.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptFormatterTest {

    @Test
    fun `formatChatPrompt includes BOS and default system prompt and assistant priming`() {
        val messages = listOf(
            ChatMessage(role = MessageRole.USER, content = "Hello Llama!")
        )

        val prompt = PromptFormatter.formatChatPrompt(messages)

        val expected = "<|begin_of_text|>" +
                "<|start_header_id|>system<|end_header_id|>\n\n" +
                PromptFormatter.DEFAULT_SYSTEM_PROMPT +
                "<|eot_id|>" +
                "<|start_header_id|>user<|end_header_id|>\n\n" +
                "Hello Llama!" +
                "<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>\n\n"

        assertEquals(expected, prompt)
    }

    @Test
    fun `formatChatPrompt handles custom system prompt`() {
        val customSystem = "You are a specialized code assistant."
        val messages = listOf(
            ChatMessage(role = MessageRole.USER, content = "Write Kotlin code")
        )

        val prompt = PromptFormatter.formatChatPrompt(messages, systemPrompt = customSystem)

        assertTrue(prompt.startsWith("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$customSystem<|eot_id|>"))
        assertTrue(prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun `formatChatPrompt handles empty system prompt`() {
        val messages = listOf(
            ChatMessage(role = MessageRole.USER, content = "Hello")
        )

        val prompt = PromptFormatter.formatChatPrompt(messages, systemPrompt = "")

        val expected = "<|begin_of_text|>" +
                "<|start_header_id|>user<|end_header_id|>\n\n" +
                "Hello" +
                "<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>\n\n"

        assertEquals(expected, prompt)
    }

    @Test
    fun `formatChatPrompt formats multi-turn dialogue in correct sequence`() {
        val messages = listOf(
            ChatMessage(role = MessageRole.USER, content = "What is 2+2?"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "2+2 equals 4."),
            ChatMessage(role = MessageRole.USER, content = "And multiplied by 3?")
        )

        val prompt = PromptFormatter.formatChatPrompt(messages, systemPrompt = "")

        val expected = "<|begin_of_text|>" +
                "<|start_header_id|>user<|end_header_id|>\n\n" +
                "What is 2+2?<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>\n\n" +
                "2+2 equals 4.<|eot_id|>" +
                "<|start_header_id|>user<|end_header_id|>\n\n" +
                "And multiplied by 3?<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>\n\n"

        assertEquals(expected, prompt)
    }
}
