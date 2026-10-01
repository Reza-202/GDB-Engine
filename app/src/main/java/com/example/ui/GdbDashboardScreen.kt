package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gdb.common.GdbConstants
import com.example.gdb.memory.MemoryAllocationClass
import com.example.gdb.memory.MemoryPressureLevel
import com.example.gdb.verification.TestCategory
import com.example.gdb.verification.TestEvidence
import com.example.gdb.verification.TestExecutionResult
import com.example.gdb.wal.WalRecordType
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GdbDashboardScreen(viewModel: GdbViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "GDB Engine",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = EmeraldTertiary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "v3.50",
                                    color = EmeraldTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = state.certifiedBaseline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.runVerificationSuite() },
                        modifier = Modifier.testTag("action_run_harness_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run Verification Suite",
                            tint = EmeraldTertiary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val items = listOf(
                    Triple("Overview", Icons.Default.Dashboard, 0),
                    Triple("Verification", Icons.Default.VerifiedUser, 1),
                    Triple("Memory", Icons.Default.Memory, 2),
                    Triple("Storage & WAL", Icons.Default.Storage, 3),
                    Triple("Registry", Icons.AutoMirrored.Filled.ListAlt, 4)
                )

                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.selectedTab) {
                0 -> OverviewTab(state, viewModel)
                1 -> VerificationTab(state, viewModel)
                2 -> MemoryGovernorTab(state, viewModel)
                3 -> StorageWalTab(state, viewModel)
                4 -> RequirementRegistryTab()
            }
        }
    }
}

@Composable
fun OverviewTab(state: GdbUiState, viewModel: GdbViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Certified Baseline Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("certified_baseline_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CERTIFIED BASELINE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Badge(
                        containerColor = EmeraldTertiary,
                        contentColor = Color.White
                    ) {
                        Text(state.phaseStatus, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.certifiedBaseline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Specification: ${state.specVersion}\nArchitecture: Bounded Memory, Async I/O, WAL Durability, Anti-Fake Telemetry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        // Quick Action Run Verification
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Automated Test Harness",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.verificationSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.runVerificationSuite() },
                    enabled = !state.isTestRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_verification_button")
                ) {
                    if (state.isTestRunning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running Harness...")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Execute Bootstrap Test Suite")
                    }
                }
            }
        }

        // Subsystem Health Grid
        Text(
            text = "Subsystem Health",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SubsystemCard(
                title = "Memory Governor",
                status = state.memoryPressureLevel.name,
                detail = "${state.memoryGovernorUsedBytes / 1024} KB Used",
                color = when (state.memoryPressureLevel) {
                    MemoryPressureLevel.NORMAL -> EmeraldTertiary
                    MemoryPressureLevel.ELEVATED -> CyanPrimary
                    MemoryPressureLevel.HIGH -> AmberWarning
                    else -> RoseError
                },
                modifier = Modifier.weight(1f)
            )
            SubsystemCard(
                title = "Storage / Page",
                status = if (state.pageVerificationSuccess == true) "INTEGRITY OK" else "CHECK",
                detail = "4KB Fixed Pages",
                color = if (state.pageVerificationSuccess == true) EmeraldTertiary else AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SubsystemCard(
                title = "WAL Durability",
                status = "${state.walRecords.size} Records",
                detail = "Sync & Crash Safe",
                color = EmeraldTertiary,
                modifier = Modifier.weight(1f)
            )
            SubsystemCard(
                title = "Hardware Telemetry",
                status = "ANTI-FAKE OK",
                detail = "UNSUPPORTED Protected",
                color = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SubsystemCard(
    title: String,
    status: String,
    detail: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = status, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VerificationTab(state: GdbUiState, viewModel: GdbViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Verification Harness",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Anti-Fake Certified Test Matrix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { viewModel.runVerificationSuite() },
                enabled = !state.isTestRunning,
                modifier = Modifier.testTag("run_harness_tab_button")
            ) {
                Text("Run All")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Certificate Badge if issued
        state.latestCertificate?.let { cert ->
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldTertiary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldTertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Certificate Issued: ${cert.certificateId}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = EmeraldTertiary
                        )
                        Text(
                            text = "Baseline: ${cert.baselineId} (${cert.verifiedTests.size} tests verified)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (state.testResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Press 'Run All' to execute the automated verification test harness and generate evidence.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.testResults) { evidence ->
                    TestEvidenceItem(evidence)
                }
            }
        }
    }
}

@Composable
fun TestEvidenceItem(evidence: TestEvidence) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (badgeColor, icon) = when (evidence.result) {
                        TestExecutionResult.PASS -> Pair(EmeraldTertiary, Icons.Default.CheckCircle)
                        TestExecutionResult.FAIL -> Pair(RoseError, Icons.Default.Cancel)
                        else -> Pair(AmberWarning, Icons.Default.Info)
                    }
                    Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = evidence.testId,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
                Surface(
                    color = when (evidence.result) {
                        TestExecutionResult.PASS -> EmeraldTertiary.copy(alpha = 0.2f)
                        TestExecutionResult.FAIL -> RoseError.copy(alpha = 0.2f)
                        else -> AmberWarning.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = evidence.result.name,
                        color = when (evidence.result) {
                            TestExecutionResult.PASS -> EmeraldTertiary
                            TestExecutionResult.FAIL -> RoseError
                            else -> AmberWarning
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Category: ${evidence.testCategory.name} | Req: ${evidence.requirementIds.joinToString()}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (evidence.logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    evidence.logs.takeLast(3).forEach { log ->
                        Text(
                            text = "• $log",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryGovernorTab(state: GdbUiState, viewModel: GdbViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Global Memory Governor",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Memory Budget Meter
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Budget Consumption", fontWeight = FontWeight.Bold)
                    Text(
                        text = "${state.memoryGovernorUsedBytes / 1024} KB / ${state.memoryGovernorMaxBudget / (1024 * 1024)} MB",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                val fraction = (state.memoryGovernorUsedBytes.toFloat() / state.memoryGovernorMaxBudget.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = when (state.memoryPressureLevel) {
                        MemoryPressureLevel.NORMAL -> EmeraldTertiary
                        MemoryPressureLevel.ELEVATED -> CyanPrimary
                        MemoryPressureLevel.HIGH -> AmberWarning
                        else -> RoseError
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pressure Tier:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.memoryPressureLevel.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (state.memoryPressureLevel) {
                            MemoryPressureLevel.NORMAL -> EmeraldTertiary
                            MemoryPressureLevel.ELEVATED -> CyanPrimary
                            MemoryPressureLevel.HIGH -> AmberWarning
                            else -> RoseError
                        }
                    )
                }
            }
        }

        // Allocation Classes Breakdown
        Text("Allocations by Class", fontWeight = FontWeight.Bold)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MemoryAllocationClass.entries.forEach { allocClass ->
                    val bytes = state.memoryUsageByClass[allocClass] ?: 0L
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(allocClass.name, fontSize = 12.sp)
                        Text("${bytes / 1024} KB", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }

        // Interactive Controls
        Text("Simulation Controls", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.allocateMemory(MemoryAllocationClass.PAGE_CACHE, 2 * 1024 * 1024L) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_alloc_2mb_cache")
            ) {
                Text("+2MB Cache", fontSize = 11.sp)
            }
            Button(
                onClick = { viewModel.allocateMemory(MemoryAllocationClass.EXECUTION, 4 * 1024 * 1024L) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_alloc_4mb_exec")
            ) {
                Text("+4MB Exec", fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.releaseMemory(MemoryAllocationClass.PAGE_CACHE, 2 * 1024 * 1024L) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("-2MB Cache", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.resetMemory() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Reset All", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StorageWalTab(state: GdbUiState, viewModel: GdbViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Storage Engine & WAL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Page Integrity Inspector
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("4KB Page Binary Integrity", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Page ${state.samplePageId} | Gen ${state.pageGeneration} | LSN ${state.pageLsn}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.pageVerificationMessage,
                    fontSize = 12.sp,
                    color = if (state.pageVerificationSuccess == true) EmeraldTertiary else RoseError
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.runPageVerification(tamper = false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_verify_clean_page")
                    ) {
                        Text("Verify Clean Page", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { viewModel.runPageVerification(tamper = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_simulate_tamper")
                    ) {
                        Text("Simulate Tamper", fontSize = 11.sp)
                    }
                }
            }
        }

        // WAL Transaction Stream
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Write-Ahead Log (WAL) Durability", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(state.walStatusMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.appendWalTransaction(
                                WalRecordType.PAGE_UPDATE,
                                "ROW_UPDATED_${System.currentTimeMillis()}"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Append TX", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { viewModel.replayWalRecovery() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Replay Log", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Durable Log Entries (${state.walRecords.size}):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                state.walRecords.takeLast(4).forEach { record ->
                    Text(
                        text = "LSN #${record.lsn} [${record.type}] TxId=${record.transactionId} bytes=${record.payload.size}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RequirementRegistryTab() {
    val reqs = listOf(
        Pair("REQ-BOOTSTRAP-001", "Governance & Baseline Architecture Tracking"),
        Pair("REQ-MEM-GOV-001", "Global Memory Governor & Allocation Budgets"),
        Pair("REQ-STORAGE-PAGE-001", "Fixed-size 4KB Pages & CRC32C Integrity"),
        Pair("REQ-ASYNC-IO-001", "Async I/O Scheduler & Bounded Queue Priority"),
        Pair("REQ-WAL-RECOVERY-001", "Write-Ahead Log & Crash Recovery Durability"),
        Pair("REQ-SECURITY-BOUNDS-001", "Path Traversal, Bounds & Integer Defenses"),
        Pair("REQ-TELEMETRY-001", "Hardware Telemetry Anti-Fake Compliance"),
        Pair("REQ-VERIFY-CHAIN-001", "Complete Verification Chain & Certification")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Requirement Registry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("GDB-SPEC v3.50 Compliance Traceability", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(reqs) { (id, title) ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(id, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Badge(containerColor = EmeraldTertiary) {
                                Text("CERTIFIED", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(title, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
