package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler

data class CurrentTermsVersion(val version: String)

class GetCurrentTermsVersionQuery : Query<CurrentTermsVersion>

fun interface CurrentTermsVersionProvider {
    fun currentTermsVersion(): String
}

@Service
internal class GetCurrentTermsVersionHandler(private val currentTermsVersionProvider: CurrentTermsVersionProvider) :
    QueryHandler<GetCurrentTermsVersionQuery, CurrentTermsVersion> {
    override suspend fun handle(query: GetCurrentTermsVersionQuery): CurrentTermsVersion =
        CurrentTermsVersion(currentTermsVersionProvider.currentTermsVersion())
}

const val CURRENT_TERMS_VERSION = "terms-v1.0.0"

@Service
internal class IdentityCurrentTermsVersionProvider : CurrentTermsVersionProvider {
    override fun currentTermsVersion(): String = CURRENT_TERMS_VERSION
}
