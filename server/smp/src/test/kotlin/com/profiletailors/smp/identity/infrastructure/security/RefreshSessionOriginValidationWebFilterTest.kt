package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class RefreshSessionOriginValidationWebFilterTest {

    private val filter = RefreshSessionOriginValidationWebFilter(
        corsProperties = CorsConfigurationProperties(allowedOrigins = listOf("http://localhost")),
        refreshSessionProperties = RefreshSessionProperties(
            cookieName = "pt_refresh",
            cookiePath = "/api/auth",
            sameSite = "Lax",
            secure = true,
            ttlSeconds = 604_800,
        ),
    )

    @Test
    fun `allows refresh request with cookie and trusted origin`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, "http://localhost")
                .header(HttpHeaders.COOKIE, "pt_refresh=lookup.secret")
                .build(),
        )
        var chainCalled = false

        filter.filter(
            exchange,
            WebFilterChain {
                chainCalled = true
                Mono.empty()
            },
        ).block()

        chainCalled shouldBe true
    }

    @Test
    fun `allows refresh request from localhost origin pattern with arbitrary port`() {
        val filterWithLocalhostPattern = RefreshSessionOriginValidationWebFilter(
            corsProperties = CorsConfigurationProperties(
                allowedOrigins = listOf("https://*.localhost:[*]"),
            ),
            refreshSessionProperties = RefreshSessionProperties(
                cookieName = "pt_refresh",
                cookiePath = "/api/auth",
                sameSite = "Lax",
                secure = true,
                ttlSeconds = 604_800,
            ),
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, "https://workspace.pt-app.localhost:1355")
                .header(HttpHeaders.COOKIE, "pt_refresh=lookup.secret")
                .build(),
        )
        var chainCalled = false

        filterWithLocalhostPattern.filter(
            exchange,
            WebFilterChain {
                chainCalled = true
                Mono.empty()
            },
        ).block()

        chainCalled shouldBe true
    }

    @Test
    fun `cors allows localhost origin pattern with credentials`() {
        val corsConfigurationSource = IdentitySecurityConfiguration().corsConfigurationSource(
            CorsConfigurationProperties(
                allowedOrigins = listOf(
                    "https://*.localhost:[*]",
                    "http://*.localhost:[*]",
                    "http://localhost:[*]",
                ),
                allowCredentials = true,
            ),
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())

        listOf(
            "https://pt-app.localhost",
            "https://workspace.pt-app.localhost:1355",
            "http://workspace.pt-app.localhost:5174",
            "http://localhost:5173",
        ).forEach { origin ->
            corsConfigurationSource.getCorsConfiguration(exchange)?.checkOrigin(origin) shouldBe origin
        }
        corsConfigurationSource.getCorsConfiguration(exchange)?.allowCredentials shouldBe true
    }

    @Test
    fun `rejects logout request with cookie and no origin metadata`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/logout")
                .header(HttpHeaders.COOKIE, "pt_refresh=lookup.secret")
                .build(),
        )

        filter.filter(exchange, WebFilterChain { Mono.empty() }).block()

        exchange.response.statusCode shouldBe HttpStatus.FORBIDDEN
    }

    @Test
    fun `allows request without refresh cookie to proceed`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/logout")
                .build(),
        )
        var chainCalled = false

        filter.filter(
            exchange,
            WebFilterChain {
                chainCalled = true
                Mono.empty()
            },
        ).block()

        chainCalled shouldBe true
    }

    @Test
    fun `allows same-origin request based on forwarded host and proto`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("http://internal/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, "https://app.example.com")
                .header(HttpHeaders.COOKIE, "pt_refresh=lookup.secret")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "app.example.com")
                .build(),
        )
        var chainCalled = false

        filter.filter(
            exchange,
            WebFilterChain {
                chainCalled = true
                Mono.empty()
            },
        ).block()

        chainCalled shouldBe true
    }

    @Test
    fun `allows refresh request when referer URL is a trusted origin`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/refresh")
                .header(HttpHeaders.COOKIE, "pt_refresh=lookup.secret")
                .header(HttpHeaders.REFERER, "http://localhost/some/deep/path")
                .build(),
        )
        var chainCalled = false

        filter.filter(
            exchange,
            WebFilterChain {
                chainCalled = true
                Mono.empty()
            },
        ).block()

        chainCalled shouldBe true
    }

    @Test
    fun `rejects logout request when referer URL is not a trusted origin`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/logout")
                .header(HttpHeaders.COOKIE, "pt_refresh=lookup.secret")
                .header(HttpHeaders.REFERER, "https://evil.example/landing")
                .build(),
        )

        filter.filter(exchange, WebFilterChain { Mono.empty() }).block()

        exchange.response.statusCode shouldBe HttpStatus.FORBIDDEN
    }
}
