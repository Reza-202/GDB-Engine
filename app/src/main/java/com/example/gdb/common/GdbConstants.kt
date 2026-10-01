package com.example.gdb.common

/**
 * Core constants defined in GDB-SPEC v3.50.
 */
object GdbConstants {
    const val SPEC_VERSION = "v3.50 — MASTER COMPLETE"
    const val ENGINE_NAME = "GDB-SPEC Engine"
    const val CURRENT_CERTIFIED_BASELINE = "BASELINE-GDB-v3.50-BOOTSTRAP-REV1"

    // Storage geometry
    const val DEFAULT_PAGE_SIZE = 4096 // 4 KB pages
    const val PAGE_HEADER_SIZE = 32 // Generation, LSN, type, payload length, checksum
    const val PAGE_MAGIC: Long = 0x4744425F50414745L // "GDB_PAGE"
    const val WAL_MAGIC: Long = 0x4744425F57414C21L  // "GDB_WAL!"

    // Memory Governor defaults
    const val DEFAULT_MAX_MEMORY_BUDGET_BYTES: Long = 64 * 1024 * 1024L // 64 MB bounded test budget
    const val ELEVATED_PRESSURE_THRESHOLD = 0.70
    const val HIGH_PRESSURE_THRESHOLD = 0.85
    const val CRITICAL_PRESSURE_THRESHOLD = 0.95
    const val OOM_IMMINENT_THRESHOLD = 0.99

    // Async I/O constraints
    const val DEFAULT_IO_QUEUE_CAPACITY = 128
    const val MAX_CONCURRENT_IO_OPERATIONS = 8
}
