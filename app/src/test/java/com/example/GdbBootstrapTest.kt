package com.example

import com.example.gdb.common.GdbConstants
import com.example.gdb.verification.CertificationAuthority
import com.example.gdb.verification.TestExecutionResult
import com.example.gdb.verification.VerificationHarness
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GdbBootstrapTest {

    private fun findSpecFile(relativePath: String): File? {
        var current: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        for (i in 0..5) {
            if (current == null) break
            val candidate = File(current, relativePath)
            if (candidate.exists()) return candidate
            current = current.parentFile
        }
        return null
    }

    @Test
    fun `test TEST-BOOTSTRAP-001 Registry and governance integrity`() {
        assertNotNull(GdbConstants.SPEC_VERSION)
        assertEquals("v3.50 — MASTER COMPLETE", GdbConstants.SPEC_VERSION)
        assertEquals("BASELINE-GDB-v3.50-BOOTSTRAP-REV1", GdbConstants.CURRENT_CERTIFIED_BASELINE)

        val actualReg = findSpecFile("gdb_spec/REQUIREMENT_REGISTRY.json")
        val actualState = findSpecFile("gdb_spec/GDB-IMPLEMENTATION-STATE.json")
        val actualBase = findSpecFile("gdb_spec/CERTIFIED-BASELINES.json")

        assertNotNull("REQUIREMENT_REGISTRY.json must exist in project tree", actualReg)
        assertNotNull("GDB-IMPLEMENTATION-STATE.json must exist in project tree", actualState)
        assertNotNull("CERTIFIED-BASELINES.json must exist in project tree", actualBase)
    }

    @Test
    fun `test TEST-MEMORY-001 Memory Governor allocation and pressure tiers`() {
        val harness = VerificationHarness()
        val evidence = harness.testMemoryGovernor("RUN-TEST-01")
        assertEquals(TestExecutionResult.PASS, evidence.result)
        assertTrue(evidence.isValidPass)
    }

    @Test
    fun `test TEST-STORAGE-001 Page serialization, checksum and corruption rejection`() {
        val harness = VerificationHarness()
        val evidence = harness.testPageIntegrity("RUN-TEST-02")
        assertEquals(TestExecutionResult.PASS, evidence.result)
        assertTrue(evidence.isValidPass)
    }

    @Test
    fun `test TEST-CACHE-001 Page cache LRU eviction and memory bounds`() {
        val harness = VerificationHarness()
        val evidence = harness.testPageCacheEviction("RUN-TEST-03")
        assertEquals(TestExecutionResult.PASS, evidence.result)
        assertTrue(evidence.isValidPass)
    }

    @Test
    fun `test TEST-WAL-001 WAL append, sync, and crash recovery replay`() {
        val harness = VerificationHarness()
        val evidence = harness.testWalDurabilityAndRecovery("RUN-TEST-04")
        assertEquals(TestExecutionResult.PASS, evidence.result)
        assertTrue(evidence.isValidPass)
    }

    @Test
    fun `test TEST-IO-001 Async I-O queue priority scheduling and execution`() {
        val harness = VerificationHarness()
        val evidence = harness.testAsyncIoScheduler("RUN-TEST-05")
        assertEquals(TestExecutionResult.PASS, evidence.result)
        assertTrue(evidence.isValidPass)
    }

    @Test
    fun `test TEST-SECURITY-001 Path traversal and integer overflow defenses`() {
        val harness = VerificationHarness()
        val evidence = harness.testSecurityGuard("RUN-TEST-06")
        assertEquals(TestExecutionResult.PASS, evidence.result)
        assertTrue(evidence.isValidPass)
    }

    @Test
    fun `test TEST-TELEMETRY-001 Anti-fake telemetry and UNSUPPORTED metric handling`() {
        val harness = VerificationHarness()
        val evidence = harness.testHardwareTelemetryAntiFake("RUN-TEST-07")
        assertEquals(TestExecutionResult.PASS, evidence.result)
        assertTrue(evidence.isValidPass)
    }

    @Test
    fun `test TEST-VERIFICATION-001 Complete verification chain and certification issuance`() {
        val harness = VerificationHarness()
        val evidenceList = harness.runAllBootstrapTests("RUN-SUITE-FULL")
        assertEquals(7, evidenceList.size)
        assertTrue(evidenceList.all { it.isValidPass })

        val certRes = CertificationAuthority.evaluateAndIssue(
            baselineId = GdbConstants.CURRENT_CERTIFIED_BASELINE,
            specVersion = GdbConstants.SPEC_VERSION,
            revision = "rev-bootstrap-001",
            evidenceBundle = evidenceList
        )

        assertTrue("Certification authority should issue certificate for all passing tests", certRes.isSuccess)
        val cert = certRes.getOrThrow()
        assertEquals("CERTIFIED", cert.status)
        assertEquals(GdbConstants.CURRENT_CERTIFIED_BASELINE, cert.baselineId)
    }
}
