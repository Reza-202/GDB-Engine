package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdb.common.Checksum
import com.example.gdb.common.GdbConstants
import com.example.gdb.common.GdbErrorCode
import com.example.gdb.common.GdbResult
import com.example.gdb.memory.MemoryAllocationClass
import com.example.gdb.memory.MemoryGovernor
import com.example.gdb.memory.MemoryPressureLevel
import com.example.gdb.storage.Page
import com.example.gdb.storage.PageCache
import com.example.gdb.storage.PageId
import com.example.gdb.telemetry.ExecutionTelemetry
import com.example.gdb.telemetry.HardwareTelemetryCollector
import com.example.gdb.verification.*
import com.example.gdb.wal.WalManager
import com.example.gdb.wal.WalRecord
import com.example.gdb.wal.WalRecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class GdbUiState(
    val selectedTab: Int = 0,
    val specVersion: String = GdbConstants.SPEC_VERSION,
    val certifiedBaseline: String = GdbConstants.CURRENT_CERTIFIED_BASELINE,
    val currentPhase: String = "PHASE_0_BOOTSTRAP",
    val phaseStatus: String = "CERTIFIED",

    // Memory Governor
    val memoryGovernorMaxBudget: Long = 16 * 1024 * 1024L, // 16 MB
    val memoryGovernorUsedBytes: Long = 0L,
    val memoryPressureLevel: MemoryPressureLevel = MemoryPressureLevel.NORMAL,
    val memoryUsageByClass: Map<MemoryAllocationClass, Long> = emptyMap(),

    // Storage & Page
    val samplePageId: PageId = PageId(1, 42L),
    val samplePagePayloadText: String = "GDB_RECORD_KEY_001=VAL_HELLO_DISTRIBUTED_WORLD",
    val pageGeneration: Long = 100L,
    val pageLsn: Long = 500L,
    val isPageTampered: Boolean = false,
    val pageVerificationMessage: String = "Ready for integrity test",
    val pageVerificationSuccess: Boolean? = null,
    val pageChecksum: Long = 0L,

    // WAL
    val walRecords: List<WalRecord> = emptyList(),
    val walStatusMessage: String = "WAL ready for transactions",

    // Verification Suite
    val isTestRunning: Boolean = false,
    val testResults: List<TestEvidence> = emptyList(),
    val latestCertificate: Certificate? = null,
    val verificationSummary: String = "Ready to run automated verification harness",

    // Telemetry
    val latestTelemetry: ExecutionTelemetry? = null
)

class GdbViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GdbUiState())
    val uiState: StateFlow<GdbUiState> = _uiState.asStateFlow()

    private val memoryGovernor = MemoryGovernor(maxBudgetBytes = 16 * 1024 * 1024L)
    private val pageCache = PageCache(memoryGovernor, maxPages = 256)
    private val telemetryCollector = HardwareTelemetryCollector()
    private val verificationHarness = VerificationHarness(
        File(application.cacheDir, "gdb_engine_verify")
    )
    private val walFile = File(application.filesDir, "gdb_engine.wal")
    private val walManager = WalManager(walFile)

    init {
        memoryGovernor.registerPressureListener { level ->
            _uiState.value = _uiState.value.copy(
                memoryPressureLevel = level,
                memoryGovernorUsedBytes = memoryGovernor.getTotalUsage(),
                memoryUsageByClass = MemoryAllocationClass.entries.associateWith { memoryGovernor.getUsage(it) }
            )
        }
        refreshMemoryState()
        refreshWalState()
        runPageVerification(tamper = false)
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun allocateMemory(allocClass: MemoryAllocationClass, bytes: Long) {
        val res = memoryGovernor.allocate(allocClass, bytes)
        refreshMemoryState()
    }

    fun releaseMemory(allocClass: MemoryAllocationClass, bytes: Long) {
        memoryGovernor.release(allocClass, bytes)
        refreshMemoryState()
    }

    fun resetMemory() {
        memoryGovernor.reset()
        refreshMemoryState()
    }

    private fun refreshMemoryState() {
        _uiState.value = _uiState.value.copy(
            memoryGovernorUsedBytes = memoryGovernor.getTotalUsage(),
            memoryPressureLevel = memoryGovernor.getPressureLevel(),
            memoryUsageByClass = MemoryAllocationClass.entries.associateWith { memoryGovernor.getUsage(it) }
        )
    }

    fun runPageVerification(tamper: Boolean) {
        val timer = telemetryCollector.startTimer()
        val payloadBytes = _uiState.value.samplePagePayloadText.toByteArray(Charsets.UTF_8)
        val page = Page(
            pageId = _uiState.value.samplePageId,
            generation = _uiState.value.pageGeneration,
            lsn = _uiState.value.pageLsn,
            payload = payloadBytes
        )
        val serialized = page.serialize()

        val bytesToTest = if (tamper) {
            val tampered = serialized.copyOf()
            tampered[GdbConstants.PAGE_HEADER_SIZE + 2] = (tampered[GdbConstants.PAGE_HEADER_SIZE + 2] + 1).toByte()
            tampered
        } else {
            serialized
        }

        timer.recordIoTime(1_500_000L)
        timer.recordProgress(rows = 1, bytes = bytesToTest.size.toLong())

        val desRes = Page.deserialize(_uiState.value.samplePageId, bytesToTest)
        val telemetry = timer.snapshot()

        when (desRes) {
            is GdbResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isPageTampered = false,
                    pageVerificationSuccess = true,
                    pageVerificationMessage = "SUCCESS: Page integrity verified! Magic OK, CRC32 match, Gen ${desRes.value.generation}, LSN ${desRes.value.lsn}",
                    pageChecksum = Checksum.compute(bytesToTest, 0, GdbConstants.PAGE_HEADER_SIZE + payloadBytes.size),
                    latestTelemetry = telemetry
                )
            }
            is GdbResult.Failure -> {
                _uiState.value = _uiState.value.copy(
                    isPageTampered = tamper,
                    pageVerificationSuccess = false,
                    pageVerificationMessage = "PROTECTION DETECTED: [${desRes.code}] ${desRes.message}",
                    latestTelemetry = telemetry
                )
            }
        }
    }

    fun appendWalTransaction(type: WalRecordType, payloadText: String) {
        val txId = System.currentTimeMillis()
        val res = walManager.append(txId, type, payloadText.toByteArray(Charsets.UTF_8))
        if (res is GdbResult.Success) {
            walManager.sync()
            _uiState.value = _uiState.value.copy(
                walStatusMessage = "Appended & synced ${type.name} at LSN #${res.value.lsn}"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                walStatusMessage = "WAL Append Failed: ${(res as GdbResult.Failure).message}"
            )
        }
        refreshWalState()
    }

    fun replayWalRecovery() {
        val res = walManager.recover()
        if (res is GdbResult.Success) {
            _uiState.value = _uiState.value.copy(
                walRecords = res.value,
                walStatusMessage = "Crash recovery replay verified ${res.value.size} durable records!"
            )
        }
    }

    private fun refreshWalState() {
        val res = walManager.recover()
        if (res is GdbResult.Success) {
            _uiState.value = _uiState.value.copy(walRecords = res.value)
        }
    }

    fun runVerificationSuite() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isTestRunning = true,
                verificationSummary = "Executing GDB-SPEC Automated Test Suite..."
            )

            val evidenceList = verificationHarness.runAllBootstrapTests()
            val allPassed = evidenceList.all { it.isValidPass }

            val certRes = if (allPassed) {
                CertificationAuthority.evaluateAndIssue(
                    baselineId = GdbConstants.CURRENT_CERTIFIED_BASELINE,
                    specVersion = GdbConstants.SPEC_VERSION,
                    revision = "rev-bootstrap-001",
                    evidenceBundle = evidenceList
                ).getOrNull()
            } else null

            _uiState.value = _uiState.value.copy(
                isTestRunning = false,
                testResults = evidenceList,
                latestCertificate = certRes,
                verificationSummary = if (allPassed) {
                    "ALL ${evidenceList.size} TESTS PASSED — CERTIFIED BASELINE ISSUED!"
                } else {
                    "TEST FAILURES DETECTED IN HARNESS"
                }
            )
        }
    }
}
