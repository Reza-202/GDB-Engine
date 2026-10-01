package com.example.gdb.verification

import com.example.gdb.common.GdbResult

data class Certificate(
    val certificateId: String,
    val baselineId: String,
    val specVersion: String,
    val revision: String,
    val issuedAt: String,
    val verifiedRequirements: List<String>,
    val verifiedTests: List<String>,
    val evidenceReferences: List<String>,
    val status: String
)

object CertificationAuthority {
    /**
     * Issues a verified certificate only if all tests strictly PASS and evidence is valid.
     */
    fun evaluateAndIssue(
        baselineId: String,
        specVersion: String,
        revision: String,
        evidenceBundle: List<TestEvidence>
    ): GdbResult<Certificate> {
        val failed = evidenceBundle.filter { !it.isValidPass }
        if (failed.isNotEmpty()) {
            val failedIds = failed.map { "${it.testId}:${it.result}" }.joinToString(", ")
            return GdbResult.Failure(
                com.example.gdb.common.GdbErrorCode.VERIFICATION_FAILED,
                "Certification rejected due to non-passing tests: $failedIds"
            )
        }

        val allReqs = evidenceBundle.flatMap { it.requirementIds }.distinct()
        val allTests = evidenceBundle.map { it.testId }.distinct()
        val cert = Certificate(
            certificateId = "CERT-${System.currentTimeMillis()}",
            baselineId = baselineId,
            specVersion = specVersion,
            revision = revision,
            issuedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date()),
            verifiedRequirements = allReqs,
            verifiedTests = allTests,
            evidenceReferences = evidenceBundle.map { "EVID-${it.testRunId}-${it.testId}" },
            status = "CERTIFIED"
        )
        return GdbResult.Success(cert)
    }
}
