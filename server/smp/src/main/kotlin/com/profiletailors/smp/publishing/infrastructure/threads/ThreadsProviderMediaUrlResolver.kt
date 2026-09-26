package com.profiletailors.smp.publishing.infrastructure.threads

import com.profiletailors.smp.publishing.domain.ProviderMediaUrl
import com.profiletailors.smp.publishing.domain.ProviderMediaUrlResolver
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.storage.domain.AttachmentsStorageBinding
import com.profiletailors.storage.domain.PresignableStorage
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

class ThreadsProviderMediaUrlResolver(
    private val properties: ThreadsPublishingProperties,
    private val storage: AttachmentsStorageBinding,
    private val clock: Clock = Clock.systemUTC(),
    private val safetyMargin: Duration = ThreadsPublishingProperties.MEDIA_URL_SAFETY_MARGIN,
) : ProviderMediaUrlResolver {
    override suspend fun resolve(
        workspaceId: String,
        assets: List<PublicationAsset>,
        deadline: Instant,
    ): List<ProviderMediaUrl> {
        val now = clock.instant()
        val minimumExpiry = deadline.plus(safetyMargin)
        require(minimumExpiry.isAfter(now)) { "Threads media URL deadline must be in the future." }
        val requiredTtl = Duration.between(now, minimumExpiry)
        require(requiredTtl <= properties.mediaUrlTtl) {
            "Threads media URL TTL does not cover the publishing workflow."
        }
        val presignableStorage = storage.storage as? PresignableStorage
            ?: throw IllegalStateException("Threads publishing requires presignable attachment storage.")
        return assets.map { asset ->
            require(asset.workspaceId == workspaceId) { "Publication media belongs to another workspace." }
            require(asset.status == PublicationAssetStatus.READY) { "Threads media must be ready before publishing." }
            val key = requireNotNull(asset.storageKey) { "Threads media is missing a storage key." }
            val expirySeconds = ceil(requiredTtl.toMillis() / MILLIS_PER_SECOND.toDouble()).toLong()
            val url = presignableStorage.presignGet(storage.bucketName, key, expirySeconds)
            check(URI.create(url).scheme.equals("https", ignoreCase = true)) {
                "Threads media URLs must use HTTPS."
            }
            ProviderMediaUrl(url = url, expiresAt = now.plusSeconds(expirySeconds))
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
