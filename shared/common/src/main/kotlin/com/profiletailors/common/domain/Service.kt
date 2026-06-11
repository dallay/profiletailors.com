package com.profiletailors.common.domain

import org.springframework.stereotype.Component

/**
 * Project marker for hexagonal "domain application service" classes (use-case handlers).
 *
 * The marker has two roles:
 *  1. **Semantic / IDE / static-analysis** — preserves the team's convention of distinguishing
 *     use-case handlers from generic Spring stereotypes.
 *  2. **Spring stereotype** — the `@Component` meta-annotation makes the custom marker
 *     discoverable by Spring's default `ClassPathBeanDefinitionScanner` filter, so it
 *     participates in component scanning without requiring an explicit `includeFilters`
 *     declaration on `@ComponentScan`.
 *
 * @since 1.0.0
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Component
annotation class Service
