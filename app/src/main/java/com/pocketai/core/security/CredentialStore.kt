package com.pocketai.core.security

object CredentialStore {

    /**
     * Safely mask an API key for UI display (e.g. sk-••••abcd).
     */
    fun maskKey(key: String): String {
        if (key.isBlank()) return "Not configured"
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }

    /**
     * Basic sanitization of API key input (removing stray spaces, tabs, newlines).
     */
    fun sanitizeKey(key: String): String {
        return key.trim()
    }

    /**
     * Generate a secure random token for robot auth pairing.
     */
    fun generateRobotToken(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..32)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
