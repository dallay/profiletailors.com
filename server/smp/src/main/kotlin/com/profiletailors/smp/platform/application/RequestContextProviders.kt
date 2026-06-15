package com.profiletailors.smp.platform.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.RequestPathProvider
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.platform.domain.RequestContextStore

class StoreBackedPrincipalContextProvider(
    private val requestContextStore: RequestContextStore,
) : PrincipalContextProvider {
    override suspend fun current(): PrincipalContext? = requestContextStore.currentPrincipalContext()
}

class StoreBackedResourceContextProvider(
    private val requestContextStore: RequestContextStore,
) : ResourceContextProvider {
    override fun current(): ResourceContext? = requestContextStore.currentResourceContext()
}

class StoreBackedRequestPathProvider(
    private val requestContextStore: RequestContextStore,
) : RequestPathProvider {
    override fun current(): String? = requestContextStore.currentRequestPath()
}
