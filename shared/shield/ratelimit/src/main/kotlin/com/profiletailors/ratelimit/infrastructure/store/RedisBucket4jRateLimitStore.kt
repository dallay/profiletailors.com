package com.profiletailors.ratelimit.infrastructure.store

import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * Redis-backed Bucket4j store using Lettuce and Bucket4j's Redis proxy manager.
 */
class RedisBucket4jRateLimitStore(private val properties: RateLimitProperties) :
    RateLimitStore,
    AutoCloseable {
    override val source: BucketSource = BucketSource.DISTRIBUTED

    private val logger = LoggerFactory.getLogger(RedisBucket4jRateLimitStore::class.java)
    private val keyPrefix = properties.store.redis.keyPrefix.trim().removeSuffix(":")

    private val redisClient: RedisClient = RedisClient.create(properties.store.redis.uri)
    private val connection: StatefulRedisConnection<String, ByteArray> = redisClient.connect(
        object : RedisCodec<String, ByteArray> {
            override fun encodeKey(key: String): ByteBuffer = StringCodec.UTF8.encodeKey(key)
            override fun decodeKey(bytes: ByteBuffer): String = StringCodec.UTF8.decodeKey(bytes)
            override fun encodeValue(value: ByteArray): ByteBuffer = ByteArrayCodec.INSTANCE.encodeValue(value)
            override fun decodeValue(bytes: ByteBuffer): ByteArray = ByteArrayCodec.INSTANCE.decodeValue(bytes)
        },
    )
    private val proxyManager: ProxyManager<String> = LettuceBasedProxyManager.builderFor(connection)
        .build()

    override fun resolveBucket(cacheKey: String, configuration: BucketConfiguration): Bucket {
        val prefixedKey = if (keyPrefix.isBlank()) cacheKey else "$keyPrefix:$cacheKey"
        logger.debug("Resolving distributed bucket for key={} (prefixed as {})", cacheKey, prefixedKey)
        return proxyManager.builder()
            .build(prefixedKey, configuration)
    }

    override fun close() {
        connection.close()
        redisClient.shutdown()
    }
}
