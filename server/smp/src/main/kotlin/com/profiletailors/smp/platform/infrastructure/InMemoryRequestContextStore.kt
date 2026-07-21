package com.profiletailors.smp.platform.infrastructure

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore

class InMemoryRequestContextStore : RequestContextStore {
    @Volatile
    private var principalContext: PrincipalContext? = null

    @Volatile
    private var resourceContext: ResourceContext? = null

    @Volatile
    private var requestPath: String? = null

    override fun currentPrincipalContext(): PrincipalContext? = principalContext

    override fun setPrincipalContext(context: PrincipalContext?) {
        principalContext = context
    }

    override fun currentResourceContext(): ResourceContext? = resourceContext

    override fun setResourceContext(context: ResourceContext?) {
        resourceContext = context
    }

    override fun currentRequestPath(): String? = requestPath

    override fun setRequestPath(path: String?) {
        requestPath = path
    }

    override fun clear() {
        principalContext = null
        resourceContext = null
        requestPath = null
    }
}
