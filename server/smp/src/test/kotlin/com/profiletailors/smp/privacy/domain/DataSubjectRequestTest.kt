package com.profiletailors.smp.privacy.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DataSubjectRequestTest {

    private val now = Instant.parse("2026-07-15T10:00:00Z")
    private val testId = DataSubjectRequestId("dsr-123e4567-e89b-12d3-a456-426614174000")

    @Test
    fun `creates request with PENDING status`() {
        val request = createRequest()

        assertEquals(DataSubjectRequestStatus.PENDING, request.status)
    }

    @Test
    fun `creates request with expiresAt set to createdAt plus 30 days`() {
        val request = createRequest()

        val expectedExpiry = now.plusSeconds(30 * 24 * 60 * 60)
        assertEquals(expectedExpiry, request.expiresAt)
    }

    @Test
    fun `creates request with all required fields`() {
        val request = createRequest()

        assertEquals(testId, request.id)
        assertEquals(RequestType.ACCESS, request.requestType)
        assertEquals("principal-1", request.requestedBy)
        assertEquals("user@example.com", request.requestedByEmail)
        assertNull(request.workspaceId)
        assertEquals("Test notes", request.notes)
        assertNull(request.correctionData)
        assertNull(request.resultRef)
        assertNull(request.rejectionReason)
        assertEquals(now, request.createdAt)
        assertEquals(now, request.updatedAt)
        assertNull(request.completedAt)
    }

    @Test
    fun `transitionTo moves from PENDING to COMPLETED and sets completedAt`() {
        val request = createRequest()
        val completedAt = Instant.parse("2026-07-19T11:00:00Z")

        val updated = request.transitionTo(DataSubjectRequestStatus.COMPLETED, completedAt = completedAt)

        assertEquals(DataSubjectRequestStatus.COMPLETED, updated.status)
        assertEquals(completedAt, updated.completedAt)
        assertTrue(updated.updatedAt >= now)
    }

    @Test
    fun `transitionTo moves from PENDING to REJECTED and requires rejectionReason`() {
        val request = createRequest()

        val updated = request.transitionTo(
            DataSubjectRequestStatus.REJECTED,
            rejectionReason = "Invalid request data",
        )

        assertEquals(DataSubjectRequestStatus.REJECTED, updated.status)
        assertEquals("Invalid request data", updated.rejectionReason)
    }

    @Test
    fun `transitionTo REJECTED throws when rejectionReason is null`() {
        val request = createRequest()

        assertThrows<IllegalArgumentException> {
            request.transitionTo(DataSubjectRequestStatus.REJECTED)
        }
    }

    @Test
    fun `transitionTo moves from PENDING to FAILED`() {
        val request = createRequest()

        val updated = request.transitionTo(DataSubjectRequestStatus.FAILED)

        assertEquals(DataSubjectRequestStatus.FAILED, updated.status)
    }

    @Test
    fun `transitionTo throws on invalid transition`() {
        val request = createRequest().transitionTo(DataSubjectRequestStatus.COMPLETED)

        assertThrows<IllegalStateException> {
            request.transitionTo(DataSubjectRequestStatus.FAILED)
        }
    }

    @Test
    fun `terminal states reject all transitions`() {
        val completed = createRequest().transitionTo(DataSubjectRequestStatus.COMPLETED)
        val rejected = createRequest().transitionTo(DataSubjectRequestStatus.REJECTED, rejectionReason = "reason")
        val failed = createRequest().transitionTo(DataSubjectRequestStatus.FAILED)

        DataSubjectRequestStatus.entries.forEach { target ->
            assertThrows<IllegalStateException> {
                completed.transitionTo(target)
            }
            assertThrows<IllegalStateException> {
                rejected.transitionTo(target)
            }
            assertThrows<IllegalStateException> {
                failed.transitionTo(target)
            }
        }
    }

    private fun createRequest(
        id: DataSubjectRequestId = testId,
        requestType: RequestType = RequestType.ACCESS,
        status: DataSubjectRequestStatus = DataSubjectRequestStatus.PENDING,
        requestedBy: String = "principal-1",
        requestedByEmail: String = "user@example.com",
        workspaceId: String? = null,
        notes: String? = "Test notes",
        correctionData: String? = null,
    ): DataSubjectRequest = DataSubjectRequest.create(
        id = id,
        requestType = requestType,
        requestedBy = requestedBy,
        requestedByEmail = requestedByEmail,
        workspaceId = workspaceId,
        notes = notes,
        correctionData = correctionData,
        createdAt = now,
    )
}
