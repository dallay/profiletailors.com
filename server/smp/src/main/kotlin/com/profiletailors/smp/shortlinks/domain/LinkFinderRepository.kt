package com.profiletailors.smp.shortlinks.domain

interface LinkFinderRepository {
    suspend fun findByDomainAndShortCode(domainId: DomainId, shortCode: ShortCode): Link?

    suspend fun findByOwner(ownerId: OwnerId, limit: Int, afterCreatedAt: java.time.Instant?): List<Link>
}
