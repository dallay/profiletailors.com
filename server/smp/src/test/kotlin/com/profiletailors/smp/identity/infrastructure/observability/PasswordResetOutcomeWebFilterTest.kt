package com.profiletailors.smp.identity.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain

class PasswordResetOutcomeWebFilterTest {

    @Test
    fun `records completed and stable failed reset outcomes without request secrets`() {
        val meters = SimpleMeterRegistry()
        val adapter = PasswordRecoveryObservabilityAdapter(meters, ObservationRegistry.NOOP)
        val filter = PasswordResetOutcomeWebFilter(adapter)

        filter.filter(exchange("raw-token-sensitive", "NewPassword123!"), chain(HttpStatus.NO_CONTENT)).block()
        filter.filter(exchange("raw-token-sensitive", "NewPassword123!"), chain(HttpStatus.BAD_REQUEST)).block()
        filter.filter(
            exchange("raw-token-sensitive", "NewPassword123!"),
            chain(HttpStatus.INTERNAL_SERVER_ERROR),
        ).block()

        assertThat(
            meters.meters.map {
                it.id.tags.associate { tag -> tag.key to tag.value }
            },
        ).containsExactlyInAnyOrder(
            resetTags("completed", "none"),
            resetTags("failed", "invalid_request"),
            resetTags("failed", "internal"),
        )
        assertThat(meters.meters.toString())
            .doesNotContain("raw-token-sensitive")
            .doesNotContain("NewPassword123!")
    }

    @Test
    fun `ignores unrelated endpoints`() {
        val meters = SimpleMeterRegistry()
        val filter = PasswordResetOutcomeWebFilter(
            PasswordRecoveryObservabilityAdapter(meters, ObservationRegistry.NOOP),
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").build())

        filter.filter(exchange, chain(HttpStatus.NO_CONTENT)).block()

        assertThat(meters.meters).isEmpty()
    }

    private fun exchange(token: String, password: String): MockServerWebExchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/auth/reset-password?token=$token")
            .header("X-Test-Password", password)
            .build(),
    )

    private fun chain(status: HttpStatus) = WebFilterChain { exchange ->
        exchange.response.statusCode = status
        exchange.response.setComplete()
    }

    private fun resetTags(status: String, category: String) = mapOf(
        "operation" to "reset",
        "notification.type" to "none",
        "status" to status,
        "failure.category" to category,
        "attempt.bucket" to "none",
    )
}
