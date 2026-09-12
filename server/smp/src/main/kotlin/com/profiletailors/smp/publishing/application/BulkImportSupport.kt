package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.BulkJobStatus
import com.profiletailors.smp.publishing.domain.ImportError

object BulkImportErrorMapper {
    fun map(ex: Exception): ImportError {
        val code = when (ex) {
            is PublicationValidationException -> "INVALID_MEDIA"
            is IllegalArgumentException -> if (ex.message?.contains("CAPABILITY") == true) {
                "CAPABILITY_VIOLATION"
            } else {
                "INVALID_DATE"
            }
            else -> "UNKNOWN"
        }
        return ImportError(code = code, message = ex.message ?: "failed")
    }
}

object BulkImportFinalStatus {
    fun compute(totalRows: Int, scheduledCount: Int, failedCount: Int): BulkJobStatus = when {
        failedCount == 0 && scheduledCount == totalRows -> BulkJobStatus.SCHEDULED
        scheduledCount == 0 && failedCount == totalRows && totalRows > 0 -> BulkJobStatus.FAILED
        else -> BulkJobStatus.PARTIAL
    }
}
