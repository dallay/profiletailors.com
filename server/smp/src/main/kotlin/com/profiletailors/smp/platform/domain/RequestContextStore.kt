package com.profiletailors.smp.platform.domain

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.ResourceContext

interface RequestContextStore {
    fun currentPrincipalContext(): PrincipalContext?

    fun setPrincipalContext(context: PrincipalContext?)

    fun currentResourceContext(): ResourceContext?

    fun setResourceContext(context: ResourceContext?)

    fun currentRequestPath(): String?

    fun setRequestPath(path: String?)

    fun clear()
}
