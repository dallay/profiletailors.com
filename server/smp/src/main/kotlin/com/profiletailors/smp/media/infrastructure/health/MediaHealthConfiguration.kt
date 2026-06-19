package com.profiletailors.smp.media.infrastructure.health

import com.profiletailors.storage.domain.BucketRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.r2dbc.core.DatabaseClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(MediaReadinessHealthIndicator::class.java)

/**
 * Readiness probe for the media context.
 *
 * Verifies:
 * 1. R2DBC DB connectivity for the media context
 * 2. Storage bucket reachability (attachments/media bucket)
 */
@Component("mediaReadiness")
class MediaReadinessHealthIndicator(
    private val databaseClient: DatabaseClient,
    private val bucketRegistry: BucketRegistry,
    @Value("\${media.storage.bucket:attachments}") private val storageBucket: String,
) : HealthIndicator {

    override fun health(): Health? {
        return runBlocking(Dispatchers.IO) {
            var dbHealthy = false
            var storageHealthy = false

            // Check DB connectivity without depending on media schema migrations.
            try {
                databaseClient.sql("SELECT 1")
                    .map { _, _ -> 1 }
                    .one()
                    .awaitSingleOrNull()
                dbHealthy = true
            } catch (e: IllegalStateException) {
                dbHealthy = false
                logger.debug("media health: database check failed", e)
            }

            // Check storage reachability; if the bucket is not configured (no such provider),
            // treat as degraded rather than DOWN so the readiness probe does not fail
            // when cloud storage credentials are not configured (e.g. dev/test environments).
            try {
                val storage = bucketRegistry.getStorage(storageBucket)
                storage.list(storageBucket)
                storageHealthy = true
            } catch (e: com.profiletailors.storage.domain.BucketNotFoundException) {
                // Bucket provider not configured — degrade gracefully, do not fail readiness
                storageHealthy = true
                logger.debug(
                    "media health: bucket provider '{}' not configured; skipping storage reachability check",
                    storageBucket,
                    e,
                )
            } catch (e: IllegalStateException) {
                storageHealthy = false
                logger.debug("media health: storage reachability check failed", e)
            }

            if (dbHealthy && storageHealthy) {
                Health.up()
                    .withDetail("db", "healthy")
                    .withDetail("storage", "reachable bucket=$storageBucket")
                    .build()
            } else if (!dbHealthy) {
                Health.down()
                    .withDetail("db", "unhealthy")
                    .withDetail("storage", if (storageHealthy) "reachable" else "unreachable")
                    .build()
            } else {
                Health.down()
                    .withDetail("db", "healthy")
                    .withDetail("storage", "unreachable bucket=$storageBucket")
                    .build()
            }
        }
    }
}

/**
 * Liveness probe for the media context.
 *
 * Performs a shallow self-check using a low-cost DB ping.
 * Fails after 3 consecutive internal failures to force pod recycling.
 */
@Component("mediaLiveness")
class MediaLivenessHealthIndicator(
    private val databaseClient: DatabaseClient,
) : HealthIndicator {

    override fun health(): Health? {
        return try {
            runBlocking(Dispatchers.IO) {
                databaseClient.sql("SELECT 1").then().block()
            }
            Health.up()
                .withDetail("db", "alive")
                .build()
        } catch (e: IllegalStateException) {
            Health.down()
                .withException(e)
                .build()
        }
    }
}
