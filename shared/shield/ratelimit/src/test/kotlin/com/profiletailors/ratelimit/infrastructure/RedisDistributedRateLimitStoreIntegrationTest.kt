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
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisDistributedRateLimitStoreIntegrationTest {

    @Test
    fun `should share buckets across two replicas via redis backend`() = runTest {
        assumeTrue(
            DockerClientFactory.instance().isDockerAvailable,
            "Docker must be available to run the Redis integration test",
        )

        val redisContainer = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
        redisContainer.start()
        val properties = RateLimitProperties(
            enabled = true,
            store = RateLimitProperties.StoreConfig(
                type = RateLimitProperties.StoreType.REDIS,
                redis = RateLimitProperties.RedisConfig(
                    uri = "redis://${redisContainer.host}:${redisContainer.getMappedPort(6379)}",
                    keyPrefix = "test-ratelimit:",
                ),
            ),
            auth = RateLimitProperties.AuthRateLimitConfig(enabled = false),
            business = RateLimitProperties.BusinessRateLimitConfig(enabled = false),
            resume = RateLimitProperties.ResumeRateLimitConfig(enabled = false),
            waitlist = RateLimitProperties.WaitlistRateLimitConfig(
                enabled = true,
                limit = RateLimitProperties.BandwidthLimit(
                    name = "waitlist-test",
                    capacity = 1,
                    refillTokens = 1,
                    refillDuration = Duration.ofMinutes(1),
                ),
            ),
        )

        val configFactory = BucketConfigurationFactory(properties)
        val apiKeyParser = ApiKeyParser(properties)
        val metrics = RateLimitMetrics(SimpleMeterRegistry())

        val storeA = RedisBucket4jRateLimitStore(properties)
        val storeB = RedisBucket4jRateLimitStore(properties)

        val limiterA = Bucket4jRateLimiter(
            configurationFactory = configFactory,
            apiKeyParser = apiKeyParser,
            metrics = metrics,
            properties = properties,
            rateLimitStore = storeA,
        )
        val limiterB = Bucket4jRateLimiter(
            configurationFactory = configFactory,
            apiKeyParser = apiKeyParser,
            metrics = metrics,
            properties = properties,
            rateLimitStore = storeB,
        )

        try {
            val identifier = "shared-ip"
            val bucketIdentity = "$identifier:/api/waitlists/profile-tailors-launch/entries"

            limiterA.consumeToken(identifier, RateLimitStrategy.WAITLIST, bucketIdentity)
                .shouldBeInstanceOf<RateLimitResult.Allowed>()

            limiterB.consumeToken(identifier, RateLimitStrategy.WAITLIST, bucketIdentity)
                .shouldBeInstanceOf<RateLimitResult.Denied>()
        } finally {
            storeA.close()
            storeB.close()
            redisContainer.stop()
        }
    }
}
