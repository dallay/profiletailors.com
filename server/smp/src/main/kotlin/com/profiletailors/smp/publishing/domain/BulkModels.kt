@file:Suppress("MaxLineLength", "FunctionOnlyReturningConstant")

package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.security.MessageDigest
import java.time.Instant

@ValueObject
enum class BulkJobStatus {
    PENDING,
    SCHEDULING,
    SCHEDULED,
    PARTIAL,
    FAILED,
}

@ValueObject
enum class BulkRowStatus {
    VALID,
    INVALID,
    SCHEDULED,
    FAILED,
}

data class ImportError(val code: String, val message: String)

data class BulkImportRow(
    val id: String,
    val jobId: String,
    val rowIndex: Int,
    val status: BulkRowStatus,
    val errors: List<ImportError> = emptyList(),
    val publicationId: String? = null,
    val bodyText: String? = null,
    val scheduledFor: Instant? = null,
    val mediaUrls: List<String> = emptyList(),
    val hasConflict: Boolean = false,
)

@AggregateRoot
data class BulkImportJob(
    val id: String,
    val workspaceId: String,
    val principalId: String,
    val idempotencyKey: String,
    val csvHash: String,
    val status: BulkJobStatus,
    val totalRows: Int,
    val scheduledCount: Int = 0,
    val failedCount: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
) {
    init {
        require(id.isNotBlank()) { "Bulk job id is required." }
        require(workspaceId.isNotBlank()) { "Workspace id is required." }
        require(principalId.isNotBlank()) { "Principal id is required." }
        require(idempotencyKey.matches(Regex("[a-f0-9]{64}"))) { "Idempotency key must be sha256 hex." }
        require(csvHash.isNotBlank()) { "csvHash is required." }
        require(totalRows >= 0) { "totalRows must be non-negative." }
        require(scheduledCount >= 0) { "scheduledCount must be non-negative." }
        require(failedCount >= 0) { "failedCount must be non-negative." }
    }

    fun withCounts(scheduledCount: Int, failedCount: Int): BulkImportJob {
        val newStatus = when {
            failedCount == 0 && scheduledCount == totalRows -> BulkJobStatus.SCHEDULED
            scheduledCount == 0 && failedCount == totalRows && totalRows > 0 -> BulkJobStatus.FAILED
            failedCount == 0 && scheduledCount == 0 -> status
            else -> BulkJobStatus.PARTIAL
        }
        return copy(
            status = newStatus,
            scheduledCount = scheduledCount,
            failedCount = failedCount,
            updatedAt = Instant.now(),
        )
    }

    companion object {
        fun computeIdempotencyKey(workspaceId: String, principalId: String, csvHash: String): String {
            val raw = "$workspaceId:$principalId:$csvHash"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(raw.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

@Suppress("FunctionOnlyReturningConstant")
data class BulkTemplate(val id: String, val name: String, val description: String) {
    companion object {
        fun canonicalHeader(): String = "bodyText,scheduledFor,timezone,media_urls,hashtags"

        fun defaultTemplates(): List<BulkTemplate> = listOf(
            BulkTemplate(id = "linkedin-calendar", name = "LinkedIn Calendar", description = "Default bulk template"),
        )
    }
}

data class BulkRowValidation(
    val rowIndex: Int,
    val status: BulkRowStatus,
    val errors: List<ImportError>,
    val bodyText: String? = null,
    val scheduledFor: Instant? = null,
    val mediaUrls: List<String> = emptyList(),
    val hasConflict: Boolean = false,
)

data class BulkValidationResult(val rows: List<BulkRowValidation>)
