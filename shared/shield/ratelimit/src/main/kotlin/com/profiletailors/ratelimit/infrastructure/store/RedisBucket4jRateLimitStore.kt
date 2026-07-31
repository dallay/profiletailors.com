package com.profiletailors.ratelimit.infrastructure.store

import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

class RedisBucket4jRateLimitStore(private val properties: RateLimitProperties) :
    RateLimitStore,
    AutoCloseable {

    private val logger = LoggerFactory.getLogger(RedisBucket4jRateLimitStore::class.java)

    private val redisClient: RedisClient = RedisClient.create(properties.store.redis.uri)
    private val connection: StatefulRedisConnection<ByteArray, ByteArray> = redisClient.connect(ByteArrayCodec.INSTANCE)
    private val proxyManager: ProxyManager<ByteArray> = LettuceBasedProxyManager.builderFor(connection).build()

    override val source: BucketSource = BucketSource.DISTRIBUTED

    init {
        logger.info(
            "Initialized distributed Redis rate-limit store: uri={}, keyPrefix={}",
            properties.store.redis.uri,
            properties.store.redis.keyPrefix,
        )
    }

    override suspend fun resolveBucket(cacheKey: String, configuration: BucketConfiguration): Bucket =
        withContext(Dispatchers.IO) {
            val key = (properties.store.redis.keyPrefix + cacheKey).toByteArray(StandardCharsets.UTF_8)
            proxyManager.builder().build(key) { configuration }
        }

    override fun close() {
        try {
            connection.close()
        } finally {
            redisClient.shutdown()
        }
    }
}
