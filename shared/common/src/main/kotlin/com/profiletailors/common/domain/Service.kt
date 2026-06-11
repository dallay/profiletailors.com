package com.profiletailors.common.domain

/**
 * Project marker for hexagonal "domain application service" classes (use-case handlers).
 *
 * The marker is a semantic / IDE / static-analysis tool — it preserves the team's
 * convention of distinguishing use-case handlers from generic Spring stereotypes.
 *
 * Spring discovers handlers annotated with this marker through an explicit
 * `includeFilters` entry in `SmpApplication` (FilterType.ANNOTATION), which replaces
 * Spring's default filter while still covering all standard stereotypes.
 *
 * @since 1.0.0
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class Service
