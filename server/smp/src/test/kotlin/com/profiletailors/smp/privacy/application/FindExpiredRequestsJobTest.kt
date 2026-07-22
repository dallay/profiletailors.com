package com.profiletailors.smp.privacy.application

import com.profiletailors.smp.privacy.domain.CreateDataSubjectRequest
import com.profiletailors.smp.privacy.domain.DataSubjectRequest
import com.profiletailors.smp.privacy.domain.DataSubjectRequestId
import com.profiletailors.smp.privacy.domain.DataSubjectRequestRepository
import com.profiletailors.smp.privacy.domain.RequestType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class FindExpiredRequestsJobTest {

    private val repository = mockk<DataSubjectRequestRepository>()
    private lateinit var job: FindExpiredRequestsJob

    private val now = Instant.parse("2026-07-19T10:00:00Z")

    @BeforeEach
    fun setUp() {
        job = FindExpiredRequestsJob(repository)
    }

    @Test
    fun `run finds expired requests and logs count`() = runTest {
        val expiredRequest = DataSubjectRequest.create(
            CreateDataSubjectRequest(
                id = DataSubjectRequestId("dsr-550e8400-e29b-41d4-a716-446655440000"),
                requestType = RequestType.ACCESS,
                requestedBy = "principal-1",
                requestedByEmail = "user@example.com",
                createdAt = now.minusSeconds(31 * 24 * 60 * 60), // 31 days ago — expired
            ),
        )
        coEvery { repository.findExpired(any()) } returns listOf(expiredRequest)

        val result = job.run()

        assertEquals(1, result.expiredCount)
        coVerify(exactly = 1) { repository.findExpired(any()) }
    }

    @Test
    fun `run returns zero when no expired requests`() = runTest {
        coEvery { repository.findExpired(any()) } returns emptyList()

        val result = job.run()

        assertEquals(0, result.expiredCount)
        assertTrue(result.durationMs >= 0)
        coVerify(exactly = 1) { repository.findExpired(any()) }
    }

    @Test
    fun `run handles multiple expired requests`() = runTest {
        val req1 = DataSubjectRequest.create(
            CreateDataSubjectRequest(
                id = DataSubjectRequestId.random(),
                requestType = RequestType.ACCESS,
                requestedBy = "principal-1",
                requestedByEmail = "a@example.com",
                createdAt = now.minusSeconds(31 * 24 * 60 * 60),
            ),
        )
        val req2 = DataSubjectRequest.create(
            CreateDataSubjectRequest(
                id = DataSubjectRequestId.random(),
                requestType = RequestType.DELETION,
                requestedBy = "principal-2",
                requestedByEmail = "b@example.com",
                createdAt = now.minusSeconds(35 * 24 * 60 * 60),
            ),
        )
        coEvery { repository.findExpired(any()) } returns listOf(req1, req2)

        val result = job.run()

        assertEquals(2, result.expiredCount)
        coVerify(exactly = 1) { repository.findExpired(any()) }
    }
}
