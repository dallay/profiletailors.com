package com.profiletailors.smp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * Entry point for the `profiletailors-backend` server module.
 *
 * The `@ComponentScan` deliberately does **not** declare an `includeFilters` block. Any explicit
 * `includeFilters` would suppress Spring's default filter set, silently dropping every class
 * annotated with `@Component`, `@Repository`, Spring-`@Service`, `@Controller`, or
 * `@RestController` (see [ClassPathBeanDefinitionScanner] Javadoc). The custom
 * `com.profiletailors.common.domain.Service` marker is meta-annotated with
 * `@org.springframework.stereotype.Component`, so it is discoverable through the default filter
 * alongside the rest of the project's stereotypes.
 *
 * The `excludeFilters` block is retained as a defence against accidentally moving test-only
 * classes (e.g. BDD glue, integration harnesses) into the main source set.
 */
@SpringBootApplication
@ComponentScan(
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
