package com.profiletailors.smp.privacy.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.privacy.domain.CreateDataSubjectRequest
import com.profiletailors.smp.privacy.domain.DataSubjectRequest
import com.profiletailors.smp.privacy.domain.DataSubjectRequestId
import com.profiletailors.smp.privacy.domain.DataSubjectRequestStatus
import com.profiletailors.smp.privacy.domain.RequestType
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcDataSubjectRequestRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var repository: R2dbcDataSubjectRequestRepository

    @BeforeEach
    fun setUp() = runTest {
        databaseClient.sql("DELETE FROM data_subject_requests").fetch().rowsUpdated().awaitSingle()
        repository = R2dbcDataSubjectRequestRepository(databaseClient)
    }

    @Test
    fun `save and findById roundtrip`() = runTest {
        val request = createRequest("dsr-11111111-1111-1111-1111-111111111111")

        repository.save(request)
        val found = repository.findById(request.id.value)

        assertNotNull(found)
        assertEquals(request.id, found!!.id)
        assertEquals(RequestType.ACCESS, found.requestType)
        assertEquals(DataSubjectRequestStatus.PENDING, found.status)
        assertEquals("principal-1", found.requestedBy)
        assertEquals("user@example.com", found.requestedByEmail)
        assertEquals("Test notes", found.notes)
        assertNotNull(found.expiresAt)
    }

    @Test
    fun `findById returns null for non-existent id`() = runTest {
        val found = repository.findById("nonexistent")

        assertNull(found)
    }

    @Test
    fun `save updates existing request`() = runTest {
        val id = "dsr-22222222-2222-2222-2222-222222222222"
        val request = createRequest(id)
        repository.save(request)

        val completed = request.transitionTo(DataSubjectRequestStatus.COMPLETED)
        repository.save(completed)
        val found = repository.findById(id)

        assertNotNull(found)
        assertEquals(DataSubjectRequestStatus.COMPLETED, found!!.status)
        assertNotNull(found.completedAt)
    }

    @Test
    fun `findByRequester returns requests for that principal`() = runTest {
        val req1 = createRequest("dsr-33333333-3333-3333-3333-333333333333", requestedBy = "principal-1")
        val req2 = createRequest("dsr-44444444-4444-4444-4444-444444444444", requestedBy = "principal-1")
        val req3 = createRequest("dsr-55555555-5555-5555-5555-555555555555", requestedBy = "principal-2")
        repository.save(req1)
        repository.save(req2)
        repository.save(req3)

        val results = repository.findByRequester("principal-1")

        assertEquals(2, results.size)
        assertEquals("principal-2", repository.findByRequester("principal-2").first().requestedBy)
    }

    @Test
    fun `findByRequester returns empty list when none found`() = runTest {
        val results = repository.findByRequester("unknown")

        assertEquals(0, results.size)
    }

    @Test
    fun `findByStatus filters by status`() = runTest {
        val pending = createRequest("dsr-66666666-6666-6666-6666-666666666666")
        val completed = createRequest("dsr-77777777-7777-7777-7777-777777777777")
            .transitionTo(DataSubjectRequestStatus.COMPLETED)
        repository.save(pending)
        repository.save(completed)

        val pendingResults = repository.findByStatus(DataSubjectRequestStatus.PENDING)
        val completedResults = repository.findByStatus(DataSubjectRequestStatus.COMPLETED)

        assertEquals(1, pendingResults.size)
        assertEquals(1, completedResults.size)
        assertEquals(DataSubjectRequestStatus.PENDING, pendingResults.first().status)
        assertEquals(DataSubjectRequestStatus.COMPLETED, completedResults.first().status)
    }

    @Test
    fun `findExpired returns requests past expiry`() = runTest {
        val notExpired = createRequest(
            id = "dsr-88888888-8888-8888-8888-888888888888",
            createdAt = Instant.parse("2026-07-15T10:00:00Z"),
        )
        repository.save(notExpired)

        val beforeExpiry = Instant.parse("2026-07-01T10:00:00Z")
        val expired = repository.findExpired(beforeExpiry)

        assertEquals(0, expired.size)
    }

    @Test
    fun `save persists across status transitions`() = runTest {
        val id = "dsr-99999999-9999-9999-9999-999999999999"
        val request = createRequest(id)
        repository.save(request)

        val pendingFromDb = repository.findById(id)
        assertEquals(DataSubjectRequestStatus.PENDING, pendingFromDb!!.status)

        val failed = request.transitionTo(DataSubjectRequestStatus.FAILED)
        repository.save(failed)

        val failedFromDb = repository.findById(id)
        assertEquals(DataSubjectRequestStatus.FAILED, failedFromDb!!.status)
    }

    @Test
    fun `save persists REJECTED with rejection reason`() = runTest {
        val id = "dsr-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val request = createRequest(id)
        repository.save(request)

        val rejected = request.transitionTo(DataSubjectRequestStatus.REJECTED, rejectionReason = "Invalid data")
        repository.save(rejected)

        val found = repository.findById(id)
        assertNotNull(found)
        assertEquals(DataSubjectRequestStatus.REJECTED, found!!.status)
        assertEquals("Invalid data", found.rejectionReason)
    }

    private fun createRequest(
        id: String,
        requestedBy: String = "principal-1",
        createdAt: Instant = Instant.parse("2026-07-15T10:00:00Z"),
    ): DataSubjectRequest = DataSubjectRequest.create(
        CreateDataSubjectRequest(
            id = DataSubjectRequestId(id),
            requestType = RequestType.ACCESS,
            requestedBy = requestedBy,
            requestedByEmail = "user@example.com",
            notes = "Test notes",
            createdAt = createdAt,
        ),
    )

    companion object {
        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("data_subject_request_repository")
    }
}
