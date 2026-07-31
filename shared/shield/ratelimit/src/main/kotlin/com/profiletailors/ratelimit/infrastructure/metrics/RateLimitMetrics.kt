package com.profiletailors.ratelimit.infrastructure.metrics

import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.infrastructure.store.BucketSource
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val STRATEGY = "strategy"

private const val RESULT = "result"

private const val BUCKET_SOURCE = "bucket_source"

/**
 * Metrics collector for rate limiting operations.
 * Tracks request counts, denial rates, cache size, and performance metrics.
 *
 * Metrics exposed:
 * - rate_limit.requests.total: Counter for all rate limit checks (tags: strategy, result)
 * - rate_limit.denied.total: Counter for denied requests (tags: strategy)
 * - rate_limit.cache.size: Gauge for bucket cache size
 * - rate_limit.token.consumption.time: Timer for token consumption latency (tags: strategy, result)
 *
 * @property meterRegistry Micrometer meter registry for metric registration
 * @since 2.0.0
 */
@Component
class RateLimitMetrics(private val meterRegistry: MeterRegistry) {

    private val cacheSize = AtomicInteger(0)
    private val cacheSizeBySource = ConcurrentHashMap<BucketSource, AtomicInteger>()

    init {
        // Register gauge for cache size
        meterRegistry.gauge(
            "rate_limit.cache.size",
            listOf(Tag.of("type", "bucket")),
            cacheSize,
        )
    }

    /**
     * Records a rate limit check with the result.
     *
     * @param strategy The rate limiting strategy applied
     * @param result The result of the rate limit check
     */
    fun recordRateLimitCheck(strategy: RateLimitStrategy, result: RateLimitResult) {
        recordRateLimitCheck(strategy, result, null)
    }

    fun recordRateLimitCheck(strategy: RateLimitStrategy, result: RateLimitResult, source: BucketSource?) {
        val resultTag = when (result) {
            is RateLimitResult.Allowed -> "allowed"
            is RateLimitResult.Denied -> "denied"
        }

        // Increment total requests counter
        Counter.builder("rate_limit.requests.total")
            .tag(STRATEGY, strategy.name.lowercase())
            .tag(RESULT, resultTag)
            .description("Total number of rate limit checks")
            .register(meterRegistry)
            .increment()

        if (source != null) {
            Counter.builder("rate_limit.requests.by_source.total")
                .tag(STRATEGY, strategy.name.lowercase())
                .tag(RESULT, resultTag)
                .tag(BUCKET_SOURCE, source.name.lowercase())
                .description("Total number of rate limit checks grouped by bucket source")
                .register(meterRegistry)
                .increment()
        }

        // If denied, also increment denied counter
        if (result is RateLimitResult.Denied) {
            Counter.builder("rate_limit.denied.total")
                .tag(STRATEGY, strategy.name.lowercase())
                .description("Total number of denied requests due to rate limiting")
                .register(meterRegistry)
                .increment()

            if (source != null) {
                Counter.builder("rate_limit.denied.by_source.total")
                    .tag(STRATEGY, strategy.name.lowercase())
                    .tag(BUCKET_SOURCE, source.name.lowercase())
                    .description("Total denied requests grouped by bucket source")
                    .register(meterRegistry)
                    .increment()
            }
        }
    }

    /**
     * Records the time taken to consume a token.
     *
     * @param strategy The rate limiting strategy applied
     * @param operation A function that performs the token consumption
     * @return The result of the operation
     */
    suspend fun <T : Any> recordTokenConsumption(strategy: RateLimitStrategy, operation: suspend () -> T): T =
        recordTokenConsumption(strategy, null, operation)

    suspend fun <T : Any> recordTokenConsumption(
        strategy: RateLimitStrategy,
        source: BucketSource?,
        operation: suspend () -> T,
    ): T {
        val timer = Timer.builder("rate_limit.token.consumption.time")
            .tag(STRATEGY, strategy.name.lowercase())
            .description("Time taken to consume a rate limit token")
            .register(meterRegistry)

        val sourceTimer = source?.let {
            Timer.builder("rate_limit.token.consumption.by_source.time")
                .tag(STRATEGY, strategy.name.lowercase())
                .tag(BUCKET_SOURCE, it.name.lowercase())
                .description("Time taken to consume a rate limit token grouped by bucket source")
                .register(meterRegistry)
        }

        val sample = Timer.start(meterRegistry)
        return try {
            operation()
        } finally {
            sample.stop(timer)
            sourceTimer?.let { sample.stop(it) }
        }
    }

    /**
     * Updates the cache size gauge.
     *
     * @param size The current size of the bucket cache
     */
    fun updateCacheSize(size: Int, source: BucketSource = BucketSource.LOCAL) {
        cacheSize.set(size)
        val gauge = cacheSizeBySource.computeIfAbsent(source) {
            val sourceGauge = AtomicInteger(0)
            meterRegistry.gauge(
                "rate_limit.cache.size.by_source",
                listOf(Tag.of(BUCKET_SOURCE, source.name.lowercase())),
                sourceGauge,
            )
            sourceGauge
        }
        gauge.set(size)
    }
}
