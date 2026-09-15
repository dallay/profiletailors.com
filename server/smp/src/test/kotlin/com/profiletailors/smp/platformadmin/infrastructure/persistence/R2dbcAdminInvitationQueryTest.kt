package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.application.model.AdminDirectInvitationSummary
import com.profiletailors.smp.platformadmin.application.query.ListAdminDirectInvitationsQuery
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.RowsFetchSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.function.BiFunction
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class R2dbcAdminInvitationQueryTest {

    private val invitationRepository = mockk<WaitlistInvitationRepository>()
    private val databaseClient = mockk<DatabaseClient>()
    private val query = R2dbcAdminInvitationQuery(invitationRepository, databaseClient)

    @Test
    fun `findById returns summary when invitation exists`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = WaitlistInvitation(
            id = WaitlistInvitationId(invitationId),
            waitlistEntryId = "entry-1",
            tokenHash = "hash",
            status = WaitlistInvitationStatus.ACTIVE,
            issuedAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-02-01T00:00:00Z"),
            createdBy = UUID.randomUUID(),
            deliveryStatus = InvitationDeliveryStatus.SENT,
            deliveryAttemptCount = 2,
            version = 3,
        )
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns invitation

        val result = query.findById(invitationId)

        assertNotNull(result)
        assertEquals(invitationId, result.id)
        assertEquals("entry-1", result.waitlistEntryId)
        assertEquals("ACTIVE", result.status)
        assertEquals("SENT", result.deliveryStatus)
        assertEquals(2, result.deliveryAttemptCount)
        assertEquals(3, result.version)
    }

    @Test
    fun `findById returns null when invitation does not exist`() = runTest {
        val invitationId = UUID.randomUUID()
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns null

        val result = query.findById(invitationId)

        assertNull(result)
    }

    @Test
    fun `findById maps accepted invitation with acceptedAt`() = runTest {
        val invitationId = UUID.randomUUID()
        val acceptedAt = Instant.parse("2026-01-15T10:00:00Z")
        val invitation = WaitlistInvitation(
            id = WaitlistInvitationId(invitationId),
            waitlistEntryId = "entry-2",
            tokenHash = "hash",
            status = WaitlistInvitationStatus.ACCEPTED,
            issuedAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-02-01T00:00:00Z"),
            acceptedAt = acceptedAt,
            createdBy = UUID.randomUUID(),
            deliveryStatus = InvitationDeliveryStatus.SENT,
            version = 1,
        )
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns invitation

        val result = query.findById(invitationId)

        assertNotNull(result)
        assertEquals(acceptedAt, result.acceptedAt)
        assertEquals("ACCEPTED", result.status)
    }

    @Test
    fun `findById maps revoked invitation with revokedAt and revokedBy`() = runTest {
        val invitationId = UUID.randomUUID()
        val revokedBy = UUID.randomUUID()
        val revokedAt = Instant.parse("2026-01-20T12:00:00Z")
        val invitation = WaitlistInvitation(
            id = WaitlistInvitationId(invitationId),
            waitlistEntryId = "entry-3",
            tokenHash = "hash",
            status = WaitlistInvitationStatus.REVOKED,
            issuedAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-02-01T00:00:00Z"),
            revokedAt = revokedAt,
            revokedBy = revokedBy,
            createdBy = UUID.randomUUID(),
            deliveryStatus = InvitationDeliveryStatus.FAILED,
            version = 2,
        )
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns invitation

        val result = query.findById(invitationId)

        assertNotNull(result)
        assertEquals(revokedAt, result.revokedAt)
        assertEquals(revokedBy, result.revokedBy)
        assertEquals("REVOKED", result.status)
        assertEquals("FAILED", result.deliveryStatus)
    }

    @Test
    fun `list rejects negative page`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            query.list(ListAdminDirectInvitationsQuery(page = -1, size = 25))
        }
    }

    @Test
    fun `list rejects oversize page`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            query.list(ListAdminDirectInvitationsQuery(page = 0, size = 101))
        }
    }

    @Test
    fun `list reads direct rows ordered by created_at desc`() = runTest {
        val sqls = stubList(total = 2, rows = listOf(directRow(), directRow(email = "ops@example.com")))
        val result = query.list(ListAdminDirectInvitationsQuery(page = 0, size = 25))

        assertEquals(2, result.items.size)
        assertEquals(2, result.totalElements)
        assertEquals(0, result.page)
        assertTrue(sqls.any { it.contains("source = 'DIRECT'") })
        assertTrue(sqls.any { it.contains("ORDER BY created_at DESC") })
        assertTrue(sqls.none { it.contains("WAITLIST") })
    }

    @Test
    fun `list applies status equality and normalized email substring`() = runTest {
        val sqls = mutableListOf<String>()
        val countSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val dataSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(capture(sqls)) } answers {
            if (firstArg<String>().contains("COUNT(*)")) countSpec else dataSpec
        }
        every { countSpec.bind(any<String>(), any<Any>()) } answers { countSpec }
        every { dataSpec.bind(any<String>(), any<Any>()) } answers { dataSpec }
        val countRows = mockk<RowsFetchSpec<Long>>(relaxed = true)
        every { countSpec.map(any<BiFunction<Row, RowMetadata, Long>>()) } returns countRows
        every { countRows.one() } returns Mono.just(0L)
        val dataRows = mockk<RowsFetchSpec<AdminDirectInvitationSummary>>(relaxed = true)
        val slot = slot<BiFunction<Row, RowMetadata, AdminDirectInvitationSummary>>()
        every { dataSpec.map(capture(slot)) } returns dataRows
        every { dataRows.all() } returns Flux.empty()

        query.list(ListAdminDirectInvitationsQuery(page = 1, size = 10, status = "ACTIVE", email = "  OPS@  "))

        verify { dataSpec.bind("status", "ACTIVE") }
        verify { dataSpec.bind("email", "ops@") }
        verify { dataSpec.bind("size", 10) }
        verify { dataSpec.bind("offset", 10L) }
        assertTrue(sqls.any { it.contains("status = :status") })
        assertTrue(sqls.any { it.contains("invited_email_normalized LIKE") })
    }

    @Test
    fun `list maps rows without token material`() = runTest {
        stubList(total = 1, rows = listOf(directRow()))

        val result = query.list(ListAdminDirectInvitationsQuery(page = 0, size = 25))

        val item = result.items.single()
        assertEquals("ops@example.com", item.email)
        assertEquals("EXISTING_WORKSPACE", item.target)
        assertEquals("ws-1", item.workspaceId)
        assertEquals("ACTIVE", item.status)
        assertEquals(3L, item.version)
    }

    @Test
    fun `list returns empty page with total zero`() = runTest {
        stubList(total = 0, rows = emptyList())

        val result = query.list(ListAdminDirectInvitationsQuery(page = 0, size = 25, status = "REVOKED"))

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.totalElements)
        assertEquals(0, result.totalPages)
    }

    private fun directRow(email: String = "ops@example.com"): Map<String, Any?> = mapOf(
        "id" to UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
        "invited_email_normalized" to email,
        "target" to "EXISTING_WORKSPACE",
        "workspace_id" to "ws-1",
        "status" to "ACTIVE",
        "expires_at" to OffsetDateTime.ofInstant(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC),
        "version" to 3L,
    )

    private fun stubList(total: Long, rows: List<Map<String, Any?>>): MutableList<String> {
        val sqls = mutableListOf<String>()
        val countSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val dataSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(capture(sqls)) } answers {
            if (firstArg<String>().contains("COUNT(*)")) countSpec else dataSpec
        }
        every { countSpec.bind(any<String>(), any<Any>()) } answers { countSpec }
        every { dataSpec.bind(any<String>(), any<Any>()) } answers { dataSpec }
        val countRows = mockk<RowsFetchSpec<Long>>(relaxed = true)
        every { countSpec.map(any<BiFunction<Row, RowMetadata, Long>>()) } returns countRows
        every { countRows.one() } returns Mono.just(total)
        val dataRows = mockk<RowsFetchSpec<AdminDirectInvitationSummary>>(relaxed = true)
        val slot = slot<BiFunction<Row, RowMetadata, AdminDirectInvitationSummary>>()
        every { dataSpec.map(capture(slot)) } returns dataRows
        every { dataRows.all() } answers {
            Flux.fromIterable(rows.map { columns -> slot.captured.apply(stubRow(columns), mockk()) })
        }
        return sqls
    }

    private fun stubRow(columns: Map<String, Any?>): Row {
        val row = mockk<Row>()
        columns.forEach { (name, value) ->
            when (value) {
                is UUID -> every { row.get(name, UUID::class.java) } returns value
                is String -> every { row.get(name, String::class.java) } returns value
                is OffsetDateTime -> every { row.get(name, OffsetDateTime::class.java) } returns value
                is Long -> every { row.get(name, Long::class.java) } returns value
                null -> every { row.get(name, String::class.java) } returns null
                else -> throw IllegalArgumentException("Unsupported stub column $name")
            }
        }
        return row
    }
}
