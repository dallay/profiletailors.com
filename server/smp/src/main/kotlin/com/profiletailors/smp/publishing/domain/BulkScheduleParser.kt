package com.profiletailors.smp.publishing.domain

import java.time.Clock
import java.time.Instant

data class BulkScheduleParseResult(val scheduledFor: Instant?, val error: ImportError?)

object BulkScheduleParser {
    fun parse(raw: String?, clock: Clock): BulkScheduleParseResult {
        if (raw.isNullOrBlank()) {
            return BulkScheduleParseResult(
                null,
                ImportError(code = "INVALID_DATE", message = "scheduledFor is required"),
            )
        }
        return try {
            val parsed = Instant.parse(raw)
            val earliestAllowed = clock.instant().plus(MIN_SCHEDULE_OFFSET)
            if (parsed.isBefore(earliestAllowed)) {
                BulkScheduleParseResult(
                    null,
                    ImportError(code = "INVALID_DATE", message = "scheduledFor must be in the future"),
                )
            } else {
                BulkScheduleParseResult(parsed, null)
            }
        } catch (_: Exception) {
            BulkScheduleParseResult(
                null,
                ImportError(code = "INVALID_DATE", message = "scheduledFor must be ISO-8601"),
            )
        }
    }
}
