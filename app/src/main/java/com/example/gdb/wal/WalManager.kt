package com.example.gdb.wal

import com.example.gdb.common.GdbErrorCode
import com.example.gdb.common.GdbResult
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Write-Ahead Log Manager implementing GDB-SPEC Section 15 Write Path & Durability.
 */
class WalManager(
    val walFile: File
) {
    private val lock = ReentrantLock()
    private val currentLsn = AtomicLong(0L)
    private var randomAccessFile: RandomAccessFile? = null

    init {
        walFile.parentFile?.mkdirs()
        randomAccessFile = RandomAccessFile(walFile, "rw")
        // Scan to end to determine current LSN
        val scanRes = recover()
        if (scanRes is GdbResult.Success) {
            val maxLsn = scanRes.value.maxOfOrNull { it.lsn } ?: 0L
            currentLsn.set(maxLsn)
        }
    }

    /**
     * Appends a WAL record, guarantees sequential monotonic LSN, and flushes bytes.
     */
    fun append(txId: Long, type: WalRecordType, payload: ByteArray): GdbResult<WalRecord> = lock.withLock {
        try {
            val raf = randomAccessFile ?: return GdbResult.Failure(GdbErrorCode.WAL_FLUSH_FAILED, "WAL file closed")
            val nextLsn = currentLsn.incrementAndGet()
            val record = WalRecord(nextLsn, txId, type, payload)
            val bytes = record.serialize()

            raf.seek(raf.length())
            raf.write(bytes)
            GdbResult.Success(record)
        } catch (e: Exception) {
            GdbResult.Failure(GdbErrorCode.WAL_FLUSH_FAILED, "Failed to append WAL record: ${e.message}", e)
        }
    }

    /**
     * Explicit fsync to guarantee disk durability before transaction commit publication.
     */
    fun sync(): GdbResult<Unit> = lock.withLock {
        try {
            randomAccessFile?.fd?.sync()
            GdbResult.Success(Unit)
        } catch (e: Exception) {
            GdbResult.Failure(GdbErrorCode.WAL_FLUSH_FAILED, "WAL fsync failed: ${e.message}", e)
        }
    }

    /**
     * Recovers and validates all durable records from the WAL.
     * Stops at any truncated or corrupted record.
     */
    fun recover(): GdbResult<List<WalRecord>> = lock.withLock {
        try {
            val records = mutableListOf<WalRecord>()
            if (!walFile.exists() || walFile.length() == 0L) {
                return GdbResult.Success(emptyList())
            }

            val fileBytes = walFile.readBytes()
            var offset = 0
            while (offset < fileBytes.size) {
                val desRes = WalRecord.deserialize(fileBytes, offset)
                when (desRes) {
                    is GdbResult.Success -> {
                        val (record, bytesConsumed) = desRes.value
                        records.add(record)
                        offset += bytesConsumed
                    }
                    is GdbResult.Failure -> {
                        // Truncated or corrupted tail record encountered during crash recovery
                        break
                    }
                }
            }
            GdbResult.Success(records)
        } catch (e: Exception) {
            GdbResult.Failure(GdbErrorCode.WAL_CORRUPTED, "Recovery failed: ${e.message}", e)
        }
    }

    fun getCurrentLsn(): Long = currentLsn.get()

    fun close() = lock.withLock {
        try {
            randomAccessFile?.close()
            randomAccessFile = null
        } catch (_: Exception) {}
    }
}
