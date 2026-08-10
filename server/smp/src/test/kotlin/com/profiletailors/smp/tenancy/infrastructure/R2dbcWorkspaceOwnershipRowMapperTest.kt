package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.r2dbc.spi.Row
import org.junit.jupiter.api.Test
import java.time.Instant

private const val COL_OWNER_PRINCIPAL_ID = "owner_principal_id"

class R2dbcWorkspaceOwnershipRowMapperTest {

    private fun row(ownerPrincipalId: String?): Row = mockk {
        every { get(COL_OWNER_PRINCIPAL_ID, String::class.java) } returns ownerPrincipalId
    }

    @Test
    fun `mapOwnerPrincipalId returns the owner principal id`() {
        mapOwnerPrincipalId(row(ownerPrincipalId = "owner-1")) shouldBe "owner-1"
    }

    @Test
    fun `mapOwnerPrincipalId throws when owner principal id is null`() {
        shouldThrow<IllegalArgumentException> {
            mapOwnerPrincipalId(row(ownerPrincipalId = null))
        }
    }

    @Test
    fun `mapWorkspaceOwnership maps a complete row`() {
        val row = mockk<Row> {
            every { get("workspace_id", String::class.java) } returns "ws-1"
            every { get("owner_principal_id", String::class.java) } returns "owner-1"
            every { get("owner_principal_type", String::class.java) } returns "USER"
            every { get("created_by", String::class.java) } returns "creator-1"
            every { get("created_at", Instant::class.java) } returns Instant.parse("2026-08-01T10:00:00Z")
        }

        val ownership = mapWorkspaceOwnership(row)

        ownership.workspaceId shouldBe "ws-1"
        ownership.ownerPrincipalId shouldBe "owner-1"
        ownership.ownerPrincipalType shouldBe PrincipalType.USER
        ownership.createdBy shouldBe "creator-1"
        ownership.createdAt shouldBe Instant.parse("2026-08-01T10:00:00Z")
    }

    @Test
    fun `mapWorkspaceOwnership throws when owner principal id is null`() {
        val row = mockk<Row> {
            every { get("workspace_id", String::class.java) } returns "ws-1"
            every { get("owner_principal_id", String::class.java) } returns null
        }

        shouldThrow<IllegalArgumentException> {
            mapWorkspaceOwnership(row)
        }
    }

    @Test
    fun `mapWorkspaceOwnership throws when workspace id is null`() {
        val row = mockk<Row> {
            every { get("workspace_id", String::class.java) } returns null
        }

        shouldThrow<IllegalArgumentException> {
            mapWorkspaceOwnership(row)
        }
    }

    @Test
    fun `mapWorkspaceOwnership throws when owner principal type is null`() {
        val row = mockk<Row> {
            every { get("workspace_id", String::class.java) } returns "ws-1"
            every { get("owner_principal_id", String::class.java) } returns "owner-1"
            every { get("owner_principal_type", String::class.java) } returns null
        }

        shouldThrow<IllegalArgumentException> {
            mapWorkspaceOwnership(row)
        }
    }
}
