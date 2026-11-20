package com.example.execution

import kotlinx.coroutines.delay

class ErrorRecoveryManager {
    suspend fun <T> executeWithRetry(
        maxRetries: Int,
        isRetryable: (T) -> Boolean,
        onRetry: suspend (attempt: Int) -> Unit = {},
        block: suspend (attempt: Int) -> T
    ): Pair<T, Int> {
        var attempt = 0
        var lastResult = block(0)
        while (attempt < maxRetries && isRetryable(lastResult)) {
            attempt++
            onRetry(attempt)
            delay(500L * attempt)
            lastResult = block(attempt)
        }
        return lastResult to attempt
    }
}
