package com.profiletailors.smp.shortlinks.domain.event

import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.ShortCode
import java.time.Instant

data class LinkCreatedEvent(
    val linkId: LinkId,
    val domainId: DomainId,
    val shortCode: ShortCode,
    val version: Long,
    val occurredAt: Instant,
)

data class LinkChangedEvent(
    val linkId: LinkId,
    val domainId: DomainId,
    val shortCode: ShortCode,
    val newStatus: LinkStatus,
    val version: Long,
    val occurredAt: Instant,
)

data class ClickRecordedEvent(
    val eventId: String,
    val linkId: LinkId,
    val timestamp: Instant,
    val referrerDomain: String?,
    val userAgent: String?,
)
