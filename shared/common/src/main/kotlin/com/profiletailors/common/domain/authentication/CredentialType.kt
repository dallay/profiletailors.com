package com.profiletailors.common.domain.authentication

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class CredentialType {
    JWT,
    SERVICE_ACCOUNT,
    API_KEY,
}
