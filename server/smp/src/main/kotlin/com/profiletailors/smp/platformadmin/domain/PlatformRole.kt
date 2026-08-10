package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class PlatformRole {
    PLATFORM_OWNER,
    PLATFORM_OPERATOR,
    SUPPORT_AGENT,
    AUDITOR,
}
