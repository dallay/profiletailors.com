package com.profiletailors.smp.privacy.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DataSubjectRequestRepositoryTest {

    private val repository = mockk<DataSubjectRequestRepository>()
    private val now = Instant.parse("2026-07-15T10:00:00Z")

    private val sampleRequest = DataSubjectRequest.create(
        CreateDataSubjectRequest(
            id = DataSubjectRequestId("dsr-550e8400-e29b-41d4-a716-446655440000"),
            requestType = RequestType.ACCESS,
            requestedBy = "principal-1",
            requestedByEmail = "user@example.com",
            notes = "Test notes",
            createdAt = now,
        ),
    )

    @Test
    fun `save persists a request`() = runTest {
        coEvery { repository.save(any()) } returns Unit

        repository.save(sampleRequest)

        coVerify(exactly = 1) { repository.save(sampleRequest) }
    }

    @Test
    fun `findById returns request when found`() = runTest {
        coEvery { repository.findById(sampleRequest.id.value) } returns sampleRequest

        val result = repository.findById(sampleRequest.id.value)

        assertNotNull(result)
        assertEquals(sampleRequest.id, result.id)
        assertEquals(sampleRequest.status, result.status)
        coVerify(exactly = 1) { repository.findById(sampleRequest.id.value) }
    }

    @Test
    fun `findById returns null when not found`() = runTest {
        coEvery { repository.findById("nonexistent") } returns null

        val result = repository.findById("nonexistent")

        assertNull(result)
        coVerify(exactly = 1) { repository.findById("nonexistent") }
    }

    @Test
    fun `findByRequester returns list of requests for that principal`() = runTest {
        coEvery { repository.findByRequester("principal-1") } returns listOf(sampleRequest)

        val results = repository.findByRequester("principal-1")

        assertEquals(1, results.size)
        assertEquals(sampleRequest.id, results.first().id)
        coVerify(exactly = 1) { repository.findByRequester("principal-1") }
    }

    @Test
    fun `findByRequester returns empty list when no requests found`() = runTest {
        coEvery { repository.findByRequester("unknown") } returns emptyList()

        val results = repository.findByRequester("unknown")

        assertTrue(results.isEmpty())
        coVerify(exactly = 1) { repository.findByRequester("unknown") }
    }

    @Test
    fun `findByStatus returns requests with matching status`() = runTest {
        coEvery { repository.findByStatus(DataSubjectRequestStatus.PENDING) } returns listOf(sampleRequest)
        coEvery { repository.findByStatus(DataSubjectRequestStatus.COMPLETED) } returns emptyList()

        val pending = repository.findByStatus(DataSubjectRequestStatus.PENDING)
        val completed = repository.findByStatus(DataSubjectRequestStatus.COMPLETED)

        assertEquals(1, pending.size)
        assertTrue(completed.isEmpty())
        coVerify(exactly = 1) { repository.findByStatus(DataSubjectRequestStatus.PENDING) }
        coVerify(exactly = 1) { repository.findByStatus(DataSubjectRequestStatus.COMPLETED) }
    }

    @Test
    fun `findExpired returns requests before given timestamp`() = runTest {
        val expiry = now.plusSeconds(30 * 24 * 60 * 60)
        coEvery { repository.findExpired(any()) } returns listOf(sampleRequest)

        val results = repository.findExpired(expiry)

        assertEquals(1, results.size)
        coVerify(exactly = 1) { repository.findExpired(expiry) }
    }

    @Test
    fun `findExpired returns empty when no requests are expired`() = runTest {
        val farFuture = now.plusSeconds(365 * 24 * 60 * 60)
        coEvery { repository.findExpired(farFuture) } returns emptyList()

        val results = repository.findExpired(farFuture)

        assertTrue(results.isEmpty())
        coVerify(exactly = 1) { repository.findExpired(farFuture) }
    }
}
