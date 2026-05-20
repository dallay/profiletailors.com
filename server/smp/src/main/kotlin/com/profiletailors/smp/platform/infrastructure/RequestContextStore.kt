package com.profiletailors.smp.platform.infrastructure

import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.domain.ResourceContext

interface RequestContextStore {
    fun currentPrincipalContext(): PrincipalContext?

    fun setPrincipalContext(context: PrincipalContext?)

    fun currentResourceContext(): ResourceContext?

    fun setResourceContext(context: ResourceContext?)

    fun clear()
}

class InMemoryRequestContextStore : RequestContextStore {
    private var principalContext: PrincipalContext? = null
    private var resourceContext: ResourceContext? = null

    override fun currentPrincipalContext(): PrincipalContext? = principalContext

    override fun setPrincipalContext(context: PrincipalContext?) {
        principalContext = context
    }

    override fun currentResourceContext(): ResourceContext? = resourceContext

    override fun setResourceContext(context: ResourceContext?) {
        resourceContext = context
    }

    override fun clear() {
        principalContext = null
        resourceContext = null
    }
}
