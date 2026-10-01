package com.example.gdb.common

/**
 * Strict explicit Result type for GDB-SPEC.
 * Prohibits silent failures or unhandled exceptions.
 */
sealed class GdbResult<out T> {
    data class Success<out T>(val value: T) : GdbResult<T>()
    data class Failure(
        val code: GdbErrorCode,
        val message: String,
        val cause: Throwable? = null
    ) : GdbResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw GdbException(code, message, cause)
    }

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
}

enum class GdbErrorCode {
    OUT_OF_MEMORY,
    PAGE_CORRUPTED,
    PAGE_NOT_FOUND,
    CHECKSUM_MISMATCH,
    GENERATION_MISMATCH,
    WAL_CORRUPTED,
    WAL_FLUSH_FAILED,
    IO_ERROR,
    QUEUE_FULL,
    SECURITY_VIOLATION,
    INVALID_ARGUMENT,
    UNSUPPORTED_OPERATION,
    VERIFICATION_FAILED
}

class GdbException(
    val code: GdbErrorCode,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException("[$code] $message", cause)
