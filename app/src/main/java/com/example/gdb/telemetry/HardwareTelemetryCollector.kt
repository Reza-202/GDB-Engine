package com.example.gdb.telemetry

/**
 * Collects measurable hardware execution telemetry.
 * If utilization percentage or kernel metrics are unsupported in the environment,
 * explicitly returns TelemetryValue.Unsupported. No fake percentages are generated.
 */
class HardwareTelemetryCollector {

    class Timer {
        private val startWallTimeNanos = System.nanoTime()
        private var ioDurationNanos = 0L
        private var transferDurationNanos = 0L
        private var decodeDurationNanos = 0L
        private var queueDurationNanos = 0L
        private var spillBytes = 0L
        private var rowsCount = 0L
        private var bytesCount = 0L

        fun recordIoTime(nanos: Long) { ioDurationNanos += nanos }
        fun recordTransferTime(nanos: Long) { transferDurationNanos += nanos }
        fun recordDecodeTime(nanos: Long) { decodeDurationNanos += nanos }
        fun recordQueueTime(nanos: Long) { queueDurationNanos += nanos }
        fun recordSpill(bytes: Long) { spillBytes += bytes }
        fun recordProgress(rows: Long, bytes: Long) {
            rowsCount += rows
            bytesCount += bytes
        }

        fun snapshot(): ExecutionTelemetry {
            val endWallTimeNanos = System.nanoTime() - startWallTimeNanos
            val runtime = Runtime.getRuntime()
            val totalMem = runtime.totalMemory()
            val freeMem = runtime.freeMemory()
            val usedMem = totalMem - freeMem

            // On standard Android unrooted processes, /proc/stat CPU utilization & GPU stats
            // are restricted by SELinux and not directly measurable.
            // Under GDB-SPEC Section 4: UNSUPPORTED must be reported. No fake values!
            return ExecutionTelemetry(
                wallTimeNanos = endWallTimeNanos,
                cpuTimeNanos = TelemetryValue.Unsupported,
                kernelTimeNanos = TelemetryValue.Unsupported,
                ioTimeNanos = ioDurationNanos,
                transferTimeNanos = transferDurationNanos,
                decodeTimeNanos = decodeDurationNanos,
                cpuUtilizationPercentage = TelemetryValue.Unsupported,
                gpuUtilizationPercentage = TelemetryValue.Unsupported,
                memoryUsageBytes = usedMem,
                peakMemoryBytes = totalMem,
                spillVolumeBytes = spillBytes,
                queueTimeNanos = queueDurationNanos,
                rowsProcessed = rowsCount,
                bytesProcessed = bytesCount
            )
        }
    }

    fun startTimer(): Timer = Timer()
}
