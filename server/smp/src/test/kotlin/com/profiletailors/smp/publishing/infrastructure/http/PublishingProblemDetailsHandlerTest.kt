package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.publishing.application.PublicationNotFoundException
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.PublicationDeletionNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `maps PublicationNotFoundException to 404 NOT_FOUND`() {
        val exception = PublicationNotFoundException("pub-404")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.status)
        assertEquals("Publication not found", problem.title)
        assertEquals("Publication 'pub-404' was not found in the active workspace.", problem.detail)
    }

    @Test
    fun `maps PublicationDeletionNotAllowedException to 409 CONFLICT with machine-readable properties`() {
        val exception = PublicationDeletionNotAllowedException("pub-409", PublicationStatus.PUBLISHED)
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication deletion not allowed", problem.title)
        assertEquals("DELETION_NOT_ALLOWED", problem.properties?.get("errorCode"))
        assertEquals("pub-409", problem.properties?.get("publicationId"))
        assertEquals("PUBLISHED", problem.properties?.get("currentStatus"))
    }
}
