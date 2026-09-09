package com.pocketai.local.engines

import com.pocketai.brain.ChatMessage

enum class ChatTemplateType {
    CHAT_ML,       // Bonsai, Qwen, Qwen2, Yi, Smollm
    LLAMA3,        // Llama 3, Llama 3.1, Llama 3.2
    GEMMA,         // Gemma, Gemma 2
    MISTRAL,       // Mistral, Mixtral
    PHI3,          // Phi-3, Phi-3.5
    PLAIN          // Fallback / Alpaca / Vicuna
}

object ChatTemplateFormatter {

    val GLOBAL_STOP_TOKENS = listOf(
        "<|im_end|>",
        "<|im_start|>",
        "<|endoftext|>",
        "<|eot_id|>",
        "<|start_header_id|>",
        "</s>",
        "<end_of_turn>",
        "<start_of_turn>",
        "<|end|>",
        "User:",
        "\nUser:",
        "Human:",
        "\nHuman:"
    )

    fun detectTemplate(architecture: String?, modelName: String): ChatTemplateType {
        val arch = (architecture ?: "").lowercase()
        val name = modelName.lowercase()

        return when {
            arch.contains("bonsai") || name.contains("bonsai") -> ChatTemplateType.CHAT_ML
            arch.contains("qwen") || name.contains("qwen") -> ChatTemplateType.CHAT_ML
            arch.contains("chatml") || name.contains("chatml") -> ChatTemplateType.CHAT_ML
            arch.contains("llama3") || name.contains("llama-3") || name.contains("llama_3") || name.contains("llama 3") -> ChatTemplateType.LLAMA3
            arch.contains("gemma") || name.contains("gemma") -> ChatTemplateType.GEMMA
            arch.contains("mistral") || name.contains("mistral") || arch.contains("mixtral") -> ChatTemplateType.MISTRAL
            arch.contains("phi3") || name.contains("phi-3") || name.contains("phi3") -> ChatTemplateType.PHI3
            // Bonsai 1.7B and most modern compact SLMs (0.5B - 3B) adopt ChatML:
            else -> ChatTemplateType.CHAT_ML
        }
    }

    fun format(
        templateType: ChatTemplateType,
        systemPrompt: String,
        contextHistory: List<ChatMessage>,
        userPrompt: String
    ): String {
        return when (templateType) {
            ChatTemplateType.CHAT_ML -> formatChatML(systemPrompt, contextHistory, userPrompt)
            ChatTemplateType.LLAMA3 -> formatLlama3(systemPrompt, contextHistory, userPrompt)
            ChatTemplateType.GEMMA -> formatGemma(systemPrompt, contextHistory, userPrompt)
            ChatTemplateType.MISTRAL -> formatMistral(systemPrompt, contextHistory, userPrompt)
            ChatTemplateType.PHI3 -> formatPhi3(systemPrompt, contextHistory, userPrompt)
            ChatTemplateType.PLAIN -> formatPlain(systemPrompt, contextHistory, userPrompt)
        }
    }

    private fun formatChatML(
        systemPrompt: String,
        contextHistory: List<ChatMessage>,
        userPrompt: String
    ): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(systemPrompt.trim()).append("<|im_end|>\n")
        }
        for (msg in contextHistory) {
            val role = if (msg.isUser) "user" else "assistant"
            sb.append("<|im_start|>").append(role).append("\n").append(msg.content.trim()).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(userPrompt.trim()).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun formatLlama3(
        systemPrompt: String,
        contextHistory: List<ChatMessage>,
        userPrompt: String
    ): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        if (systemPrompt.isNotBlank()) {
            sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                .append(systemPrompt.trim())
                .append("<|eot_id|>")
        }
        for (msg in contextHistory) {
            val role = if (msg.isUser) "user" else "assistant"
            sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                .append(msg.content.trim())
                .append("<|eot_id|>")
        }
        sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
            .append(userPrompt.trim())
            .append("<|eot_id|>")
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun formatGemma(
        systemPrompt: String,
        contextHistory: List<ChatMessage>,
        userPrompt: String
    ): String {
        val sb = StringBuilder()
        val combinedFirst = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n" else ""
        var isFirst = true
        for (msg in contextHistory) {
            val role = if (msg.isUser) "user" else "model"
            sb.append("<start_of_turn>").append(role).append("\n")
            if (isFirst && msg.isUser && combinedFirst.isNotBlank()) {
                sb.append(combinedFirst)
                isFirst = false
            }
            sb.append(msg.content.trim()).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>user\n")
        if (isFirst && combinedFirst.isNotBlank()) {
            sb.append(combinedFirst)
        }
        sb.append(userPrompt.trim()).append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun formatMistral(
        systemPrompt: String,
        contextHistory: List<ChatMessage>,
        userPrompt: String
    ): String {
        val sb = StringBuilder()
        val sys = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n" else ""
        for (msg in contextHistory) {
            if (msg.isUser) {
                sb.append("[INST] ").append(msg.content.trim()).append(" [/INST] ")
            } else {
                sb.append(msg.content.trim()).append(" ")
            }
        }
        sb.append("[INST] ").append(sys).append(userPrompt.trim()).append(" [/INST]")
        return sb.toString()
    }

    private fun formatPhi3(
        systemPrompt: String,
        contextHistory: List<ChatMessage>,
        userPrompt: String
    ): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("<|system|>\n").append(systemPrompt.trim()).append("<|end|>\n")
        }
        for (msg in contextHistory) {
            val role = if (msg.isUser) "user" else "assistant"
            sb.append("<|").append(role).append("|>\n").append(msg.content.trim()).append("<|end|>\n")
        }
        sb.append("<|user|>\n").append(userPrompt.trim()).append("<|end|>\n")
        sb.append("<|assistant|>\n")
        return sb.toString()
    }

    private fun formatPlain(
        systemPrompt: String,
        contextHistory: List<ChatMessage>,
        userPrompt: String
    ): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("System: ").append(systemPrompt.trim()).append("\n\n")
        }
        for (msg in contextHistory) {
            val role = if (msg.isUser) "User" else "Assistant"
            sb.append(role).append(": ").append(msg.content.trim()).append("\n")
        }
        sb.append("User: ").append(userPrompt.trim()).append("\nAssistant: ")
        return sb.toString()
    }
}
