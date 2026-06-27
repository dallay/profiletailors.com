package com.profiletailors.controllers

import com.profiletailors.common.domain.error.EntityNotFoundException
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GlobalExceptionHandlerTest {

    private class MissingUserException : EntityNotFoundException("User was not found")

    private val messageSource = StaticMessageSource().apply {
        addMessage(MSG_ENTITY_NOT_FOUND, Locale.ENGLISH, "Entity could not be found")
        addMessage(TITLE_INVALID_INPUT, Locale.ENGLISH, "Invalid Input")
        addMessage(MSG_BAD_REQUEST, Locale.ENGLISH, "Bad request localized")
        addMessage(MSG_INTERNAL_SERVER_ERROR, Locale.ENGLISH, "Something went wrong")
    }

    private val handler = GlobalExceptionHandler(messageSource)

    @Test
    fun `should handle entity not found with instance and trace metadata`() {
        val exchange = exchangeFor("/api/users/123")

        val problem = handler.handleEntityNotFound(MissingUserException(), exchange)

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.status)
        assertEquals(ENTITY_NOT_FOUND, problem.title)
        assertEquals("User was not found", problem.detail)
        assertEquals("https://profiletailors.com/errors/entity-not-found", problem.type.toString())
        assertEquals("/api/users/123", problem.instance.toString())
        assertEquals("NOT_FOUND", problem.properties!![ERROR_CATEGORY])
        assertEquals(MSG_ENTITY_NOT_FOUND, problem.properties!![MESSAGE_KEY])
        assertEquals("Entity could not be found", problem.properties!![LOCALIZED_MESSAGE])
        assertEquals(exchange.request.id, problem.properties!![TRACE_ID])
        assertNotNull(problem.properties!![TIMESTAMP])
    }

    @Test
    fun `should handle illegal argument with localized title and original message detail`() {
        val exchange = exchangeFor("/api/users")

        val problem = handler.handleIllegalArgumentException(
            IllegalArgumentException("email is invalid"),
            exchange,
        )

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("Invalid Input", problem.title)
        assertEquals("email is invalid", problem.detail)
        assertEquals("BAD_REQUEST", problem.properties!![ERROR_CATEGORY])
        assertEquals(MSG_BAD_REQUEST, problem.properties!![MESSAGE_KEY])
        assertEquals("Invalid Input", problem.properties!![LOCALIZED_MESSAGE])
    }

    @Test
    fun `should handle generic exception without exposing internal message`() {
        val exchange = exchangeFor("/api/internal")

        val problem = handler.handleGenericException(
            RuntimeException("database password leaked"),
            exchange,
        )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.status)
        assertEquals("Internal server error", problem.title)
        assertEquals("An internal server error occurred", problem.detail)
        assertEquals("INTERNAL_SERVER_ERROR", problem.properties!![ERROR_CATEGORY])
        assertEquals(MSG_INTERNAL_SERVER_ERROR, problem.properties!![MESSAGE_KEY])
        assertEquals("Something went wrong", problem.properties!![LOCALIZED_MESSAGE])
    }

    private fun exchangeFor(path: String): MockServerWebExchange {
        val request = MockServerHttpRequest.get(path)
            .header(HttpHeaders.ACCEPT_LANGUAGE, Locale.ENGLISH.toLanguageTag())
            .build()
        return MockServerWebExchange.from(request)
    }
}
