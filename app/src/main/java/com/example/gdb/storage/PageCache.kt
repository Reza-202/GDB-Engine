package com.example.gdb.storage

import com.example.gdb.common.GdbErrorCode
import com.example.gdb.common.GdbResult
import com.example.gdb.memory.MemoryAllocationClass
import com.example.gdb.memory.MemoryGovernor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe Page Cache with Generation Validation and Eviction.
 * Coordinates with [MemoryGovernor] for bounded memory consumption.
 */
class PageCache(
    val memoryGovernor: MemoryGovernor,
    val maxPages: Int = 1024
) {
    private val lock = ReentrantReadWriteLock()
    private val cache = LinkedHashMap<PageId, Page>(maxPages, 0.75f, true)
    private val hitCount = java.util.concurrent.atomic.AtomicLong(0)
    private val missCount = java.util.concurrent.atomic.AtomicLong(0)

    init {
        memoryGovernor.registerEvictionHandler {
            evictPages(maxPages / 4)
        }
    }

    fun get(pageId: PageId, expectedGeneration: Long? = null): GdbResult<Page> = lock.read {
        val page = cache[pageId]
        if (page != null) {
            if (expectedGeneration != null && page.generation != expectedGeneration) {
                return GdbResult.Failure(
                    GdbErrorCode.GENERATION_MISMATCH,
                    "Page generation mismatch for $pageId: expected=$expectedGeneration, actual=${page.generation}"
                )
            }
            hitCount.incrementAndGet()
            GdbResult.Success(page)
        } else {
            missCount.incrementAndGet()
            GdbResult.Failure(GdbErrorCode.PAGE_NOT_FOUND, "Page $pageId not in cache")
        }
    }

    fun put(page: Page): GdbResult<Unit> = lock.write {
        val existing = cache[page.pageId]
        if (existing == null) {
            // Allocate memory budget
            val allocRes = memoryGovernor.allocate(MemoryAllocationClass.PAGE_CACHE, page.pageSize.toLong())
            if (allocRes is GdbResult.Failure) {
                // Try evicting one
                val freed = evictInternal(1)
                if (freed == 0L) {
                    return allocRes
                }
                val retryRes = memoryGovernor.allocate(MemoryAllocationClass.PAGE_CACHE, page.pageSize.toLong())
                if (retryRes is GdbResult.Failure) return retryRes
            }
        }
        cache[page.pageId] = page
        if (cache.size > maxPages) {
            evictInternal(cache.size - maxPages)
        }
        GdbResult.Success(Unit)
    }

    fun evictPages(count: Int): Long = lock.write {
        evictInternal(count)
    }

    private fun evictInternal(count: Int): Long {
        var bytesFreed = 0L
        val iterator = cache.entries.iterator()
        var evicted = 0
        while (iterator.hasNext() && evicted < count) {
            val entry = iterator.next()
            iterator.remove()
            bytesFreed += entry.value.pageSize
            memoryGovernor.release(MemoryAllocationClass.PAGE_CACHE, entry.value.pageSize.toLong())
            evicted++
        }
        return bytesFreed
    }

    fun getHitCount(): Long = hitCount.get()
    fun getMissCount(): Long = missCount.get()
    fun getCachedPageCount(): Int = lock.read { cache.size }
    fun clear() = lock.write {
        for (page in cache.values) {
            memoryGovernor.release(MemoryAllocationClass.PAGE_CACHE, page.pageSize.toLong())
        }
        cache.clear()
    }
}
