package com.profiletailors.smp.media.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the media context.
 *
 * Covers upload limits, preview URL signing, stale-asset cleanup, and storage bucket routing.
 */
@ConfigurationProperties(prefix = "media")
class MediaProperties(
    val previewSigningSecret: String = "",
    val maxConcurrentUploads: Int = 5,
    val maxCreationsPerHour: Int = 200,
    val storage: Storage = Storage(),
    val stale: Stale = Stale(),
    val dedup: Dedup = Dedup(),
    val previewUrlExpirySeconds: Long = 3600,
) {
    class Storage(val bucket: String = "attachments")

    class Stale(val thresholdHours: Long = 2, val gracePeriodMinutes: Long = 30)

    class Dedup(val enabled: Boolean = true)
}
