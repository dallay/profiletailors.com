package com.profiletailors.smp.shortlinks.application

import com.profiletailors.smp.shortlinks.domain.Link

internal fun Link.toResult(shortUrlBase: String): LinkResult = LinkResult(
    id = id.value,
    shortCode = shortCode.value,
    shortUrl = "$shortUrlBase/${shortCode.value}",
    destinationUrl = destinationUrl.value,
    status = status.name,
    createdAt = createdAt,
    expiresAt = expiresAt,
    version = version,
)
