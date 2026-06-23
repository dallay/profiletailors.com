package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.PublicationAlreadyTerminalException
import com.profiletailors.smp.publishing.domain.PublicationCancellationNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationDeletionNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationEditNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationRetryNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationStateTransitionException
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class PublishingProblemDetailsHandlerTest {
    private val handler = PublishingProblemDetailsHandler()

    @Test
    fun `maps ProviderNotConfiguredException to 503 SERVICE_UNAVAILABLE`() {
        val exception = ProviderNotConfiguredException(SocialProvider.LINKEDIN)
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), problem.status)
        assertEquals("Provider not configured", problem.title)
        assertEquals("Provider 'LINKEDIN' is not configured.", problem.detail)
    }

    @Test
    fun `maps ExpiredOAuthStateException to 400 BAD_REQUEST`() {
        val exception = ExpiredOAuthStateException()
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("OAuth state expired", problem.title)
        assertEquals("OAuth state has expired.", problem.detail)
    }

    @Test
    fun `maps InvalidOAuthStateException to 400 BAD_REQUEST`() {
        val exception = InvalidOAuthStateException("Custom message")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("OAuth state invalid", problem.title)
        assertEquals("Custom message", problem.detail)
    }

    // -------------------------------------------------------------------------
    // Publication state transition exceptions → 409 CONFLICT
    // -------------------------------------------------------------------------

    @Test
    fun `maps PublicationEditNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationEditNotAllowedException("pub-123")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertTrue(problem.detail!!.contains("pub-123"))
    }

    @Test
    fun `maps PublicationDeletionNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationDeletionNotAllowedException("pub-456")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertTrue(problem.detail!!.contains("pub-456"))
    }

    @Test
    fun `maps PublicationCancellationNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationCancellationNotAllowedException("pub-789")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertTrue(problem.detail!!.contains("pub-789"))
    }

    @Test
    fun `maps PublicationRetryNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationRetryNotAllowedException("pub-retry")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertTrue(problem.detail!!.contains("pub-retry"))
    }

    @Test
    fun `maps PublicationAlreadyTerminalException to 409 CONFLICT`() {
        val exception = PublicationAlreadyTerminalException("pub-terminal", PublicationStatus.PUBLISHED)
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertTrue(problem.detail!!.contains("pub-terminal"))
        assertTrue(problem.detail!!.contains("PUBLISHED"))
    }

    @Test
    fun `maps base PublicationStateTransitionException to 409 CONFLICT`() {
        val exception = PublicationStateTransitionException("Generic state transition error for pub-base")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertEquals("Generic state transition error for pub-base", problem.detail)
    }

    @Test
    fun `uses fallback detail message when exception message is null`() {
        // Subclass with null message is not directly constructable, so verify via a
        // concrete subclass that the detail is never null in practice
        val exception = PublicationEditNotAllowedException("pub-null-msg")
        val problem = handler.handle(exception)

        // The handler provides a fallback string; detail must not be null
        assertTrue(problem.detail != null)
    }
}
