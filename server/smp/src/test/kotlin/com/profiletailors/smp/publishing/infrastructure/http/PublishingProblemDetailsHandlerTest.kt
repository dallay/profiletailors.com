package com.profiletailors.smp.publishing.infrastructure.http

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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class PublishingProblemDetailsHandlerTest {
    private val handler = PublishingProblemDetailsHandler()

    @Test
    fun `maps ProviderNotConfiguredException to 503 SERVICE_UNAVAILABLE`() {
        val exception = ProviderNotConfiguredException(SocialProvider.LINKEDIN)
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
        problem.title shouldBe "Provider not configured"
        problem.detail shouldBe "The requested provider is not available."
    }

    @Test
    fun `maps ExpiredOAuthStateException to 400 BAD_REQUEST`() {
        val exception = ExpiredOAuthStateException()
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "OAuth state expired"
        problem.detail shouldBe "OAuth state has expired."
    }

    @Test
    fun `maps InvalidOAuthStateException to 400 BAD_REQUEST`() {
        val exception = InvalidOAuthStateException("Custom message")
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "OAuth state invalid"
        problem.detail shouldBe "OAuth state is invalid."
    }

    @Test
    fun `maps PublicationNotFoundException to 404 NOT_FOUND`() {
        val exception = PublicationNotFoundException("pub-missing")
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.NOT_FOUND.value()
        problem.title shouldBe "Publication not found"
        problem.detail shouldBe "Publication not found."
    }

    // -------------------------------------------------------------------------
    // Publication state transition exceptions → 409 CONFLICT
    // -------------------------------------------------------------------------

    @Test
    fun `maps PublicationEditNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationEditNotAllowedException("pub-123")
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Publication state conflict"
        problem.detail shouldBe "The publication cannot transition from its current state."
    }

    @Test
    fun `maps PublicationDeletionNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationDeletionNotAllowedException("pub-456")
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Publication state conflict"
        problem.detail shouldBe "The publication cannot transition from its current state."
    }

    @Test
    fun `maps PublicationCancellationNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationCancellationNotAllowedException("pub-789")
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Publication state conflict"
        problem.detail shouldBe "The publication cannot transition from its current state."
    }

    @Test
    fun `maps PublicationRetryNotAllowedException to 409 CONFLICT`() {
        val exception = PublicationRetryNotAllowedException("pub-retry")
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Publication state conflict"
        problem.detail shouldBe "The publication cannot transition from its current state."
    }

    @Test
    fun `maps PublicationAlreadyTerminalException to 409 CONFLICT`() {
        val exception = PublicationAlreadyTerminalException("pub-terminal", PublicationStatus.PUBLISHED)
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Publication state conflict"
        problem.detail shouldBe "The publication cannot transition from its current state."
    }

    @Test
    fun `maps base PublicationStateTransitionException to 409 CONFLICT`() {
        val exception = PublicationStateTransitionException("Generic state transition error for pub-base")
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Publication state conflict"
        problem.detail shouldBe "The publication cannot transition from its current state."
    }

    @Test
    fun `maps asset not ready without leaking asset id`() {
        val exception = com.profiletailors.smp.media.application.AssetNotReadyException(
            "asset-123",
            "storage unavailable",
        )
        val problem = handler.handle(exception)

        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "Asset not ready"
        problem.detail shouldBe "One or more assets are not ready for publishing."
        problem.properties?.get("errorCode") shouldBe "ASSET_NOT_READY"
        problem.properties?.get("assetId").shouldBeNull()
    }
}
