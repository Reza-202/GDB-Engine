package com.example.gdb.verification

enum class TestCategory {
    UNIT,
    INTEGRATION,
    SYSTEM,
    REGRESSION,
    GOLDEN,
    PROPERTY,
    FUZZ,
    RECOVERY,
    PERFORMANCE,
    RESOURCE,
    SECURITY,
    COMPATIBILITY,
    END_TO_END
}

enum class TestExecutionResult {
    PASS,
    FAIL,
    ERROR,
    UNSUPPORTED,
    SKIPPED
}

/**
 * Standard Evidence format mandated by GDB-SPEC Section 10.
 */
data class TestEvidence(
    val testRunId: String,
    val testId: String,
    val testCategory: TestCategory,
    val requirementIds: List<String>,
    val implementationRevision: String,
    val timestamp: String,
    val environment: String,
    val configuration: Map<String, String>,
    val datasetFixture: String,
    val result: TestExecutionResult,
    val metrics: Map<String, String>,
    val logs: List<String>,
    val artifacts: List<String>
) {
    /**
     * Anti-Fake check: strictly only PASS with real execution is considered valid compliance.
     */
    val isValidPass: Boolean
        get() = result == TestExecutionResult.PASS
}
