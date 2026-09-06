package com.profiletailors.smp.privacy.application

import com.profiletailors.smp.privacy.domain.DataSubjectRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PrivacyHandlerSerializationTest {
    private val repository = mockk<DataSubjectRequestRepository>()
    private val aggregationService = mockk<DataAggregationService>()
    private val anonymizationService = mockk<AnonymizationService>()
    private val storage = mockk<Storage>()
    private val auditor = mockk<PrivacyMutationAuditor>(relaxed = true)
    private val serializer = mockk<PrivacyDataSerializer>()
    private val clock = Clock.fixed(Instant.parse("2026-09-06T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `access request persists the serializer output`() = runTest {
        val aggregatedData = mapOf("identity" to mapOf("email" to "user@example.com"))
        every { serializer.toJson(aggregatedData) } returns "serialized-access-data"
        coEvery { aggregationService.aggregate(any(), any()) } returns aggregatedData
        coEvery { repository.save(any()) } returns Unit
        val handler = SubmitAccessRequestHandler(
            repository = repository,
            dataAggregationService = aggregationService,
            auditor = auditor,
            clock = clock,
            serializer = serializer,
        )

        handler.handle(accessCommand())

        verify(exactly = 1) { serializer.toJson(aggregatedData) }
        coVerify(exactly = 1) { repository.save(match { it.resultRef == "serialized-access-data" }) }
    }

    @Test
    fun `export request uploads the serializer output`() = runTest {
        val aggregatedData = mapOf("identity" to null)
        every { serializer.toJson(aggregatedData) } returns "serialized-export-data"
        coEvery { aggregationService.aggregate(any(), any()) } returns aggregatedData
        coEvery { storage.uploadJson(any(), any()) } returns "https://storage.example/export.json"
        coEvery { repository.save(any()) } returns Unit
        val handler = SubmitExportRequestHandler(
            repository = repository,
            dataAggregationService = aggregationService,
            storage = storage,
            auditor = auditor,
            clock = clock,
            serializer = serializer,
        )

        handler.handle(exportCommand())

        verify(exactly = 1) { serializer.toJson(aggregatedData) }
        coVerify(exactly = 1) { storage.uploadJson(match { it.endsWith(".json") }, "serialized-export-data") }
    }

    @Test
    fun `correction request persists the serializer output`() = runTest {
        every { serializer.toJson(any()) } returns "serialized-correction-data"
        coEvery { anonymizationService.verifyCorrection(any(), any(), any()) } returns
            AnonymizationService.CorrectionResult.Success
        coEvery { repository.save(any()) } returns Unit
        val handler = SubmitCorrectionRequestHandler(
            repository = repository,
            anonymizationService = anonymizationService,
            auditor = auditor,
            clock = clock,
            serializer = serializer,
        )

        handler.handle(correctionCommand())

        verify(exactly = 1) {
            serializer.toJson(
                match {
                    it == mapOf(
                        "field" to "username",
                        "newValue" to "updated-user",
                    )
                },
            )
        }
        coVerify(exactly = 1) {
            repository.save(match { it.correctionData == "serialized-correction-data" })
        }
    }

    private fun accessCommand() = SubmitAccessRequestCommand(
        requestedByPrincipalId = "principal-1",
        requestedByEmail = "user@example.com",
        workspaceId = "workspace-1",
        notes = null,
    )

    private fun exportCommand() = SubmitExportRequestCommand(
        requestedByPrincipalId = "principal-1",
        requestedByEmail = "user@example.com",
        workspaceId = "workspace-1",
        notes = null,
    )

    private fun correctionCommand() = SubmitCorrectionRequestCommand(
        requestedByPrincipalId = "principal-1",
        requestedByEmail = "user@example.com",
        field = CorrectionField.USERNAME,
        newValue = "updated-user",
        workspaceId = "workspace-1",
        notes = null,
    )
}
