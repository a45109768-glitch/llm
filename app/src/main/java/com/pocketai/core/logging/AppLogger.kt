package com.pocketai.core.logging

import android.util.Log

object AppLogger {
    private const val TAG = "PocketAI"

    // Patterns that match API keys or auth tokens to prevent accidental logging
    private val SENSITIVE_PATTERNS = listOf(
        Regex("""(?i)(bearer\s+)[a-zA-Z0-9_\-\.]{8,}"""),
        Regex("""(?i)(api[_-]?key["':\s=]+)[a-zA-Z0-9_\-\.]{8,}"""),
        Regex("""(?i)(sk-[a-zA-Z0-9]{12,})"""),
        Regex("""(?i)(token["':\s=]+)[a-zA-Z0-9_\-\.]{8,}""")
    )

    fun d(tag: String = TAG, message: String) {
        Log.d(tag, sanitize(message))
    }

    fun i(tag: String = TAG, message: String) {
        Log.i(tag, sanitize(message))
    }

    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        Log.w(tag, sanitize(message), throwable)
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        Log.e(tag, sanitize(message), throwable)
    }

    fun sanitize(input: String): String {
        var sanitized = input
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = pattern.replace(sanitized) { matchResult ->
                val full = matchResult.value
                if (full.length > 8) {
                    full.substring(0, 4) + "••••••••" + full.substring(full.length - 4)
                } else {
                    "••••••••"
                }
            }
        }
        return sanitized
    }
}
