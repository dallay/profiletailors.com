package com.profiletailors.smp.shortlinks.domain

interface LinkRepository {
    suspend fun save(link: Link): Link

    suspend fun findById(id: LinkId, ownerId: OwnerId): Link?

    suspend fun updateWithVersion(link: Link, expectedVersion: Long, ownerId: OwnerId): Boolean
}
