package com.example.gdb.memory

/**
 * Allocation Classes defined in GDB-SPEC Section 16.
 */
enum class MemoryAllocationClass {
    METADATA,
    PAGE_CACHE,
    EXECUTION,
    TRANSACTION,
    INDEX_BUILD,
    GPU_EXECUTION,
    PREFETCH,
    SPILL_BUFFER,
    RECOVERY
}
