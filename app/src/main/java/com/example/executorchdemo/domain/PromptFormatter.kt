package com.example.executorchdemo.domain

import com.example.executorchdemo.domain.model.ChatMessage
import com.example.executorchdemo.domain.model.MessageRole

object PromptFormatter {

    private const val BOS = "<|begin_of_text|>"
    private const val START_HEADER = "<|start_header_id|>"
    private const val END_HEADER = "<|end_header_id|>\n\n"
    private const val EOT = "<|eot_id|>"

    const val DEFAULT_SYSTEM_PROMPT =
        "You are a helpful, concise AI assistant running fully offline on an Android device using PyTorch ExecuTorch."

    /**
     * Formats a linear conversational history into Llama 3.2 chat syntax.
     *
     * @param messages Complete historical transcript.
     * @param systemPrompt System instructions.
     * @return Fully structured prompt string ready for ExecuTorch inference.
     */
    fun formatChatPrompt(
        messages: List<ChatMessage>,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): String {
        val builder = StringBuilder()

        // 1. Begin of sequence
        builder.append(BOS)

        // 2. System header and content
        if (systemPrompt.isNotBlank()) {
            builder.append(START_HEADER)
                .append("system")
                .append(END_HEADER)
                .append(systemPrompt.trim())
                .append(EOT)
        }

        // 3. Conversation turns
        for (message in messages) {
            val roleTag = when (message.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }

            builder.append(START_HEADER)
                .append(roleTag)
                .append(END_HEADER)
                .append(message.content.trim())
                .append(EOT)
        }

        // 4. Prime the assistant generation turn
        builder.append(START_HEADER)
            .append("assistant")
            .append(END_HEADER)

        return builder.toString()
    }
}
