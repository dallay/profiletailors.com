package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class CredentialDeletionTombstoneTest {
    @Test
    fun `should build tombstone with disconnect reason`() {
        val tombstone = CredentialDeletionTombstone(
            workspaceId = "workspace-1",
            connectionId = "connection-1",
            provider = SocialProvider.THREADS,
            providerConnectionRef = "threads-user-1",
            credentialReference = null,
            activityId = "pa-006",
            policyVersion = "",
            reason = CredentialDeletionReason.DISCONNECT,
            deletedAt = Instant.parse("2026-09-29T00:00:00Z"),
        )

        tombstone.workspaceId shouldBe "workspace-1"
        tombstone.reason shouldBe CredentialDeletionReason.DISCONNECT
    }

    @Test
    fun `should reject blank workspace connection ref and activity`() {
        shouldThrow<IllegalArgumentException> {
            CredentialDeletionTombstone(
                workspaceId = "",
                connectionId = "connection-1",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-user-1",
                credentialReference = null,
                activityId = "pa-006",
                policyVersion = "",
                reason = CredentialDeletionReason.DISCONNECT,
                deletedAt = Instant.now(),
            )
        }
        shouldThrow<IllegalArgumentException> {
            CredentialDeletionTombstone(
                workspaceId = "workspace-1",
                connectionId = "",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-user-1",
                credentialReference = null,
                activityId = "pa-006",
                policyVersion = "",
                reason = CredentialDeletionReason.DISCONNECT,
                deletedAt = Instant.now(),
            )
        }
        shouldThrow<IllegalArgumentException> {
            CredentialDeletionTombstone(
                workspaceId = "workspace-1",
                connectionId = "connection-1",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "",
                credentialReference = null,
                activityId = "pa-006",
                policyVersion = "",
                reason = CredentialDeletionReason.DISCONNECT,
                deletedAt = Instant.now(),
            )
        }
        shouldThrow<IllegalArgumentException> {
            CredentialDeletionTombstone(
                workspaceId = "workspace-1",
                connectionId = "connection-1",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-user-1",
                credentialReference = null,
                activityId = "",
                policyVersion = "",
                reason = CredentialDeletionReason.DISCONNECT,
                deletedAt = Instant.now(),
            )
        }
    }
}
