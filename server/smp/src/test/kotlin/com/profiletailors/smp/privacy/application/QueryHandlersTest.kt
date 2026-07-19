package com.profiletailors.smp.privacy.application

import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.smp.privacy.domain.DataSubjectRequest
import com.profiletailors.smp.privacy.domain.DataSubjectRequestId
import com.profiletailors.smp.privacy.domain.DataSubjectRequestRepository
import com.profiletailors.smp.privacy.domain.DataSubjectRequestStatus
import com.profiletailors.smp.privacy.domain.RequestType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class CheckRequestStatusHandlerTest {

    private val repository = mockk<DataSubjectRequestRepository>()
    private val handler: QueryHandler<CheckRequestStatusQuery, DataSubjectRequestResponse?> =
        CheckRequestStatusHandler(repository)

    @Test
    fun `handle returns null when request not found`() = runTest {
        coEvery { repository.findById("nonexistent") } returns null

        val query = CheckRequestStatusQuery(requestId = "nonexistent")
        val result = handler.handle(query)

        assert(result == null)
    }

    @Test
    fun `handle finds request by id`() = runTest {
        val request = createSampleRequest(id = "dsr-123")
        coEvery { repository.findById("dsr-123") } returns request

        val query = CheckRequestStatusQuery(requestId = "dsr-123")
        handler.handle(query)

        coVerify { repository.findById("dsr-123") }
    }

    @Test
    fun `handle maps domain object to response`() = runTest {
        val completedAt = Instant.parse("2026-07-19T12:00:00Z")
        val request = createSampleRequest(
            id = "dsr-456",
            status = DataSubjectRequestStatus.COMPLETED,
            resultRef = "https://storage.example.com/export.zip",
            completedAt = completedAt,
        )
        coEvery { repository.findById("dsr-456") } returns request

        val query = CheckRequestStatusQuery(requestId = "dsr-456")
        val result = handler.handle(query)

        assert(result!!.id == "dsr-456")
        assert(result.status == DataSubjectRequestStatus.COMPLETED.name)
        assert(result.resultRef == "https://storage.example.com/export.zip")
        assert(result.completedAt == completedAt)
    }
}

class ListRequestsHandlerTest {

    private val repository = mockk<DataSubjectRequestRepository>()
    private val handler: QueryHandler<ListRequestsQuery, List<DataSubjectRequestResponse>> =
        ListRequestsHandler(repository)

    @Test
    fun `handle returns empty list when principal has no requests`() = runTest {
        coEvery { repository.findByRequester("principal-empty") } returns emptyList()

        val query = ListRequestsQuery(requesterPrincipalId = "principal-empty")
        val result = handler.handle(query)

        assert(result.isEmpty())
    }

    @Test
    fun `handle finds requests by requester`() = runTest {
        coEvery { repository.findByRequester("principal-1") } returns emptyList()

        val query = ListRequestsQuery(requesterPrincipalId = "principal-1")
        handler.handle(query)

        coVerify { repository.findByRequester("principal-1") }
    }

    @Test
    fun `handle maps all requests to responses`() = runTest {
        val requests = listOf(
            createSampleRequest(id = "dsr-1", type = RequestType.ACCESS),
            createSampleRequest(id = "dsr-2", type = RequestType.EXPORT, status = DataSubjectRequestStatus.COMPLETED),
        )
        coEvery { repository.findByRequester("principal-1") } returns requests

        val query = ListRequestsQuery(requesterPrincipalId = "principal-1")
        val result = handler.handle(query)

        assert(result.size == 2)
        assert(result[0].id == "dsr-1")
        assert(result[1].id == "dsr-2")
    }

    @Test
    fun `handle orders by most recent first`() = runTest {
        val earlier = createSampleRequest(
            id = "dsr-1",
            createdAt = Instant.parse("2026-07-18T10:00:00Z"),
        )
        val later = createSampleRequest(
            id = "dsr-2",
            createdAt = Instant.parse("2026-07-19T10:00:00Z"),
        )
        coEvery { repository.findByRequester("principal-1") } returns listOf(earlier, later)

        val query = ListRequestsQuery(requesterPrincipalId = "principal-1")
        val result = handler.handle(query)

        // Most recent first
        assert(result[0].id == "dsr-2")
        assert(result[1].id == "dsr-1")
    }
}

// ——————— test helpers ———————

private fun createSampleRequest(
    id: String = "dsr-test",
    type: RequestType = RequestType.ACCESS,
    status: DataSubjectRequestStatus = DataSubjectRequestStatus.PENDING,
    resultRef: String? = null,
    completedAt: Instant? = null,
    createdAt: Instant = Instant.parse("2026-07-19T10:00:00Z"),
): DataSubjectRequest = DataSubjectRequest(
    id = DataSubjectRequestId(id),
    requestType = type,
    status = status,
    requestedBy = "principal-1",
    requestedByEmail = "user@example.com",
    workspaceId = null,
    notes = null,
    correctionData = null,
    resultRef = resultRef,
    rejectionReason = null,
    createdAt = createdAt,
    updatedAt = createdAt,
    completedAt = completedAt,
    expiresAt = createdAt.plusSeconds(30 * 24 * 60 * 60),
)
