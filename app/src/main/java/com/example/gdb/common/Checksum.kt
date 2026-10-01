package com.example.gdb.common

import java.util.zip.CRC32

/**
 * High-performance integrity checksum calculation for GDB-SPEC.
 */
object Checksum {
    /**
     * Compute CRC32 checksum for a byte array slice.
     */
    fun compute(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) {
            "Checksum slice out of bounds: offset=$offset length=$length size=${bytes.size}"
        }
        val crc = CRC32()
        crc.update(bytes, offset, length)
        return crc.value
    }
}
