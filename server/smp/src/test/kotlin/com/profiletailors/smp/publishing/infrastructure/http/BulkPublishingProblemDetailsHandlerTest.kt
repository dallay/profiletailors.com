@file:Suppress("MaxLineLength")

package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.publishing.application.BulkJobNotFoundException
import com.profiletailors.smp.publishing.application.BulkWorkspaceMismatchException
import com.profiletailors.smp.publishing.application.DuplicateBulkImportException
import com.profiletailors.smp.publishing.application.PublicationNotFoundException
import com.profiletailors.smp.publishing.application.RecurringScheduleNotFoundException
import com.profiletailors.smp.publishing.application.SocialContentActorNotFoundException
import com.profiletailors.smp.publishing.application.SocialContentPostIsolationException
import com.profiletailors.smp.publishing.application.SocialContentPostNotFoundException
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidSocialContentCursorException
import com.profiletailors.smp.publishing.domain.ProviderConnectionNotAvailableException
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.PublicationAlreadyTerminalException
import com.profiletailors.smp.publishing.domain.PublicationCancellationNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationDeletionNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationEditNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationRetryNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationStateTransitionException
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.SocialContentAccessDenial
import com.profiletailors.smp.publishing.domain.SocialContentAccessDeniedException
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class BulkPublishingProblemDetailsHandlerTest {
    private val handler = PublishingProblemDetailsHandler()

    @Test
    fun `maps DuplicateBulkImportException to 409 with jobId`() {
        val ex = DuplicateBulkImportException("job-dup-1")
        val problem = handler.handle(ex)
        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Bulk import duplicate"
        problem.detail shouldBe "Duplicate bulk import job: job-dup-1"
        problem.properties?.get("jobId") shouldBe "job-dup-1"
    }

    @Test
    fun `maps BulkJobNotFoundException to 404`() {
        val ex = BulkJobNotFoundException("job-missing")
        val problem = handler.handle(ex)
        problem.status shouldBe HttpStatus.NOT_FOUND.value()
        problem.title shouldBe "Bulk job not found"
        problem.detail shouldBe "Bulk job not found: job-missing"
    }

    @Test
    fun `maps BulkWorkspaceMismatchException to 403 with code`() {
        val ex = BulkWorkspaceMismatchException("Workspace path does not match")
        val problem = handler.handle(ex)
        problem.status shouldBe HttpStatus.FORBIDDEN.value()
        problem.title shouldBe "Workspace access denied"
        problem.detail shouldBe "Workspace path does not match"
        problem.properties?.get("code") shouldBe "WORKSPACE_MISMATCH"
    }

    @Test
    fun `maps BulkWorkspaceMismatch default message`() {
        val ex = BulkWorkspaceMismatchException()
        val problem = handler.handle(ex)
        problem.status shouldBe HttpStatus.FORBIDDEN.value()
        problem.title shouldBe "Workspace access denied"
    }

    @Test
    fun `maps ProviderConnectionNotAvailableException to 409 with reason`() {
        val ex =
            ProviderConnectionNotAvailableException(
                SocialProvider.LINKEDIN,
                com.profiletailors.smp.publishing.domain.ProviderCatalogState.LOCKED,
                reason = null,
            )
        val problem = handler.handle(ex)
        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Provider connection unavailable"
    }

    @Test
    fun `maps PublicationValidationException to 400`() {
        val ex = PublicationValidationException("bad body")
        val problem = handler.handle(ex)
        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "Bad Request"
        problem.detail shouldBe "bad body"
    }

    @Test
    fun `maps IllegalArgumentException without message to fallback`() {
        val ex = IllegalArgumentException()
        val problem = handler.handle(ex)
        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "Bad Request"
        problem.detail shouldBe "Invalid request argument"
    }

    @Test
    fun `maps RecurringScheduleNotFoundException to 404`() {
        val ex = RecurringScheduleNotFoundException("sched-1")
        val problem = handler.handle(ex)
        problem.status shouldBe HttpStatus.NOT_FOUND.value()
        problem.title shouldBe "Recurring schedule not found"
    }

    @Test
    fun `maps InvalidSocialContentCursorException to 400 with errorCode`() {
        val problem = handler.handle(InvalidSocialContentCursorException("bad"))
        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "Invalid social content cursor"
        problem.properties?.get("errorCode") shouldBe "INVALID_SOCIAL_CONTENT_CURSOR"
    }

    @Test
    fun `maps MediaServiceUnavailableException to 503`() {
        val problem = handler.handle(MediaServiceUnavailableException("timeout"))
        problem.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
        problem.title shouldBe "Media service unavailable"
        problem.properties?.get("errorCode") shouldBe "MEDIA_SERVICE_UNAVAILABLE"
    }

    @Test
    fun `maps AssetNotReadyException to 400`() {
        val problem = handler.handle(AssetNotReadyException("asset-1", "not ready"))
        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "Asset not ready"
        problem.properties?.get("errorCode") shouldBe "ASSET_NOT_READY"
    }

    @Test
    fun `maps ExpiredOAuthState to 400`() {
        val problem = handler.handle(ExpiredOAuthStateException())
        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "OAuth state expired"
    }

    @Test
    fun `maps InvalidOAuthState to 400`() {
        val problem = handler.handle(InvalidOAuthStateException("bad"))
        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "OAuth state invalid"
    }

    @Test
    fun `maps ProviderNotConfigured to 503`() {
        val problem = handler.handle(ProviderNotConfiguredException(SocialProvider.LINKEDIN))
        problem.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
        problem.title shouldBe "Provider not configured"
    }

    @Test
    fun `maps PublicationStateTransition group to 409`() {
        listOf(
            PublicationEditNotAllowedException("p1"),
            PublicationDeletionNotAllowedException("p2"),
            PublicationCancellationNotAllowedException("p3"),
            PublicationRetryNotAllowedException("p4"),
            PublicationAlreadyTerminalException("p5", PublicationStatus.PUBLISHED),
            PublicationStateTransitionException("generic"),
        ).forEach { ex ->
            val problem = handler.handle(ex)
            problem.status shouldBe HttpStatus.CONFLICT.value()
            problem.title shouldBe "Publication state conflict"
        }
    }

    @Test
    fun `maps SocialContentAccessDenied to 403`() {
        val problem = handler.handle(SocialContentAccessDeniedException(SocialContentAccessDenial.OPERATION_DISABLED))
        problem.status shouldBe HttpStatus.FORBIDDEN.value()
        problem.title shouldBe "Social content access denied"
        problem.properties?.get("code") shouldBe "OPERATION_DISABLED"
    }

    @Test
    fun `maps SocialContentPostIsolation to 409`() {
        val problem = handler.handle(SocialContentPostIsolationException())
        problem.status shouldBe HttpStatus.CONFLICT.value()
        problem.title shouldBe "Social content workspace conflict"
    }

    @Test
    fun `maps PublicationNotFound to 404`() {
        val problem = handler.handle(PublicationNotFoundException("pub-1"))
        problem.status shouldBe HttpStatus.NOT_FOUND.value()
        problem.title shouldBe "Publication not found"
    }

    @Test
    fun `maps SocialContentPostNotFound to 404`() {
        val problem = handler.handle(SocialContentPostNotFoundException("post-1"))
        problem.status shouldBe HttpStatus.NOT_FOUND.value()
        problem.title shouldBe "Social content post not found"
    }

    @Test
    fun `maps SocialContentActorNotFound to 404`() {
        val problem = handler.handle(SocialContentActorNotFoundException("actor-1"))
        problem.status shouldBe HttpStatus.NOT_FOUND.value()
        problem.title shouldBe "Social content actor not found"
    }
}
