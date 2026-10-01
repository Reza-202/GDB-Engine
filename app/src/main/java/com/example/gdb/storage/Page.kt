package com.example.gdb.storage

import com.example.gdb.common.Checksum
import com.example.gdb.common.GdbConstants
import com.example.gdb.common.GdbErrorCode
import com.example.gdb.common.GdbResult
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard Storage Page implementing GDB-SPEC Section 15.
 * Features:
 * - 8-byte Magic header (0x4744425F50414745L)
 * - 8-byte Generation number
 * - 8-byte LSN
 * - 4-byte Payload Length
 * - 4-byte Checksum (CRC32 over header + payload)
 * - Raw Payload Bytes (padded to fixed page size)
 */
class Page(
    val pageId: PageId,
    val generation: Long,
    val lsn: Long,
    val payload: ByteArray,
    val pageSize: Int = GdbConstants.DEFAULT_PAGE_SIZE
) {
    val usablePayloadCapacity: Int = pageSize - GdbConstants.PAGE_HEADER_SIZE

    init {
        require(payload.size <= usablePayloadCapacity) {
            "Payload size ${payload.size} exceeds usable page capacity $usablePayloadCapacity"
        }
    }

    /**
     * Serializes this page to a byte buffer of exactly [pageSize] bytes.
     */
    fun serialize(): ByteArray {
        val buffer = ByteBuffer.allocate(pageSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(GdbConstants.PAGE_MAGIC)
        buffer.putLong(generation)
        buffer.putLong(lsn)
        buffer.putInt(payload.size)
        // Checksum placeholder at offset 28..31
        buffer.putInt(0)

        // Put payload
        buffer.put(payload)

        // Compute checksum over first 28 bytes + payload
        val raw = buffer.array()
        val computedCrc = Checksum.compute(raw, 0, GdbConstants.PAGE_HEADER_SIZE + payload.size)

        // Put computed checksum into offset 28
        val crcInt = (computedCrc and 0xFFFFFFFFL).toInt()
        buffer.putInt(28, crcInt)

        return raw
    }

    companion object {
        /**
         * Deserializes and validates page integrity from byte buffer.
         */
        fun deserialize(pageId: PageId, bytes: ByteArray, expectedPageSize: Int = GdbConstants.DEFAULT_PAGE_SIZE): GdbResult<Page> {
            if (bytes.size < expectedPageSize) {
                return GdbResult.Failure(
                    GdbErrorCode.PAGE_CORRUPTED,
                    "Page buffer size ${bytes.size} is smaller than required $expectedPageSize"
                )
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.long
            if (magic != GdbConstants.PAGE_MAGIC) {
                return GdbResult.Failure(
                    GdbErrorCode.PAGE_CORRUPTED,
                    "Invalid page magic: 0x${java.lang.Long.toHexString(magic)}"
                )
            }

            val generation = buffer.long
            val lsn = buffer.long
            val payloadLength = buffer.int
            val recordedChecksum = buffer.int.toLong() and 0xFFFFFFFFL

            if (payloadLength < 0 || payloadLength > expectedPageSize - GdbConstants.PAGE_HEADER_SIZE) {
                return GdbResult.Failure(
                    GdbErrorCode.PAGE_CORRUPTED,
                    "Invalid payload length: $payloadLength"
                )
            }

            // Zero out checksum field temporarily to verify CRC
            val copy = bytes.copyOf()
            ByteBuffer.wrap(copy).order(ByteOrder.BIG_ENDIAN).putInt(28, 0)
            val computedCrc = Checksum.compute(copy, 0, GdbConstants.PAGE_HEADER_SIZE + payloadLength)

            if (computedCrc != recordedChecksum) {
                return GdbResult.Failure(
                    GdbErrorCode.CHECKSUM_MISMATCH,
                    "Page checksum mismatch: recorded=$recordedChecksum, computed=$computedCrc"
                )
            }

            val payload = ByteArray(payloadLength)
            buffer.position(GdbConstants.PAGE_HEADER_SIZE)
            buffer.get(payload)

            return GdbResult.Success(
                Page(
                    pageId = pageId,
                    generation = generation,
                    lsn = lsn,
                    payload = payload,
                    pageSize = expectedPageSize
                )
            )
        }
    }
}
