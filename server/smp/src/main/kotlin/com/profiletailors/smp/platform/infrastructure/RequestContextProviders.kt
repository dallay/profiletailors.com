package com.profiletailors.smp.platform.infrastructure

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.RequestPathProvider
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext

class ReactorContextPrincipalContextProvider : PrincipalContextProvider {
    override suspend fun current(): PrincipalContext? = currentCoroutineContext()[ReactorContext]
        ?.context
        ?.getOrEmpty<PrincipalContext>(RequestContextKeys.principal)
        ?.orElse(null)
}

class ReactorContextResourceContextProvider : ResourceContextProvider {
    override suspend fun current(): ResourceContext? = currentCoroutineContext()[ReactorContext]
        ?.context
        ?.getOrEmpty<ResourceContext>(RequestContextKeys.resource)
        ?.orElse(null)
}

class ReactorContextRequestPathProvider : RequestPathProvider {
    override suspend fun current(): String? = currentCoroutineContext()[ReactorContext]
        ?.context
        ?.getOrEmpty<String>(RequestContextKeys.requestPath)
        ?.orElse(null)
}
