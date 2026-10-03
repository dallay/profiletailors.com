package com.profiletailors.smp.shortlinks.domain

data class RedirectEntry(
    val linkId: LinkId,
    val destination: DestinationUrl,
    val status: LinkStatus,
    val expiresAt: java.time.Instant?,
    val version: Long,
)

sealed interface CacheLookup {
    data class Present(val entry: RedirectEntry) : CacheLookup
    data object Negative : CacheLookup
    data object Miss : CacheLookup
}

interface LinkCachePort {
    suspend fun get(domain: String, shortCode: String): CacheLookup

    suspend fun put(domain: String, shortCode: String, entry: RedirectEntry)

    suspend fun putNegative(domain: String, shortCode: String)

    suspend fun evict(domain: String, shortCode: String)
}
