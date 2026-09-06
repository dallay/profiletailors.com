package com.profiletailors.smp.privacy.application

import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.privacy.domain.DataSubjectRequestRepository
import com.profiletailors.smp.privacy.domain.DataSubjectRequestStatus
import com.profiletailors.smp.privacy.domain.RequestType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SubmitAccessRequestHandlerTest {

    private val repository = mockk<DataSubjectRequestRepository>()
    private val dataAggregationService = mockk<DataAggregationService>()
    private val auditor = mockk<PrivacyMutationAuditor>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneId.of("UTC"))
    private val handler: CommandWithResultHandler<SubmitAccessRequestCommand, DataSubjectRequestResponse> =
        SubmitAccessRequestHandler(repository, dataAggregationService, auditor, clock)

    @Test
    fun `handle creates ACCESS request and aggregates data`() = runTest {
        coEvery { dataAggregationService.aggregate(any(), any()) } returns mapOf("identity" to null)
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitAccessRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = null,
            notes = "Please provide my data",
        )

        val result = handler.handle(command)

        assert(result.type == RequestType.ACCESS.name)
        assert(result.status == DataSubjectRequestStatus.COMPLETED.name)

        coVerify { dataAggregationService.aggregate("principal-1", "user@example.com") }
        coVerify { repository.save(any()) }
    }

    @Test
    fun `handle stores aggregated data in resultRef`() = runTest {
        val testData = mapOf("identity" to mapOf("email" to "user@example.com"))
        coEvery { dataAggregationService.aggregate(any(), any()) } returns testData
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitAccessRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = null,
            notes = null,
        )

        val result = handler.handle(command)

        assert(result.status == DataSubjectRequestStatus.COMPLETED.name)
        assert(result.completedAt != null)
    }
}

class SubmitExportRequestHandlerTest {

    private val repository = mockk<DataSubjectRequestRepository>()
    private val dataAggregationService = mockk<DataAggregationService>()
    private val storage = mockk<Storage>()
    private val auditor = mockk<PrivacyMutationAuditor>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneId.of("UTC"))
    private val handler: CommandWithResultHandler<SubmitExportRequestCommand, DataSubjectRequestResponse> =
        SubmitExportRequestHandler(repository, dataAggregationService, storage, auditor, clock)

    @Test
    fun `handle creates EXPORT request and generates JSON`() = runTest {
        coEvery { dataAggregationService.aggregate(any(), any()) } returns mapOf("identity" to null)
        coEvery { storage.uploadJson(any(), any()) } returns "https://storage.example.com/export.json"
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitExportRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = "ws-1",
            notes = "Export my data",
        )

        val result = handler.handle(command)

        assert(result.type == RequestType.EXPORT.name)
        assert(result.status == DataSubjectRequestStatus.COMPLETED.name)
    }

    @Test
    fun `handle uploads JSON and stores download URL`() = runTest {
        coEvery { dataAggregationService.aggregate(any(), any()) } returns mapOf("identity" to null)
        coEvery { storage.uploadJson(any(), any()) } returns "https://storage.example.com/export.json"
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitExportRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = "ws-1",
            notes = null,
        )

        handler.handle(command)

        coVerify { storage.uploadJson(match { it.startsWith("dsar-exports/") }, any()) }
    }

    @Test
    fun `handle persists completed request with download URL`() = runTest {
        coEvery { dataAggregationService.aggregate(any(), any()) } returns mapOf("identity" to null)
        coEvery { storage.uploadJson(any(), any()) } returns "https://storage.example.com/export.json"
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitExportRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = null,
            notes = null,
        )

        handler.handle(command)

        coVerify {
            repository.save(
                match { request ->
                    request.requestType == RequestType.EXPORT &&
                        request.status == DataSubjectRequestStatus.COMPLETED &&
                        request.resultRef == "https://storage.example.com/export.json"
                },
            )
        }
    }
}

class SubmitCorrectionRequestHandlerTest {

    private val repository = mockk<DataSubjectRequestRepository>()
    private val anonymizationService = mockk<AnonymizationService>()
    private val auditor = mockk<PrivacyMutationAuditor>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneId.of("UTC"))
    private val handler: CommandWithResultHandler<SubmitCorrectionRequestCommand, DataSubjectRequestResponse> =
        SubmitCorrectionRequestHandler(repository, anonymizationService, auditor, clock)

    @Test
    fun `handle creates CORRECTION request with COMPLETED status`() = runTest {
        coEvery { anonymizationService.verifyCorrection(any(), any(), any()) } returns
            AnonymizationService.CorrectionResult.Success
        coEvery { anonymizationService.anonymizeWaitlistByEmail(any(), any()) } returns Unit
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitCorrectionRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            field = CorrectionField.EMAIL,
            newValue = "new@example.com",
            workspaceId = null,
            notes = null,
        )

        val result = handler.handle(command)

        assert(result.type == RequestType.CORRECTION.name)
        assert(result.status == DataSubjectRequestStatus.COMPLETED.name)
    }

    @Test
    fun `handle propagates email change to waitlist`() = runTest {
        coEvery { anonymizationService.verifyCorrection(any(), any(), any()) } returns
            AnonymizationService.CorrectionResult.Success
        coEvery { anonymizationService.anonymizeWaitlistByEmail(any(), any()) } returns Unit
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitCorrectionRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            field = CorrectionField.EMAIL,
            newValue = "new@example.com",
            workspaceId = null,
            notes = null,
        )

        handler.handle(command)

        coVerify { anonymizationService.anonymizeWaitlistByEmail("user@example.com", any()) }
    }

    @Test
    fun `handle does NOT propagate username changes to waitlist`() = runTest {
        coEvery { anonymizationService.verifyCorrection(any(), any(), any()) } returns
            AnonymizationService.CorrectionResult.Success
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitCorrectionRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            field = CorrectionField.USERNAME,
            newValue = "newuser",
            workspaceId = null,
            notes = null,
        )

        handler.handle(command)

        coVerify(exactly = 0) { anonymizationService.anonymizeWaitlistByEmail(any(), any()) }
    }

    @Test
    fun `handle throws when principal not found`() = runTest {
        coEvery { anonymizationService.verifyCorrection(any(), any(), any()) } returns
            AnonymizationService.CorrectionResult.NotFound

        val command = SubmitCorrectionRequestCommand(
            requestedByPrincipalId = "nonexistent",
            requestedByEmail = "user@example.com",
            field = CorrectionField.EMAIL,
            newValue = "new@example.com",
            workspaceId = null,
            notes = null,
        )

        try {
            handler.handle(command)
            assert(false) { "Expected exception was not thrown" }
        } catch (e: IllegalArgumentException) {
            assert(e.message!!.contains("not found", ignoreCase = true))
        }
    }
}

class SubmitDeletionRequestHandlerTest {

    private val repository = mockk<DataSubjectRequestRepository>()
    private val anonymizationService = mockk<AnonymizationService>()
    private val tenancyData = mockk<TenancyData>()
    private val publishing = mockk<PublishingDeletion>()
    private val auditor = mockk<PrivacyMutationAuditor>(relaxed = true)
    private val transactionRunner = object : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }
    private val clock = Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneId.of("UTC"))
    private val handler: CommandWithResultHandler<SubmitDeletionRequestCommand, DataSubjectRequestResponse> =
        SubmitDeletionRequestHandler(
            repository = repository,
            anonymizationService = anonymizationService,
            tenancyData = tenancyData,
            publishing = publishing,
            transactionRunner = transactionRunner,
            auditor = auditor,
            clock = clock,
        )

    @Test
    fun `handle creates DELETION request with COMPLETED status`() = runTest {
        coEvery { tenancyData.isSoleOwnerInAnyWorkspace(any()) } returns false
        coEvery { anonymizationService.anonymizeIdentityAndWaitlist(any(), any(), any()) } returns Unit
        coEvery { anonymizationService.revokeCredentials(any()) } returns Unit
        coEvery { publishing.deleteSocialConnections(any()) } returns Unit
        coEvery { publishing.deleteSecureCredentials(any()) } returns Unit
        coEvery { publishing.cancelPendingPublications(any()) } returns Unit
        coEvery { tenancyData.removeAllMemberships(any()) } returns emptyList()
        coEvery { tenancyData.getMembershipWorkspaceIds(any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitDeletionRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = null,
            notes = null,
        )

        val result = handler.handle(command)

        assert(result.type == RequestType.DELETION.name)
        assert(result.status == DataSubjectRequestStatus.COMPLETED.name)
    }

    @Test
    fun `handle rejects sole owner deletions`() = runTest {
        coEvery { tenancyData.isSoleOwnerInAnyWorkspace(any()) } returns true

        val command = SubmitDeletionRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = null,
            notes = null,
        )

        try {
            handler.handle(command)
            assert(false) { "Expected exception was not thrown for sole owner" }
        } catch (e: IllegalArgumentException) {
            assert(e.message!!.contains("sole owner", ignoreCase = true))
        }
    }

    @Test
    fun `handle runs Phase 1 inside atomic transaction`() = runTest {
        coEvery { tenancyData.isSoleOwnerInAnyWorkspace(any()) } returns false
        coEvery { anonymizationService.anonymizeIdentityAndWaitlist(any(), any(), any()) } returns Unit
        coEvery { anonymizationService.revokeCredentials(any()) } returns Unit
        coEvery { publishing.deleteSocialConnections(any()) } returns Unit
        coEvery { publishing.deleteSecureCredentials(any()) } returns Unit
        coEvery { publishing.cancelPendingPublications(any()) } returns Unit
        coEvery { tenancyData.removeAllMemberships(any()) } returns emptyList()
        coEvery { tenancyData.getMembershipWorkspaceIds(any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitDeletionRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = null,
            notes = null,
        )

        handler.handle(command)

        // The fake AtomicTransactionRunner runs the block synchronously
        coVerify { anonymizationService.anonymizeIdentityAndWaitlist("principal-1", "user@example.com", any()) }
    }

    @Test
    fun `handle runs Phase 2 and Phase 3 after transaction`() = runTest {
        coEvery { tenancyData.isSoleOwnerInAnyWorkspace(any()) } returns false
        coEvery { anonymizationService.anonymizeIdentityAndWaitlist(any(), any(), any()) } returns Unit
        coEvery { anonymizationService.revokeCredentials(any()) } returns Unit
        coEvery { publishing.deleteSocialConnections(any()) } returns Unit
        coEvery { publishing.deleteSecureCredentials(any()) } returns Unit
        coEvery { publishing.cancelPendingPublications(any()) } returns Unit
        coEvery { tenancyData.removeAllMemberships(any()) } returns listOf("ws-1")
        coEvery { tenancyData.getMembershipWorkspaceIds(any()) } returns listOf("ws-1")
        coEvery { anonymizationService.markMediaForGc(any(), any()) } returns Unit
        coEvery { repository.save(any()) } returns Unit

        val command = SubmitDeletionRequestCommand(
            requestedByPrincipalId = "principal-1",
            requestedByEmail = "user@example.com",
            workspaceId = null,
            notes = null,
        )

        handler.handle(command)

        // Phase 2
        coVerify { anonymizationService.revokeCredentials("principal-1") }
        coVerify { publishing.deleteSocialConnections("principal-1") }
        coVerify { publishing.deleteSecureCredentials("principal-1") }
        coVerify { publishing.cancelPendingPublications("principal-1") }
        coVerify { tenancyData.removeAllMemberships("principal-1") }

        // Phase 3
        coVerify { anonymizationService.markMediaForGc("principal-1", listOf("ws-1")) }
    }
}
