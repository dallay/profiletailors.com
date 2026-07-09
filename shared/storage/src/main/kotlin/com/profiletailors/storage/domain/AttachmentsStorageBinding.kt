package com.profiletailors.storage.domain

/**
 * Single point of resolution for the attachments storage target.
 *
 * Features (media, preview, publishing) consume the [providerName] as the
 * logical name of the attachment capability. The [bucketName] and [storage]
 * are resolved once from `platform.storage.providers.attachments.*` and shared
 * across contexts so that uploads, preview resolution and publishing reads
 * always target the same physical bucket.
 */
data class AttachmentsStorageBinding(val providerName: String, val bucketName: String, val storage: Storage)
