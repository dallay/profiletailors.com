package com.profiletailors.smp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service as SpringService
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import com.profiletailors.common.domain.Service

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
 */
@SpringBootApplication
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
