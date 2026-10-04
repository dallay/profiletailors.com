package com.profiletailors.smp.shortlinks.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.shortlinks.application.LinkDeletedApplicationException
import com.profiletailors.smp.shortlinks.application.LinkDisabledApplicationException
import com.profiletailors.smp.shortlinks.application.LinkExpiredApplicationException
import com.profiletailors.smp.shortlinks.application.LinkNotFoundApplicationException
import com.profiletailors.smp.shortlinks.application.LinkQuarantinedApplicationException
import com.profiletailors.smp.shortlinks.application.ResolveLinkQuery
import com.profiletailors.smp.shortlinks.application.ResolveResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import java.net.InetSocketAddress
import java.util.UUID

internal class RedirectControllerTest {
    private val mediator = mockk<Mediator>()
    private val controller = RedirectController(mediator)
    private val linkId = UUID.fromString("0199b1ca-0000-7000-8000-000000000001")

    @Test
    fun `redirects active link with no-store`() = runBlocking {
        val request = request("go.profiletailors.com")
        coEvery { mediator.send(any<ResolveLinkQuery>()) } returns
            ResolveResult("https://example.com/path", "ACTIVE", linkId)

        val response = controller.redirect("AbC123", request)

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertEquals("https://example.com/path", response.headers.getFirst(HttpHeaders.LOCATION))
        assertEquals("no-store", response.headers.getFirst(HttpHeaders.CACHE_CONTROL))
    }

    @Test
    fun `resolves with request host as domain`() = runBlocking {
        val request = request("go.profiletailors.com")
        coEvery { mediator.send(any<ResolveLinkQuery>()) } returns
            ResolveResult("https://example.com/path", "ACTIVE", linkId)

        controller.redirect("AbC123", request)

        coVerify(exactly = 1) {
            mediator.send(
                match<ResolveLinkQuery> { it.domain == "go.profiletailors.com" && it.shortCode == "AbC123" },
            )
        }
    }

    @Test
    fun `unknown code returns not found`() = runBlocking {
        coEvery { mediator.send(any<ResolveLinkQuery>()) } throws LinkNotFoundApplicationException("Nope")

        assertEquals(HttpStatus.NOT_FOUND, controller.redirect("Nope", request("go.profiletailors.com")).statusCode)
    }

    @Test
    fun `expired link returns gone`() = runBlocking {
        coEvery { mediator.send(any<ResolveLinkQuery>()) } throws LinkExpiredApplicationException("Old")

        assertEquals(HttpStatus.GONE, controller.redirect("Old", request("go.profiletailors.com")).statusCode)
    }

    @Test
    fun `disabled link returns not found`() = runBlocking {
        coEvery { mediator.send(any<ResolveLinkQuery>()) } throws LinkDisabledApplicationException("Off")

        assertEquals(HttpStatus.NOT_FOUND, controller.redirect("Off", request("go.profiletailors.com")).statusCode)
    }

    @Test
    fun `deleted link returns not found`() = runBlocking {
        coEvery { mediator.send(any<ResolveLinkQuery>()) } throws LinkDeletedApplicationException("Gone")

        assertEquals(HttpStatus.NOT_FOUND, controller.redirect("Gone", request("go.profiletailors.com")).statusCode)
    }

    @Test
    fun `quarantined link returns forbidden`() = runBlocking {
        coEvery { mediator.send(any<ResolveLinkQuery>()) } throws LinkQuarantinedApplicationException("Bad")

        assertEquals(HttpStatus.FORBIDDEN, controller.redirect("Bad", request("go.profiletailors.com")).statusCode)
    }

    private fun request(host: String): ServerHttpRequest {
        val headers = HttpHeaders()
        headers.host = InetSocketAddress(host, 443)
        val request = mockk<ServerHttpRequest>()
        every { request.headers } returns headers
        return request
    }
}
