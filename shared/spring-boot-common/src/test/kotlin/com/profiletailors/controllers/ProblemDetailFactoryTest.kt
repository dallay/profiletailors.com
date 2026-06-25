package com.profiletailors.controllers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class ProblemDetailFactoryTest {

    @Test
    fun `should create problem detail with required fields`() {
        val problem = createProblemDetail(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = "Invalid input",
            typeSuffix = "bad-request",
            errorCategory = "VALIDATION",
        )

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("Bad Request", problem.title)
        assertEquals("Invalid input", problem.detail)
        assertEquals("https://profiletailors.com/errors/bad-request", problem.type.toString())
        assertEquals("VALIDATION", problem.properties!![ERROR_CATEGORY])
        assertNotNull(problem.properties!![TIMESTAMP])
        assertNull(problem.properties!![TRACE_ID])
    }

    @Test
    fun `should use title as detail when detail is null`() {
        val problem = createProblemDetail(
            status = HttpStatus.NOT_FOUND,
            title = ENTITY_NOT_FOUND,
            detail = null,
            typeSuffix = "entity-not-found",
            errorCategory = "NOT_FOUND",
        )

        assertEquals(ENTITY_NOT_FOUND, problem.detail)
    }

    @Test
    fun `should include exchange trace and instance when requested`() {
        val request = MockServerHttpRequest.get("/api/test/123").build()
        val exchange = MockServerWebExchange.from(request)

        val problem = createProblemDetail(
            status = HttpStatus.NOT_FOUND,
            title = ENTITY_NOT_FOUND,
            detail = "missing",
            typeSuffix = "entity-not-found",
            errorCategory = "NOT_FOUND",
            options = ProblemDetailOptions(exchange = exchange, includeInstance = true),
        )

        assertEquals(exchange.request.id, problem.properties!![TRACE_ID])
        assertEquals("/api/test/123", problem.instance.toString())
    }

    @Test
    fun `should include optional localized message and additional properties`() {
        val problem = createProblemDetail(
            status = HttpStatus.BAD_REQUEST,
            title = "Validation failed",
            detail = "Invalid request",
            typeSuffix = "validation/constraint-violation",
            errorCategory = "VALIDATION",
            options = ProblemDetailOptions(
                messageKey = "error.validation.failed",
                localizedMessage = "La validación falló",
                additionalProperties = mapOf("field" to "email"),
            ),
        )

        assertEquals("error.validation.failed", problem.properties!![MESSAGE_KEY])
        assertEquals("La validación falló", problem.properties!![LOCALIZED_MESSAGE])
        assertEquals("email", problem.properties!!["field"])
    }
}
