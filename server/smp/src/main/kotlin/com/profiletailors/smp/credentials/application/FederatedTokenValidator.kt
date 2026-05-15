package com.profiletailors.smp.credentials.application

import com.profiletailors.smp.credentials.domain.ValidatedToken

interface FederatedTokenValidator<RAW_TOKEN> {
    suspend fun validate(token: RAW_TOKEN): ValidatedToken
}
