package com.profiletailors.smp

import com.profiletailors.common.domain.Service
import com.profiletailors.storage.infrastructure.StorageAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.stereotype.Service as SpringService

/**
 * Entry point for the `profiletailors-backend` server module.
 *
 * The `@ComponentScan` declares an explicit `includeFilters` block because any
 * `includeFilters` disables Spring's default filter set. To compensate, all standard
 * Spring stereotypes are listed explicitly alongside the custom `@Service` marker,
 * ensuring the full set of stereotypes is discovered.
 *
 * The `excludeFilters` block is retained as a defence against accidentally moving test-only
 * classes (e.g. BDD glue, integration harnesses) into the main source set.
 *
 * `@Modulithic` makes the modular-monolith contract explicit at the application root so
 * Spring Modulith tooling, verification, and generated documentation are anchored here.
 */
@Modulithic
@SpringBootApplication
@EnableScheduling
@Import(StorageAutoConfiguration::class)
@ComponentScan(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = [
                Component::class,
                Repository::class,
                SpringService::class,
                org.springframework.stereotype.Controller::class,
                RestController::class,
                RestControllerAdvice::class,
                Service::class,
            ],
        ),
    ],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = [
                "com\\.profiletailors\\.smp\\.integration\\..*",
                "com\\.profiletailors\\.smp\\.bdd\\..*",
            ],
        ),
    ],
)
class SmpApplication

fun main(args: Array<String>) {
    runApplication<SmpApplication>(*args)
}
