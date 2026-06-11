package com.profiletailors.smp.publishing.infrastructure.credentials

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Co-located `@Configuration` for the `publishing/credentials` bounded context.
 *
 * The class exists to register [PublishingCredentialsProperties] via the idiomatic
 * Spring Boot `@EnableConfigurationProperties` mechanism, replacing the previous
 * redundant `@Component` on the properties class. It follows the project house style
 * of dedicated `@Configuration` classes per bounded context (e.g.
 * `IdentitySecurityConfiguration`, `TenancyWebConfiguration`).
 *
 * The class intentionally declares no `@Bean` methods today; new beans for this
 * bounded context should be added here as the need arises.
 *
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(PublishingCredentialsProperties::class)
class PublishingCredentialsConfiguration
