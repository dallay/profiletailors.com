package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.common.domain.context.MissingRequestPathException
import com.profiletailors.smp.platform.infrastructure.ReactorContextRequestPathProvider
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

class RequestPathWebFilterTest {

    @Test
    fun `isolates concurrent request paths in downstream provider`() {
        val filter = RequestPathWebFilter()
        val secondDownstreamEntered = Sinks.one<Unit>()
        val firstDownstreamObserved = Sinks.one<Unit>()
        val provider = ReactorContextRequestPathProvider()
        var firstResolvedPath: String? = null
        var secondResolvedPath: String? = null
        val firstRequest = filter.filter(
            MockServerWebExchange.from(MockServerHttpRequest.get("/api/first").build()),
            WebFilterChain {
                secondDownstreamEntered.asMono()
                    .then(
                        mono {
                            firstResolvedPath = provider.current()
                            firstDownstreamObserved.tryEmitValue(Unit)
                            Unit
                        },
                    )
                    .then()
            },
        )
        val secondRequest = filter.filter(
            MockServerWebExchange.from(MockServerHttpRequest.get("/api/second").build()),
            WebFilterChain {
                mono { provider.current() }
                    .doOnNext { secondResolvedPath = it }
                    .doOnSubscribe { secondDownstreamEntered.tryEmitValue(Unit) }
                    .then(firstDownstreamObserved.asMono().then())
            },
        )

        Mono.`when`(firstRequest, secondRequest).block()

        assertAll(
            { assertEquals("/api/first", firstResolvedPath) },
            { assertEquals("/api/second", secondResolvedPath) },
        )
    }

    @Test
    fun `request path is absent outside subscription context`() {
        val provider = ReactorContextRequestPathProvider()
        var path: String? = "unexpected"

        runTest { path = provider.current() }

        assertNull(path)
        assertThrows(MissingRequestPathException::class.java) {
            runTest { provider.require() }
        }
    }
}
