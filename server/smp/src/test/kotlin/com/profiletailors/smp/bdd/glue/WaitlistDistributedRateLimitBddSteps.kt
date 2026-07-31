package com.profiletailors.smp.bdd.glue

import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.infrastructure.adapter.ApiKeyParser
import com.profiletailors.ratelimit.infrastructure.adapter.Bucket4jRateLimiter
import com.profiletailors.ratelimit.infrastructure.config.BucketConfigurationFactory
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import com.profiletailors.ratelimit.infrastructure.store.RedisBucket4jRateLimitStore
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Clock
import java.time.Duration

class WaitlistDistributedRateLimitBddSteps {

    private lateinit var limiterA: Bucket4jRateLimiter
    private lateinit var limiterB: Bucket4jRateLimiter
    private lateinit var storeA: RedisBucket4jRateLimitStore
    private lateinit var storeB: RedisBucket4jRateLimitStore

    private val consumptions = mutableListOf<RateLimitResult>()

    @Before("@distributed")
    fun resetScenarioState() {
        if (!redis.isRunning) {
            redis.start()
        }
        consumptions.clear()
    }

    @After("@distributed")
    fun closeStores() {
        if (this::storeA.isInitialized) {
            storeA.close()
        }
        if (this::storeB.isInitialized) {
            storeB.close()
        }
    }

    @Given("distributed WAITLIST rate limiting is configured with capacity 3 per minute")
    fun distributedWaitlistRateLimitConfigured() {
        val properties = rateLimitProperties()

        storeA = RedisBucket4jRateLimitStore(properties)
        storeB = RedisBucket4jRateLimitStore(properties)

        limiterA = Bucket4jRateLimiter(
            configurationFactory = BucketConfigurationFactory(properties),
            apiKeyParser = ApiKeyParser(properties),
            metrics = RateLimitMetrics(SimpleMeterRegistry()),
            rateLimitStore = storeA,
            clock = Clock.systemUTC(),
        )

        limiterB = Bucket4jRateLimiter(
            configurationFactory = BucketConfigurationFactory(properties),
            apiKeyParser = ApiKeyParser(properties),
            metrics = RateLimitMetrics(SimpleMeterRegistry()),
            rateLimitStore = storeB,
            clock = Clock.systemUTC(),
        )
    }

    @When("replica A consumes one WAITLIST token for client {string} on waitlist {string}")
    fun replicaAConsumes(clientIdentifier: String, waitlistKey: String) {
        consumptions += consume(limiterA, clientIdentifier, waitlistKey)
    }

    @And("replica B consumes one WAITLIST token for client {string} on waitlist {string}")
    fun replicaBConsumes(clientIdentifier: String, waitlistKey: String) {
        consumptions += consume(limiterB, clientIdentifier, waitlistKey)
    }

    @Then("the first {int} distributed WAITLIST consumptions should be allowed")
    fun firstConsumptionsAllowed(allowedCount: Int) {
        assertEquals(allowedCount, consumptions.take(allowedCount).size)
        consumptions.take(allowedCount).forEach { result ->
            assertTrue(result is RateLimitResult.Allowed)
        }
    }

    @And("the {int}th distributed WAITLIST consumption should be denied")
    fun nthConsumptionDenied(index: Int) {
        val result = consumptions[index - 1]
        assertTrue(result is RateLimitResult.Denied)
    }

    private fun consume(limiter: Bucket4jRateLimiter, clientIdentifier: String, waitlistKey: String): RateLimitResult =
        runBlocking {
            val endpoint = "/api/waitlists/$waitlistKey/entries"
            limiter.consumeToken(
                clientIdentifier,
                RateLimitStrategy.WAITLIST,
                "$clientIdentifier:$endpoint",
            )
        }

    private fun rateLimitProperties(): RateLimitProperties {
        val redisUri = "redis://${redis.host}:${redis.firstMappedPort}"
        return RateLimitProperties(
            enabled = true,
            store = RateLimitProperties.StoreConfig(
                distributedEnabled = true,
                type = RateLimitProperties.StoreType.REDIS,
                redis = RateLimitProperties.RedisStoreConfig(
                    uri = redisUri,
                    keyPrefix = "bdd:ratelimit:",
                ),
            ),
            auth = RateLimitProperties.AuthRateLimitConfig(enabled = false),
            business = RateLimitProperties.BusinessRateLimitConfig(enabled = false),
            resume = RateLimitProperties.ResumeRateLimitConfig(enabled = false),
            waitlist = RateLimitProperties.WaitlistRateLimitConfig(
                enabled = true,
                limit = RateLimitProperties.BandwidthLimit(
                    name = "waitlist-bdd",
                    capacity = 3,
                    refillTokens = 3,
                    refillDuration = Duration.ofMinutes(1),
                ),
            ),
        )
    }

    companion object {
        private val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
    }
}
