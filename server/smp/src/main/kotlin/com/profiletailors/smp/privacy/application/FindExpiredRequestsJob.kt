package com.profiletailors.smp.privacy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.observability.NoOpOperationalEventSink
import com.profiletailors.observability.OperationalEventSink
import com.profiletailors.observability.error
import com.profiletailors.observability.info
import com.profiletailors.smp.privacy.domain.DataSubjectRequestRepository
import java.time.Instant

/**
 * Scheduled job that finds data subject requests past their retention expiry.
 *
 * For Phase 1 this is a discovery-only job — it logs expired requests
 * but does not delete them. Deletion/TTL handling will be added in Phase 2.
 *
 * @since 1.0.0
 */
@Service
class FindExpiredRequestsJob(
    private val repository: DataSubjectRequestRepository,
    private val operationalEvents: OperationalEventSink = NoOpOperationalEventSink,
) {

    /**
     * Run one expiry discovery cycle.
     *
     * @return [FindExpiredRequestsResult] with metrics about the run.
     */
    suspend fun run(): FindExpiredRequestsResult {
        val startTime = System.currentTimeMillis()

        val expired = try {
            repository.findExpired(Instant.now())
        } catch (e: Exception) {
            operationalEvents.error("privacy.expiry.runFailed", e)
            return FindExpiredRequestsResult(
                expiredCount = 0,
                errors = 1,
                durationMs = System.currentTimeMillis() - startTime,
                timestamp = Instant.now(),
            )
        }

        val durationMs = System.currentTimeMillis() - startTime
        operationalEvents.info(
            "privacy.expiry.run expiredCount={} durationMs={}",
            expired.size,
            durationMs,
        )

        return FindExpiredRequestsResult(
            expiredCount = expired.size,
            errors = 0,
            durationMs = durationMs,
            timestamp = Instant.now(),
        )
    }
}

/**
 * Result of a [FindExpiredRequestsJob] run.
 *
 * @property expiredCount Number of expired requests found
 * @property errors Number of errors encountered
 * @property durationMs Execution time in milliseconds
 * @property timestamp When the run occurred
 * @since 1.0.0
 */
data class FindExpiredRequestsResult(
    val expiredCount: Int,
    val errors: Int,
    val durationMs: Long,
    val timestamp: Instant,
)
