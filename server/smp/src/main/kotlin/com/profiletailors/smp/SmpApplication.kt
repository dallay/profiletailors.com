package com.profiletailors.smp

import com.profiletailors.common.domain.Service
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

@SpringBootApplication
@ComponentScan(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = [
                Service::class,
                org.springframework.web.bind.annotation.RestControllerAdvice::class,
            ],
        ),
    ],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.profiletailors\\.smp\\.integration\\..*"],
        ),
    ],
)
class SmpApplication

fun main(args: Array<String>) {
	runApplication<SmpApplication>(*args)
}
