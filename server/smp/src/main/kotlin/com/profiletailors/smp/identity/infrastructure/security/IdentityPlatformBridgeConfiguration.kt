package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.platform.domain.RequestContextStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class IdentityPlatformBridgeConfiguration {

    @Bean
    @Primary
    fun principalContextProvider(requestContextStore: RequestContextStore): PrincipalContextProvider =
        DelegatingPrincipalContextProvider(
            securityContextProvider = SecurityContextPrincipalContextProvider(),
            requestContextStore = requestContextStore,
        )
}

class DelegatingPrincipalContextProvider(
    private val securityContextProvider: SecurityContextPrincipalContextProvider,
    private val requestContextStore: RequestContextStore,
) : PrincipalContextProvider {
    override suspend fun current() =
        securityContextProvider.current() ?: requestContextStore.currentPrincipalContext()
}
