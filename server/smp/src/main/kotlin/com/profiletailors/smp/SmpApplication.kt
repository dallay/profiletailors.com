package com.profiletailors.smp

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.config.PlatformAdminBootstrapCommand
import com.profiletailors.storage.infrastructure.StorageAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
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
 */
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
                """com\.profiletailors\.smp\.platformadmin\.infrastructure\.cli\..*""",
            ],
        ),
    ],
)
class SmpApplication

fun main(args: Array<String>) {
    dispatchSmpCommand(
        args = args,
        bootstrap = PlatformAdminBootstrapCommand::run,
        server = { runApplication<SmpApplication>(*it) },
    )
}

internal fun dispatchSmpCommand(args: Array<String>, bootstrap: () -> Unit, server: (Array<String>) -> Unit) {
    if (args.contentEquals(arrayOf(PLATFORM_ADMIN_BOOTSTRAP_COMMAND))) {
        bootstrap()
    } else {
        server(args)
    }
}

private const val PLATFORM_ADMIN_BOOTSTRAP_COMMAND = "platform-admin-bootstrap"
