package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.identity.application.GetCurrentTermsVersionQuery
import com.profiletailors.smp.platformadmin.application.CompleteBootstrapCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class BootstrapRequirementsResponse(val currentTermsVersion: String)

data class CompleteBootstrapRequest(
    @field:NotBlank val invitationToken: String,
    @field:NotBlank val password: String,
    @field:NotBlank val acceptedTermsVersion: String,
)

@RestController
@RequestMapping("/api/bootstrap")
class PlatformBootstrapController(private val mediator: Mediator) {
    @GetMapping("/requirements")
    @Operation(summary = "Read public bootstrap completion requirements")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Current Terms version"),
        ],
    )
    suspend fun requirements(): BootstrapRequirementsResponse =
        BootstrapRequirementsResponse(mediator.send(GetCurrentTermsVersionQuery()).version)

    @PostMapping("/complete")
    @Operation(summary = "Claim a platform bootstrap invitation")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "202", description = "Account created; verification email sent"),
            ApiResponse(responseCode = "400", description = "Bootstrap invitation unavailable"),
        ],
    )
    suspend fun complete(@Valid @RequestBody request: CompleteBootstrapRequest): ResponseEntity<Unit> {
        mediator.send(
            CompleteBootstrapCommand(
                invitationToken = request.invitationToken,
                password = request.password,
                acceptedTermsVersion = request.acceptedTermsVersion,
            ),
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}
