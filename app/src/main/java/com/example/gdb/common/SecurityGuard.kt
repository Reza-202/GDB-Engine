package com.example.gdb.common

import java.io.File

/**
 * Security controls mandated by GDB-SPEC v3.50 Section 14.
 */
object SecurityGuard {
    /**
     * Prevent path traversal attacks by validating that target file resides within allowed root.
     */
    fun validatePath(baseDirectory: File, userPath: String): GdbResult<File> {
        return try {
            val resolved = File(baseDirectory, userPath).canonicalFile
            val baseCanonical = baseDirectory.canonicalFile
            if (!resolved.path.startsWith(baseCanonical.path)) {
                GdbResult.Failure(
                    GdbErrorCode.SECURITY_VIOLATION,
                    "Path traversal attempt detected: '$userPath' escapes root '${baseCanonical.path}'"
                )
            } else {
                GdbResult.Success(resolved)
            }
        } catch (e: Exception) {
            GdbResult.Failure(GdbErrorCode.SECURITY_VIOLATION, "Path validation failed: ${e.message}", e)
        }
    }

    /**
     * Checks integer addition overflow.
     */
    fun safeAdd(a: Long, b: Long): GdbResult<Long> {
        val result = a + b
        // If signs are same and result sign differs, overflow occurred
        return if (((a xor result) and (b xor result)) < 0) {
            GdbResult.Failure(GdbErrorCode.SECURITY_VIOLATION, "Integer overflow in addition: $a + $b")
        } else {
            GdbResult.Success(result)
        }
    }

    /**
     * Checks bounds for byte slices.
     */
    fun checkBounds(offset: Int, length: Int, capacity: Int): GdbResult<Unit> {
        if (offset < 0 || length < 0 || offset > capacity - length) {
            return GdbResult.Failure(
                GdbErrorCode.SECURITY_VIOLATION,
                "Out of bounds: offset=$offset, length=$length, capacity=$capacity"
            )
        }
        return GdbResult.Success(Unit)
    }
}
