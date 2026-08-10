package com.profiletailors.smp.credentials.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class CredentialType {
    JWT,
    SERVICE_ACCOUNT,
    API_KEY,
}
