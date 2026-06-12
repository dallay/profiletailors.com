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

    /**
     * Singleton store shared across the request pipeline.
     *
     * In a pure WebFlux reactive application `@RequestScope` is not available because
     * request state lives in Reactor Context, not thread-local storage.  The idiomatic
     * per-request fix would be to migrate to Reactor Context (SubscriberContext) so each
     * subscription sees its own data without shared mutable state.
     *
     * Until that migration, the existing filters (RequestPathWebFilter etc.) set and clear
     * their keys synchronously within each request, which is safe for the current deployment
     * profile (sequential / low-concurrency).  If request-concurrent isolation is needed,
     * migrate InMemoryRequestContextStore to use Reactor Context under the same interface.
     */
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
