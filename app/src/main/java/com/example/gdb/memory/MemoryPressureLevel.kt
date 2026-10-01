package com.example.gdb.memory

/**
 * Memory Pressure Tiers defined in GDB-SPEC Section 16.
 */
enum class MemoryPressureLevel {
    NORMAL,
    ELEVATED,
    HIGH,
    CRITICAL,
    OOM_IMMINENT
}
