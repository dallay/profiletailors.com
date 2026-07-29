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
class PasswordResetOutcomeWebFilter(private val observability: PasswordRecoveryObservabilityAdapter) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (exchange.request.path.value() != RESET_PATH) {
            return chain.filter(exchange)
        }
        return chain.filter(exchange)
            .doOnSuccess { observability.recordResetStatus(exchange.response.statusCode) }
            .doOnError { observability.recordResetFailed(PasswordResetFailureCategory.INTERNAL) }
    }

    private fun PasswordRecoveryObservabilityAdapter.recordResetStatus(status: HttpStatusCode?) {
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
