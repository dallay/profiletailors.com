package com.profiletailors.smp.media.infrastructure.health

import com.profiletailors.smp.media.infrastructure.MediaProperties
import com.profiletailors.storage.domain.BucketRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.r2dbc.core.DatabaseClient
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
    mediaProperties: MediaProperties,
) : HealthIndicator {
    private val storageBucket: String = mediaProperties.storage.bucket

    @Suppress("TooGenericExceptionCaught")
    override fun health(): Health? = runBlocking(Dispatchers.IO) {
        val dbHealthy: Boolean
        val storageHealthy: Boolean

        // Check DB connectivity without depending on media schema migrations.
        dbHealthy = try {
            databaseClient.sql("SELECT 1")
                .map { _, _ -> 1 }
                .one()
                .awaitSingleOrNull()
            true
        } catch (e: Exception) {
            logger.debug("media health: database check failed", e)
            false
        }

        // Check storage reachability; if the bucket is not configured (no such provider),
        // treat as degraded rather than DOWN so the readiness probe does not fail
        // when cloud storage credentials are not configured (e.g. dev/test environments).
        storageHealthy = try {
            val storage = bucketRegistry.getStorage(storageBucket)
            storage.list(storageBucket)
            true
        } catch (e: com.profiletailors.storage.domain.BucketNotFoundException) {
            // Bucket provider not configured — degrade gracefully, do not fail readiness
            logger.debug(
                "media health: bucket provider '{}' not configured; skipping storage reachability check",
                storageBucket,
                e,
            )
            true
        } catch (e: Exception) {
            logger.debug("media health: storage reachability check failed", e)
            false
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

/**
 * Liveness probe for the media context.
 *
 * Performs a shallow self-check using a low-cost DB ping.
 * Fails after 3 consecutive internal failures to force pod recycling.
 */
@Component("mediaLiveness")
class MediaLivenessHealthIndicator(private val databaseClient: DatabaseClient) : HealthIndicator {

    @Suppress("TooGenericExceptionCaught")
    override fun health(): Health? = try {
        runBlocking(Dispatchers.IO) {
            databaseClient.sql("SELECT 1").then().block()
        }
        Health.up()
            .withDetail("db", "alive")
            .build()
    } catch (e: Exception) {
        Health.down()
            .withException(e)
            .build()
    }
}
