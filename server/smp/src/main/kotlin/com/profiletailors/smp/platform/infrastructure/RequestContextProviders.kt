package com.profiletailors.smp.platform.infrastructure

import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext

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
