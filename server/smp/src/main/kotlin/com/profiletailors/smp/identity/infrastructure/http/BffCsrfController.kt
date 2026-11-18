package com.profiletailors.smp.identity.infrastructure.http

import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/auth")
class BffCsrfController {
    @GetMapping("/csrf")
    fun csrf(exchange: ServerWebExchange): Mono<CsrfToken> = exchange.getAttribute(CsrfToken::class.java.name)
        ?: Mono.error(IllegalStateException("CSRF token is not available"))
}
