package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.observability.NoOpOperationalEventSink
import com.profiletailors.observability.OperationalEventSink
import com.profiletailors.observability.Severity
import com.profiletailors.observability.emit
import com.profiletailors.smp.publishing.domain.CredentialPurgeCandidate
import com.profiletailors.smp.publishing.domain.CredentialPurgeRepository
import com.profiletailors.smp.publishing.domain.CredentialRetentionRule
import com.profiletailors.smp.publishing.domain.ResolvedCredentialRetention
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.time.Instant

private const val DEFAULT_BATCH_SIZE = 100
private const val MAX_BATCH_SIZE = 1000
private const val MAX_REPORT_SAMPLES = 20

@Service
class CredentialRetentionJob(
    private val purgeRepository: CredentialPurgeRepository,
    private val retentionRule: CredentialRetentionRule = CredentialRetentionRule(
        activityId = CredentialRetentionRule.DEFAULT_ACTIVITY_ID,
        policyVersion = "",
        expiredMetadataRetention = java.time.Duration.ofDays(
            CredentialRetentionRule.DEFAULT_EXPIRED_METADATA_RETENTION_DAYS,
        ),
        disconnectGrace = java.time.Duration.ZERO,
        enabled = false,
    ),
    private val operationalEvents: OperationalEventSink = NoOpOperationalEventSink,
) {
    suspend fun run(dryRun: Boolean = false, batchSize: Int = DEFAULT_BATCH_SIZE): CredentialPurgeResult {
        require(batchSize in 1..MAX_BATCH_SIZE) { "Batch size must be between 1 and $MAX_BATCH_SIZE." }
        val startTime = System.currentTimeMillis()
        val accumulator = PurgeAccumulator()
        val now = Instant.now()
        try {
            for (provider in SocialProvider.entries) {
                val resolved = retentionRule.resolveFor(provider)
                if (resolved.enabled) {
                    runForProvider(resolved, now, dryRun, batchSize, accumulator)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            accumulator.errors++
            operationalEvents.emit(
                severity = Severity.ERROR,
                name = "credentials.purge.runFailed",
                cause = e,
            )
        }
        return accumulator.toResult(dryRun, startTime)
    }

    private suspend fun runForProvider(
        resolved: ResolvedCredentialRetention,
        now: Instant,
        dryRun: Boolean,
        batchSize: Int,
        accumulator: PurgeAccumulator,
    ) {
        val expiredBefore = now.minus(resolved.expiredMetadataRetention)
        purgeRepository.findOrphanExpiredCandidates(resolved.provider, expiredBefore, batchSize).collect { candidate ->
            accumulator.register(candidate, dryRun, ::deleteCandidate)
        }
    }

    private suspend fun deleteCandidate(candidate: CredentialPurgeCandidate): CandidateOutcome {
        try {
            if (purgeRepository.deleteById(candidate.id)) {
                operationalEvents.emit(
                    severity = Severity.INFO,
                    name = "credentials.purge.deleted",
                    attributes = arrayOf(
                        "provider" to candidate.provider.name,
                        "credentialId" to candidate.id.toString(),
                    ),
                )
                return CandidateOutcome.DELETED
            }
            return CandidateOutcome.SKIPPED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            operationalEvents.emit(
                severity = Severity.WARN,
                name = "credentials.purge.failed",
                cause = e,
                attributes = arrayOf(
                    "provider" to candidate.provider.name,
                    "credentialId" to candidate.id.toString(),
                ),
            )
            return CandidateOutcome.FAILED
        }
    }

    private inner class PurgeAccumulator {
        var scanned = 0
        var deleted = 0
        var errors = 0
        var skipped = 0
        val samples = mutableListOf<String>()

        suspend fun register(
            candidate: CredentialPurgeCandidate,
            dryRun: Boolean,
            deleter: suspend (CredentialPurgeCandidate) -> CandidateOutcome,
        ) {
            scanned++
            if (samples.size < MAX_REPORT_SAMPLES) {
                samples += candidate.id.toString()
            }
            if (dryRun) {
                skipped++
                return
            }
            when (deleter(candidate)) {
                CandidateOutcome.DELETED -> deleted++
                CandidateOutcome.SKIPPED -> skipped++
                CandidateOutcome.FAILED -> errors++
            }
        }

        fun toResult(dryRun: Boolean, startTime: Long): CredentialPurgeResult {
            val durationMs = System.currentTimeMillis() - startTime
            operationalEvents.emit(
                severity = Severity.INFO,
                name = "credentials.purge.run",
                attributes = arrayOf(
                    "scanned" to scanned,
                    "deleted" to deleted,
                    "errors" to errors,
                    "skipped" to skipped,
                    "dryRun" to dryRun,
                    "durationMs" to durationMs,
                ),
            )
            return CredentialPurgeResult(
                scanned = scanned,
                deleted = deleted,
                errors = errors,
                skipped = skipped,
                dryRun = dryRun,
                sampleCredentialIds = samples.toList(),
                durationMs = durationMs,
                timestamp = Instant.now(),
            )
        }
    }

    private enum class CandidateOutcome { DELETED, SKIPPED, FAILED }
}

data class CredentialPurgeResult(
    val scanned: Int,
    val deleted: Int,
    val errors: Int,
    val skipped: Int,
    val dryRun: Boolean,
    val sampleCredentialIds: List<String>,
    val durationMs: Long,
    val timestamp: Instant,
)
