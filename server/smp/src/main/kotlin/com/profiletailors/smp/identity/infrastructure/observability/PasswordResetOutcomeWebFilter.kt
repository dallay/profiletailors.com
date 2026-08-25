package com.profiletailors.smp.identity.infrastructure.observability

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class PasswordResetOutcomeWebFilter(private val observability: PasswordRecoveryObservability) : WebFilter {

    /**
     * Filters password reset requests and records their completion status or internal failure.
     *
     * @param exchange The current server exchange.
     * @param chain The filter chain to continue processing.
     * @return A reactive completion signal for the filter chain.
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (exchange.request.path.value() != RESET_PATH) {
            return chain.filter(exchange)
        }
        return chain.filter(exchange)
            .doOnSuccess { observability.recordResetStatus(exchange.response.statusCode) }
            .doOnError { observability.recordResetFailed(PasswordResetFailureCategory.INTERNAL) }
    }

    /**
     * Records the password reset outcome represented by the response status.
     *
     * @param status The HTTP response status used to determine the reset outcome.
     */
    private fun PasswordRecoveryObservability.recordResetStatus(status: HttpStatusCode?) {
        when {
            status?.value() == NO_CONTENT -> recordResetCompleted()
            status?.is4xxClientError == true -> recordResetFailed(PasswordResetFailureCategory.INVALID_REQUEST)
            else -> recordResetFailed(PasswordResetFailureCategory.INTERNAL)
        }
    }

    private companion object {
        const val RESET_PATH = "/api/auth/reset-password"
        const val NO_CONTENT = 204
    }
}
