package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.platform.infrastructure.ReactorContextPrincipalContextProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class IdentityPlatformBridgeConfiguration {

    @Bean
    @Primary
    fun principalContextProvider(): PrincipalContextProvider = DelegatingPrincipalContextProvider(
        securityContextProvider = SecurityContextPrincipalContextProvider(),
        requestContextProvider = ReactorContextPrincipalContextProvider(),
    )
}

class DelegatingPrincipalContextProvider(
    private val securityContextProvider: SecurityContextPrincipalContextProvider,
    private val requestContextProvider: PrincipalContextProvider,
) : PrincipalContextProvider {
    override suspend fun current() = securityContextProvider.current() ?: requestContextProvider.current()
}
