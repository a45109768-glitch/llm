package com.pocketai.local

import com.pocketai.brain.ChatMessage
import com.pocketai.brain.MessageRole
import com.pocketai.local.engines.ChatTemplateFormatter
import com.pocketai.local.engines.ChatTemplateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTemplateFormatterTest {

    @Test
    fun testDetectBonsaiTemplate() {
        val detected = ChatTemplateFormatter.detectTemplate(
            architecture = "bonsai",
            modelName = "Bonsai 1.7B"
        )
        assertEquals(ChatTemplateType.CHAT_ML, detected)
    }

    @Test
    fun testDetectQwenTemplate() {
        val detected = ChatTemplateFormatter.detectTemplate(
            architecture = "qwen2",
            modelName = "Qwen 2.5 0.5B"
        )
        assertEquals(ChatTemplateType.CHAT_ML, detected)
    }

    @Test
    fun testDetectLlama3Template() {
        val detected = ChatTemplateFormatter.detectTemplate(
            architecture = "llama3",
            modelName = "Llama 3.2 1B Instruct"
        )
        assertEquals(ChatTemplateType.LLAMA3, detected)
    }

    @Test
    fun testFormatChatMLPrompt() {
        val systemPrompt = "You are an AI assistant."
        val history = listOf(
            ChatMessage(
                role = MessageRole.USER,
                content = "Hi there"
            ),
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Hello! How can I help?"
            )
        )
        val prompt = "Tell me about offline inference."

        val formatted = ChatTemplateFormatter.format(
            templateType = ChatTemplateType.CHAT_ML,
            systemPrompt = systemPrompt,
            contextHistory = history,
            userPrompt = prompt
        )

        assertTrue(formatted.contains("<|im_start|>system\nYou are an AI assistant.<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>user\nHi there<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>assistant\nHello! How can I help?<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>user\nTell me about offline inference.<|im_end|>"))
        assertTrue(formatted.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun testGlobalStopTokensContainChatMLAndLlama3Tokens() {
        assertTrue(ChatTemplateFormatter.GLOBAL_STOP_TOKENS.contains("<|im_end|>"))
        assertTrue(ChatTemplateFormatter.GLOBAL_STOP_TOKENS.contains("<|eot_id|>"))
        assertTrue(ChatTemplateFormatter.GLOBAL_STOP_TOKENS.contains("</s>"))
    }
}
