package com.profiletailors.leadcapture.common

private const val LEAD_METADATA_MAX_FIELD_LENGTH = 500

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
) {
    init {
        val fields = listOf(
            utmSource,
            utmMedium,
            utmCampaign,
            utmContent,
            utmTerm,
            referrer,
            pagePath,
            userAgentFamily,
            consentVersion,
        )
        require(fields.all { it == null || it.length <= LEAD_METADATA_MAX_FIELD_LENGTH }) {
            "Lead metadata field must be at most $LEAD_METADATA_MAX_FIELD_LENGTH characters"
        }
    }
}
