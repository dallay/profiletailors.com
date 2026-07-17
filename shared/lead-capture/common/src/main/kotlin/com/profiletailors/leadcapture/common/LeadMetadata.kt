package com.profiletailors.leadcapture.common

data class LeadMetadata(
    val utmSource: String? = null,
    val utmMedium: String? = null,
    val utmCampaign: String? = null,
    val utmContent: String? = null,
    val utmTerm: String? = null,
    val referrer: String? = null,
    val pagePath: String? = null,
    val userAgentFamily: String? = null,
    val consentVersion: String? = null,
)
