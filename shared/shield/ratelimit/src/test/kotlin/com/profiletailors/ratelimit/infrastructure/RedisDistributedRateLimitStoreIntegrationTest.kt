package com.profiletailors.ratelimit.infrastructure

import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.infrastructure.adapter.ApiKeyParser
import com.profiletailors.ratelimit.infrastructure.adapter.Bucket4jRateLimiter
import com.profiletailors.ratelimit.infrastructure.config.BucketConfigurationFactory
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import com.profiletailors.ratelimit.infrastructure.store.RedisBucket4jRateLimitStore
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Clock
import java.time.Duration

@Testcontainers(disabledWithoutDocker = true)
class RedisDistributedRateLimitStoreIntegrationTest {

    @Test
    fun `two limiter replicas share waitlist bucket state through redis backend`() = runTest {
        val redisUri = "redis://${redis.host}:${redis.firstMappedPort}"
        val properties = testProperties(redisUri)

        val replicaA = createReplica(properties)
        val replicaB = createReplica(properties)

        try {
            val identifier = "IP:203.0.113.10"
            val bucketIdentity = "$identifier:/api/waitlists/profile-tailors-launch/entries"

            // Capacity is 3. If both replicas share state, the 4th combined request is denied.
            replicaA.limiter.consumeToken(identifier, RateLimitStrategy.WAITLIST, bucketIdentity)
                .shouldBeInstanceOf<RateLimitResult.Allowed>()
            replicaB.limiter.consumeToken(identifier, RateLimitStrategy.WAITLIST, bucketIdentity)
                .shouldBeInstanceOf<RateLimitResult.Allowed>()
            replicaA.limiter.consumeToken(identifier, RateLimitStrategy.WAITLIST, bucketIdentity)
                .shouldBeInstanceOf<RateLimitResult.Allowed>()

            val denied = replicaB.limiter.consumeToken(identifier, RateLimitStrategy.WAITLIST, bucketIdentity)
            denied.shouldBeInstanceOf<RateLimitResult.Denied>()

            val sourceMetric = replicaA.meterRegistry.find("rate_limit.requests.by_source.total")
                .tag("strategy", "waitlist")
                .tag("result", "allowed")
                .tag("bucket_source", "distributed")
                .counter()

            requireNotNull(sourceMetric) { "Expected distributed source metric to be present" }
        } finally {
            replicaA.store.close()
            replicaB.store.close()
        }
    }

    private fun createReplica(properties: RateLimitProperties): Replica {
        val meterRegistry = SimpleMeterRegistry()
        val metrics = RateLimitMetrics(meterRegistry)
        val store = RedisBucket4jRateLimitStore(properties)

        val limiter = Bucket4jRateLimiter(
            configurationFactory = BucketConfigurationFactory(properties),
            apiKeyParser = ApiKeyParser(properties),
            metrics = metrics,
            rateLimitStore = store,
            clock = Clock.systemUTC(),
        )

        return Replica(limiter = limiter, store = store, meterRegistry = meterRegistry)
    }

    private data class Replica(
        val limiter: Bucket4jRateLimiter,
        val store: RedisBucket4jRateLimitStore,
        val meterRegistry: SimpleMeterRegistry,
    )

    private fun testProperties(redisUri: String): RateLimitProperties = RateLimitProperties(
        enabled = true,
        store = RateLimitProperties.StoreConfig(
            distributedEnabled = true,
            type = RateLimitProperties.StoreType.REDIS,
            redis = RateLimitProperties.RedisStoreConfig(
                uri = redisUri,
                keyPrefix = "ratelimit:test:",
            ),
        ),
        auth = RateLimitProperties.AuthRateLimitConfig(enabled = false),
        business = RateLimitProperties.BusinessRateLimitConfig(enabled = false),
        resume = RateLimitProperties.ResumeRateLimitConfig(enabled = false),
        waitlist = RateLimitProperties.WaitlistRateLimitConfig(
            enabled = true,
            limit = RateLimitProperties.BandwidthLimit(
                name = "waitlist-distributed-test",
                capacity = 3,
                refillTokens = 3,
                refillDuration = Duration.ofMinutes(1),
            ),
        ),
    )

    companion object {
        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
    }
}
