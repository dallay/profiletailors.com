package com.profiletailors.smp.platform.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.RequestPathProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.platform.application.StoreBackedPrincipalContextProvider
import com.profiletailors.smp.platform.application.StoreBackedRequestPathProvider
import com.profiletailors.smp.platform.application.StoreBackedResourceContextProvider
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platform.infrastructure.http.RequestPathWebFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.WebFilter
import java.time.Clock

@Configuration
class PlatformBootstrapConfiguration {

    @Bean
    fun requestContextStore(): RequestContextStore = InMemoryRequestContextStore()

    @Bean("storeBackedPrincipalContextProvider")
    fun storeBackedPrincipalContextProvider(requestContextStore: RequestContextStore): PrincipalContextProvider =
        StoreBackedPrincipalContextProvider(requestContextStore)

    @Bean
    fun resourceContextProvider(requestContextStore: RequestContextStore): ResourceContextProvider =
        StoreBackedResourceContextProvider(requestContextStore)

    @Bean
    fun requestPathProvider(requestContextStore: RequestContextStore): RequestPathProvider =
        StoreBackedRequestPathProvider(requestContextStore)

    @Bean
    fun requestPathWebFilter(requestContextStore: RequestContextStore): WebFilter =
        RequestPathWebFilter(requestContextStore)

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper()

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
