package com.profiletailors.smp.shortlinks.domain

import com.profiletailors.common.domain.AggregateRoot
import java.time.Instant

@AggregateRoot
data class Link(
    val id: LinkId,
    val ownerId: OwnerId,
    val domainId: DomainId,
    val shortCode: ShortCode,
    val destinationUrl: DestinationUrl,
    val status: LinkStatus,
    val expiresAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Long,
) {
    fun isExpired(now: Instant): Boolean = expiresAt != null && !now.isBefore(expiresAt)

    fun effectiveStatus(now: Instant): LinkStatus = when {
        status == LinkStatus.ACTIVE && isExpired(now) -> LinkStatus.EXPIRED
        else -> status
    }

    fun updateDestination(newDestinationUrl: DestinationUrl, newExpiresAt: Instant?, now: Instant): Link {
        requireMutable(now)
        return copy(
            destinationUrl = newDestinationUrl,
            expiresAt = newExpiresAt,
            updatedAt = now,
            version = version + 1,
        )
    }

    fun disable(now: Instant): Link {
        require(status == LinkStatus.ACTIVE) {
            throw LinkStateTransitionException(id, status, LinkStatus.DISABLED)
        }
        return copy(
            status = LinkStatus.DISABLED,
            updatedAt = now,
            version = version + 1,
        )
    }

    fun enable(now: Instant): Link {
        if (status != LinkStatus.DISABLED || isExpired(now)) {
            throw LinkStateTransitionException(id, status, LinkStatus.ACTIVE)
        }
        return copy(
            status = LinkStatus.ACTIVE,
            updatedAt = now,
            version = version + 1,
        )
    }

    fun softDelete(now: Instant): Link {
        require(status != LinkStatus.DELETED) {
            throw LinkStateTransitionException(id, status, LinkStatus.DELETED)
        }
        return copy(
            status = LinkStatus.DELETED,
            deletedAt = now,
            updatedAt = now,
            version = version + 1,
        )
    }

    fun quarantine(now: Instant): Link {
        require(status == LinkStatus.ACTIVE) {
            throw LinkStateTransitionException(id, status, LinkStatus.QUARANTINED)
        }
        return copy(
            status = LinkStatus.QUARANTINED,
            updatedAt = now,
            version = version + 1,
        )
    }

    private fun requireMutable(now: Instant) {
        require(status == LinkStatus.ACTIVE || status == LinkStatus.DISABLED) {
            throw LinkStateTransitionException(id, status, status)
        }
        require(!isExpired(now)) {
            throw LinkStateTransitionException(id, status, LinkStatus.EXPIRED)
        }
    }

    companion object {
        fun create(
            id: LinkId,
            ownerId: OwnerId,
            domainId: DomainId,
            shortCode: ShortCode,
            destinationUrl: DestinationUrl,
            expiresAt: Instant?,
            now: Instant,
        ): Link = Link(
            id = id,
            ownerId = ownerId,
            domainId = domainId,
            shortCode = shortCode,
            destinationUrl = destinationUrl,
            status = LinkStatus.ACTIVE,
            expiresAt = expiresAt,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            version = 1,
        )
    }
}
