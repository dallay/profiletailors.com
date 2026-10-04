package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.smp.shortlinks.domain.CacheLookup
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.LinkFinderRepository
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.RedirectEntry
import com.profiletailors.smp.shortlinks.domain.ShortCode
import java.time.Clock

@Service
internal class ResolveLinkHandler(
    private val linkCachePort: LinkCachePort,
    private val linkFinderRepository: LinkFinderRepository,
    private val clock: Clock,
    private val shortLinksProperties: ShortLinksConfigProperties,
) : QueryHandler<ResolveLinkQuery, ResolveResult> {

    override suspend fun handle(query: ResolveLinkQuery): ResolveResult {
        val domain = query.domain.ifBlank { shortLinksProperties.publicHost }.trim().lowercase()

        when (val cached = linkCachePort.get(domain, query.shortCode)) {
            is CacheLookup.Present -> return resolveFromEntry(cached.entry, query.shortCode)
            CacheLookup.Negative -> throw LinkNotFoundApplicationException(query.shortCode)
            CacheLookup.Miss -> Unit
        }

        val link = linkFinderRepository.findByDomainAndShortCode(
            domainId = DomainId.fromHost(domain),
            shortCode = ShortCode(query.shortCode),
        )

        if (link == null) {
            linkCachePort.putNegative(domain, query.shortCode)
            throw LinkNotFoundApplicationException(query.shortCode)
        }

        val entry = RedirectEntry(
            linkId = link.id,
            destination = link.destinationUrl,
            status = link.effectiveStatus(clock.instant()),
            expiresAt = link.expiresAt,
            version = link.version,
        )

        linkCachePort.put(domain, query.shortCode, entry)
        return resolveFromEntry(entry, query.shortCode)
    }

    private fun resolveFromEntry(entry: RedirectEntry, shortCode: String): ResolveResult {
        val now = clock.instant()
        val effectiveStatus = when {
            entry.status == LinkStatus.ACTIVE && entry.expiresAt != null && !now.isBefore(entry.expiresAt) ->
                LinkStatus.EXPIRED
            else -> entry.status
        }

        return when (effectiveStatus) {
            LinkStatus.ACTIVE -> ResolveResult(
                destinationUrl = entry.destination.value,
                status = effectiveStatus.name,
                linkId = entry.linkId.value,
            )
            LinkStatus.EXPIRED, LinkStatus.DISABLED, LinkStatus.DELETED, LinkStatus.QUARANTINED ->
                throw statusException(effectiveStatus, shortCode)
        }
    }

    private fun statusException(status: LinkStatus, shortCode: String): ShortLinksApplicationException = when (status) {
        LinkStatus.EXPIRED -> LinkExpiredApplicationException(shortCode)
        LinkStatus.DISABLED -> LinkDisabledApplicationException(shortCode)
        LinkStatus.DELETED -> LinkDeletedApplicationException(shortCode)
        LinkStatus.QUARANTINED -> LinkQuarantinedApplicationException(shortCode)
        LinkStatus.ACTIVE -> error("Active links do not have a status exception")
    }
}
