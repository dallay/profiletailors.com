package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import reactor.kotlin.core.publisher.switchIfEmpty
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.security.core.context.ReactiveSecurityContextHolder

class SecurityContextPrincipalContextProvider : PrincipalContextProvider {
    override suspend fun current(): PrincipalContext? =
        ReactiveSecurityContextHolder.getContext()
            .mapNotNull { securityContext -> securityContext.authentication?.principal as? AuthenticatedPrincipal }
            .map { authenticatedPrincipal -> authenticatedPrincipal.context }
            .switchIfEmpty { reactor.core.publisher.Mono.empty() }
            .awaitSingleOrNull()
}
