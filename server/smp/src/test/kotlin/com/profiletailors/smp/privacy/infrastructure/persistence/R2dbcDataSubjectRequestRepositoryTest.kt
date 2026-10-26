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

    @Test
    fun `native UUID workspace round trips with requester identity`() = runTest {
        val request = createRequest("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
            .copy(workspaceId = "cccccccc-cccc-cccc-cccc-cccccccccccc")

        repository.save(request)

        val found = requireNotNull(repository.findById(request.id.value))
        assertEquals(request.workspaceId, found.workspaceId)
        assertEquals(request.requestedBy, found.requestedBy)
    }

    @BeforeEach
    fun setUp() = runTest {
        databaseClient.sql("DELETE FROM data_subject_requests").fetch().rowsUpdated().awaitSingle()
        repository = R2dbcDataSubjectRequestRepository(databaseClient)
    }

    @Test
    fun `save and findById roundtrip`() = runTest {
        val request = createRequest("11111111-1111-1111-1111-111111111111")

        repository.save(request)
        val found = repository.findById(request.id.value)

        assertNotNull(found)
        assertEquals(request.id, found!!.id)
        assertEquals(RequestType.ACCESS, found.requestType)
        assertEquals(DataSubjectRequestStatus.PENDING, found.status)
        assertEquals(PRINCIPAL_1, found.requestedBy)
        assertEquals("user@example.com", found.requestedByEmail)
        assertEquals("Test notes", found.notes)
        assertNotNull(found.expiresAt)
    }

    @Test
    fun `findById returns null for non-existent id`() = runTest {
        val found = repository.findById(NONEXISTENT)

        assertNull(found)
    }

    @Test
    fun `save updates existing request`() = runTest {
        val id = "22222222-2222-2222-2222-222222222222"
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
        val req1 = createRequest("33333333-3333-3333-3333-333333333333", requestedBy = PRINCIPAL_1)
        val req2 = createRequest("44444444-4444-4444-4444-444444444444", requestedBy = PRINCIPAL_1)
        val req3 = createRequest("55555555-5555-5555-5555-555555555555", requestedBy = PRINCIPAL_2)
        repository.save(req1)
        repository.save(req2)
        repository.save(req3)

        val results = repository.findByRequester(PRINCIPAL_1)

        assertEquals(2, results.size)
        assertEquals(PRINCIPAL_2, repository.findByRequester(PRINCIPAL_2).first().requestedBy)
    }

    @Test
    fun `findByRequester returns empty list when none found`() = runTest {
        val results = repository.findByRequester(UNKNOWN)

        assertEquals(0, results.size)
    }

    @Test
    fun `findByStatus filters by status`() = runTest {
        val pending = createRequest("66666666-6666-6666-6666-666666666666")
        val completed = createRequest("77777777-7777-7777-7777-777777777777")
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
            id = "88888888-8888-8888-8888-888888888888",
            createdAt = Instant.parse("2026-07-15T10:00:00Z"),
        )
        repository.save(notExpired)

        val beforeExpiry = Instant.parse("2026-07-01T10:00:00Z")
        val expired = repository.findExpired(beforeExpiry)

        assertEquals(0, expired.size)
    }

    @Test
    fun `save persists across status transitions`() = runTest {
        val id = "99999999-9999-9999-9999-999999999999"
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
        val id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
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
        requestedBy: String = PRINCIPAL_1,
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
        private const val PRINCIPAL_1 = "f4b0c946-5bac-5e21-a5af-5213b71804cc"
        private const val NONEXISTENT = "c855c7ae-709c-5477-a38d-b487e2704375"
        private const val PRINCIPAL_2 = "7a878b88-f89d-5e15-8c16-200e06a13313"
        private const val UNKNOWN = "0edc3579-77eb-5d16-b309-e651d9d22fa6"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("data_subject_request_repository")
    }
}
