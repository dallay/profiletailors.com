package com.profiletailors.smp.platform.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.RequestPathProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.platform.infrastructure.http.RequestPathWebFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.WebFilter
import java.time.Clock

@Configuration
class PlatformBootstrapConfiguration {

    @Bean
    fun reactivePrincipalProvider(): PrincipalContextProvider = ReactorContextPrincipalContextProvider()

    @Bean
    fun resourceContextProvider(): ResourceContextProvider = ReactorContextResourceContextProvider()

    @Bean
    fun requestPathProvider(): RequestPathProvider = ReactorContextRequestPathProvider()

    @Bean
    fun requestPathWebFilter(): WebFilter = RequestPathWebFilter()

    /**
     * Jackson 2 ObjectMapper with Kotlin module support.
     *
     * Required by components that serialize/deserialize JSON using Jackson 2
     * (e.g. HmacOAuthStateSigner, R2dbcDirectGrantResolver, R2dbcAuditHook).
     *
     * This is separate from the Jackson 3 JsonMapper configured in JacksonConfig
     * which handles HTTP request/response serialization via WebFlux codecs.
     *
     * The Kotlin module is registered to properly handle Kotlin data classes,
     * nullable types, and default parameter values during serialization.
     */
    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
