package com.example.gdb.storage

import com.example.gdb.common.GdbConstants
import com.example.gdb.common.GdbErrorCode
import com.example.gdb.common.GdbResult
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

enum class IoPriority {
    CRITICAL_WAL,
    HIGH_PAGE_READ,
    MEDIUM_DIRTY_FLUSH,
    LOW_PREFETCH
}

enum class IoOperationType {
    READ,
    WRITE,
    FSYNC
}

data class IoRequest(
    val id: Long,
    val type: IoOperationType,
    val priority: IoPriority,
    val file: File,
    val offset: Long,
    val data: ByteArray?,
    val length: Int,
    val queuedTimestampNanos: Long = System.nanoTime()
) : Comparable<IoRequest> {
    override fun compareTo(other: IoRequest): Int {
        // Highest priority first (enum ordinal lowest first)
        val pComp = this.priority.ordinal.compareTo(other.priority.ordinal)
        return if (pComp != 0) pComp else this.queuedTimestampNanos.compareTo(other.queuedTimestampNanos)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IoRequest) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Async I/O Scheduler implementing GDB-SPEC Section 15.
 * Features priority queues, bounded queue capacity, backpressure, and fsync durability.
 */
class AsyncIoEngine(
    val maxQueueDepth: Int = GdbConstants.DEFAULT_IO_QUEUE_CAPACITY,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val queue = PriorityBlockingQueue<IoRequest>(maxQueueDepth)
    private val requestIdSeq = java.util.concurrent.atomic.AtomicLong(1)
    private val activeOperations = AtomicInteger(0)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<GdbResult<ByteArray?>>>()
    private val coroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    val currentQueueDepth: Int get() = queue.size

    init {
        // Start background worker loop
        coroutineScope.launch {
            while (isActive) {
                try {
                    val req = withContext(Dispatchers.IO) { queue.take() }
                    activeOperations.incrementAndGet()
                    val deferred = pendingRequests.remove(req.id)
                    val result = executeIoSync(req)
                    activeOperations.decrementAndGet()
                    deferred?.complete(result)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    // Continue loop
                }
            }
        }
    }

    suspend fun submit(
        type: IoOperationType,
        priority: IoPriority,
        file: File,
        offset: Long,
        data: ByteArray?,
        length: Int
    ): GdbResult<ByteArray?> {
        if (queue.size >= maxQueueDepth) {
            return GdbResult.Failure(
                GdbErrorCode.QUEUE_FULL,
                "Async I/O queue backpressure triggered: depth=${queue.size} >= max=$maxQueueDepth"
            )
        }

        val id = requestIdSeq.getAndIncrement()
        val req = IoRequest(id, type, priority, file, offset, data, length)
        val deferred = CompletableDeferred<GdbResult<ByteArray?>>()
        pendingRequests[id] = deferred

        if (!queue.offer(req)) {
            pendingRequests.remove(id)
            return GdbResult.Failure(GdbErrorCode.QUEUE_FULL, "Failed to enqueue I/O request")
        }

        return deferred.await()
    }

    fun executeIoSync(req: IoRequest): GdbResult<ByteArray?> {
        return try {
            when (req.type) {
                IoOperationType.READ -> {
                    if (!req.file.exists()) {
                        return GdbResult.Failure(GdbErrorCode.PAGE_NOT_FOUND, "File does not exist: ${req.file.path}")
                    }
                    RandomAccessFile(req.file, "r").use { raf ->
                        raf.seek(req.offset)
                        val buf = ByteArray(req.length)
                        val bytesRead = raf.read(buf)
                        if (bytesRead < req.length) {
                            GdbResult.Failure(GdbErrorCode.IO_ERROR, "Unexpected EOF reading file: read $bytesRead of ${req.length}")
                        } else {
                            GdbResult.Success(buf)
                        }
                    }
                }
                IoOperationType.WRITE -> {
                    req.file.parentFile?.mkdirs()
                    RandomAccessFile(req.file, "rw").use { raf ->
                        raf.seek(req.offset)
                        if (req.data != null) {
                            raf.write(req.data, 0, req.length)
                        }
                    }
                    GdbResult.Success(null)
                }
                IoOperationType.FSYNC -> {
                    if (req.file.exists()) {
                        RandomAccessFile(req.file, "rw").use { raf ->
                            raf.fd.sync()
                        }
                    }
                    GdbResult.Success(null)
                }
            }
        } catch (e: Exception) {
            GdbResult.Failure(GdbErrorCode.IO_ERROR, "I/O error during ${req.type}: ${e.message}", e)
        }
    }

    fun shutdown() {
        coroutineScope.cancel()
    }
}
