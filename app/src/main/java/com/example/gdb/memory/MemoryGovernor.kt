package com.example.gdb.memory

import com.example.gdb.common.GdbConstants
import com.example.gdb.common.GdbErrorCode
import com.example.gdb.common.GdbResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Global Memory Governor implementing GDB-SPEC Section 16.
 * Enforces bounded memory limits across all allocation classes,
 * triggers eviction on memory pressure, and guarantees zero silent corruptions under OOM.
 */
class MemoryGovernor(
    val maxBudgetBytes: Long = GdbConstants.DEFAULT_MAX_MEMORY_BUDGET_BYTES
) {
    private val classUsage = ConcurrentHashMap<MemoryAllocationClass, AtomicLong>().apply {
        MemoryAllocationClass.entries.forEach { this[it] = AtomicLong(0L) }
    }
    private val totalUsedBytes = AtomicLong(0L)
    private val pressureListeners = CopyOnWriteArrayList<(MemoryPressureLevel) -> Unit>()
    private val evictionHandlers = CopyOnWriteArrayList<() -> Long>()

    fun registerPressureListener(listener: (MemoryPressureLevel) -> Unit) {
        pressureListeners.add(listener)
    }

    fun registerEvictionHandler(handler: () -> Long) {
        evictionHandlers.add(handler)
    }

    /**
     * Request memory allocation for a specific class.
     * Follows GDB-SPEC workflow: Budget Check -> Allocate -> Evict/Spill if needed.
     */
    @Synchronized
    fun allocate(allocationClass: MemoryAllocationClass, bytes: Long): GdbResult<Unit> {
        if (bytes <= 0) return GdbResult.Success(Unit)

        val currentTotal = totalUsedBytes.get()
        if (currentTotal + bytes > maxBudgetBytes) {
            // Attempt eviction
            var freed = 0L
            for (handler in evictionHandlers) {
                freed += handler.invoke()
                if (totalUsedBytes.get() + bytes <= maxBudgetBytes) break
            }

            if (totalUsedBytes.get() + bytes > maxBudgetBytes) {
                return GdbResult.Failure(
                    GdbErrorCode.OUT_OF_MEMORY,
                    "Memory budget exceeded: current=${totalUsedBytes.get()} + requested=$bytes > max=$maxBudgetBytes for class=$allocationClass"
                )
            }
        }

        totalUsedBytes.addAndGet(bytes)
        classUsage[allocationClass]?.addAndGet(bytes)
        checkAndNotifyPressure()
        return GdbResult.Success(Unit)
    }

    /**
     * Release allocated memory.
     */
    fun release(allocationClass: MemoryAllocationClass, bytes: Long) {
        if (bytes <= 0) return
        val currentClass = classUsage[allocationClass]?.get() ?: 0L
        val toSubClass = minOf(currentClass, bytes)
        classUsage[allocationClass]?.addAndGet(-toSubClass)

        val currentTotal = totalUsedBytes.get()
        val toSubTotal = minOf(currentTotal, bytes)
        totalUsedBytes.addAndGet(-toSubTotal)

        checkAndNotifyPressure()
    }

    fun getUsage(allocationClass: MemoryAllocationClass): Long {
        return classUsage[allocationClass]?.get() ?: 0L
    }

    fun getTotalUsage(): Long = totalUsedBytes.get()

    fun getPressureLevel(): MemoryPressureLevel {
        val ratio = totalUsedBytes.get().toDouble() / maxBudgetBytes.toDouble()
        return when {
            ratio >= GdbConstants.OOM_IMMINENT_THRESHOLD -> MemoryPressureLevel.OOM_IMMINENT
            ratio >= GdbConstants.CRITICAL_PRESSURE_THRESHOLD -> MemoryPressureLevel.CRITICAL
            ratio >= GdbConstants.HIGH_PRESSURE_THRESHOLD -> MemoryPressureLevel.HIGH
            ratio >= GdbConstants.ELEVATED_PRESSURE_THRESHOLD -> MemoryPressureLevel.ELEVATED
            else -> MemoryPressureLevel.NORMAL
        }
    }

    private fun checkAndNotifyPressure() {
        val level = getPressureLevel()
        if (level != MemoryPressureLevel.NORMAL) {
            for (listener in pressureListeners) {
                try {
                    listener(level)
                } catch (_: Exception) {}
            }
        }
    }

    fun reset() {
        totalUsedBytes.set(0L)
        classUsage.values.forEach { it.set(0L) }
    }
}
