package com.profiletailors.smp.platform.infrastructure

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.ResourceContext

object RequestContextKeys {
    val principal = RequestContextKey<PrincipalContext>("principal")
    val resource = RequestContextKey<ResourceContext>("resource")
    val requestPath = RequestContextKey<String>("requestPath")
}

data class RequestContextKey<T : Any>(val name: String)
