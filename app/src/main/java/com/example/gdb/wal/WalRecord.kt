package com.example.gdb.wal

import com.example.gdb.common.Checksum
import com.example.gdb.common.GdbConstants
import com.example.gdb.common.GdbErrorCode
import com.example.gdb.common.GdbResult
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class WalRecordType(val code: Byte) {
    TX_BEGIN(1),
    PAGE_UPDATE(2),
    TX_COMMIT(3),
    TX_ABORT(4),
    CHECKPOINT(5);

    companion object {
        fun fromCode(code: Byte): WalRecordType? = entries.find { it.code == code }
    }
}

/**
 * Standard WAL Record implementing GDB-SPEC Section 15.
 * Format:
 * - 8 bytes Magic (0x4744425F57414C21L)
 * - 8 bytes LSN
 * - 8 bytes Transaction ID
 * - 1 byte Record Type
 * - 4 bytes Payload Length
 * - 4 bytes Checksum (CRC32)
 * - N bytes Payload
 */
data class WalRecord(
    val lsn: Long,
    val transactionId: Long,
    val type: WalRecordType,
    val payload: ByteArray
) {
    fun serialize(): ByteArray {
        val totalSize = HEADER_SIZE + payload.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(GdbConstants.WAL_MAGIC)
        buffer.putLong(lsn)
        buffer.putLong(transactionId)
        buffer.put(type.code)
        buffer.putInt(payload.size)
        // Checksum placeholder at offset 29..32
        buffer.putInt(0)
        buffer.put(payload)

        val raw = buffer.array()
        val computedCrc = Checksum.compute(raw, 0, HEADER_SIZE + payload.size)
        buffer.putInt(29, (computedCrc and 0xFFFFFFFFL).toInt())

        return raw
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WalRecord) return false
        return lsn == other.lsn &&
                transactionId == other.transactionId &&
                type == other.type &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = lsn.hashCode()
        result = 31 * result + transactionId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_SIZE = 33 // 8+8+8+1+4+4 = 33 bytes

        fun deserialize(bytes: ByteArray, offset: Int = 0): GdbResult<Pair<WalRecord, Int>> {
            if (bytes.size - offset < HEADER_SIZE) {
                return GdbResult.Failure(GdbErrorCode.WAL_CORRUPTED, "Buffer too small for WAL header")
            }

            val buffer = ByteBuffer.wrap(bytes, offset, bytes.size - offset).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.long
            if (magic != GdbConstants.WAL_MAGIC) {
                return GdbResult.Failure(GdbErrorCode.WAL_CORRUPTED, "Invalid WAL magic: 0x${java.lang.Long.toHexString(magic)}")
            }

            val lsn = buffer.long
            val txId = buffer.long
            val typeCode = buffer.get()
            val type = WalRecordType.fromCode(typeCode)
                ?: return GdbResult.Failure(GdbErrorCode.WAL_CORRUPTED, "Unknown WAL record type: $typeCode")
            val payloadLength = buffer.int
            val recordedCrc = buffer.int.toLong() and 0xFFFFFFFFL

            if (payloadLength < 0 || bytes.size - offset - HEADER_SIZE < payloadLength) {
                return GdbResult.Failure(GdbErrorCode.WAL_CORRUPTED, "Invalid or truncated WAL payload length: $payloadLength")
            }

            val totalRecordSize = HEADER_SIZE + payloadLength
            val recordSlice = bytes.copyOfRange(offset, offset + totalRecordSize)
            ByteBuffer.wrap(recordSlice).order(ByteOrder.BIG_ENDIAN).putInt(29, 0)
            val computedCrc = Checksum.compute(recordSlice, 0, totalRecordSize)

            if (computedCrc != recordedCrc) {
                return GdbResult.Failure(
                    GdbErrorCode.CHECKSUM_MISMATCH,
                    "WAL record CRC mismatch: recorded=$recordedCrc, computed=$computedCrc"
                )
            }

            val payload = ByteArray(payloadLength)
            buffer.get(payload)

            return GdbResult.Success(Pair(WalRecord(lsn, txId, type, payload), totalRecordSize))
        }
    }
}
