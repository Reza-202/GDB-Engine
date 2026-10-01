package com.example.gdb.telemetry

/**
 * Standard Telemetry Metric representation supporting genuine values or UNSUPPORTED.
 * Strictly adheres to GDB-SPEC Section 4 Anti-Fake Policy.
 */
sealed class TelemetryValue<out T> {
    data class Measured<out T>(val value: T, val unit: String) : TelemetryValue<T>()
    object Unsupported : TelemetryValue<Nothing>() {
        override fun toString(): String = "UNSUPPORTED"
    }

    val isMeasured: Boolean get() = this is Measured
}

data class ExecutionTelemetry(
    val wallTimeNanos: Long,
    val cpuTimeNanos: TelemetryValue<Long>,
    val kernelTimeNanos: TelemetryValue<Long>,
    val ioTimeNanos: Long,
    val transferTimeNanos: Long,
    val decodeTimeNanos: Long,
    val cpuUtilizationPercentage: TelemetryValue<Double>,
    val gpuUtilizationPercentage: TelemetryValue<Double>,
    val memoryUsageBytes: Long,
    val peakMemoryBytes: Long,
    val spillVolumeBytes: Long,
    val queueTimeNanos: Long,
    val rowsProcessed: Long,
    val bytesProcessed: Long
) {
    val wallTimeMillis: Double get() = wallTimeNanos / 1_000_000.0
    val throughputRowsPerSec: Double get() = if (wallTimeNanos > 0) (rowsProcessed * 1_000_000_000.0) / wallTimeNanos else 0.0
    val throughputBytesPerSec: Double get() = if (wallTimeNanos > 0) (bytesProcessed * 1_000_000_000.0) / wallTimeNanos else 0.0
}
