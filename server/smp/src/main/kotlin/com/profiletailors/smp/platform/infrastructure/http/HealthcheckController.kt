package com.profiletailors.smp.platform.infrastructure.http

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "System",
    description = "System health and status endpoints",
)
@RestController
@RequestMapping("/api")
class HealthcheckController {

    @Operation(summary = "Health check endpoint")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "OK"),
        ApiResponse(responseCode = "500", description = "Internal server error"),
    )
    @GetMapping("/health-check", version = "1")
    @Suppress("FunctionOnlyReturningConstant")
    suspend fun healthcheck(): String = "OK"
}
