package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.publishing.application.PublicationNotFoundException
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
import org.junit.jupiter.api.Assertions.assertNull
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
        assertEquals("The requested provider is not available.", problem.detail)
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
        assertEquals("OAuth state is invalid.", problem.detail)
    }

    @Test
    fun `maps PublicationNotFoundException to 404 NOT_FOUND`() {
        val exception = PublicationNotFoundException("pub-missing")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.status)
        assertEquals("Publication not found", problem.title)
        assertEquals("Publication not found.", problem.detail)
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
        assertEquals("The publication cannot transition from its current state.", problem.detail)
    }

    @Test
    fun `maps PublicationDeletionNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationDeletionNotAllowedException("pub-456")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertEquals("The publication cannot transition from its current state.", problem.detail)
    }

    @Test
    fun `maps PublicationCancellationNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationCancellationNotAllowedException("pub-789")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertEquals("The publication cannot transition from its current state.", problem.detail)
    }

    @Test
    fun `maps PublicationRetryNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationRetryNotAllowedException("pub-retry")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertEquals("The publication cannot transition from its current state.", problem.detail)
    }

    @Test
    fun `maps PublicationAlreadyTerminalException to 409 CONFLICT`() {
        val exception = PublicationAlreadyTerminalException("pub-terminal", PublicationStatus.PUBLISHED)
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertEquals("The publication cannot transition from its current state.", problem.detail)
    }

    @Test
    fun `maps base PublicationStateTransitionException to 409 CONFLICT`() {
        val exception = PublicationStateTransitionException("Generic state transition error for pub-base")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("Publication state conflict", problem.title)
        assertEquals("The publication cannot transition from its current state.", problem.detail)
    }

    @Test
    fun `maps asset not ready without leaking asset id`() {
        val exception = AssetNotReadyException("asset-123", "storage unavailable")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("Asset not ready", problem.title)
        assertEquals("One or more assets are not ready for publishing.", problem.detail)
        assertEquals("ASSET_NOT_READY", problem.properties?.get("errorCode"))
        assertNull(problem.properties?.get("assetId"))
    }
}
