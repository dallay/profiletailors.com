package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.CredentialDeletionReason
import com.profiletailors.smp.publishing.domain.CredentialDeletionTombstone
import com.profiletailors.smp.publishing.domain.CredentialDeletionTombstoneRepository
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentialGateway
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentials
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class DisconnectProviderConnectionIdempotencyTest {
    private val fixedInstant = Instant.parse("2026-09-29T00:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Test
    fun `replays tombstone without side effects when connection was already deleted`() = runTest {
        val order = mutableListOf<String>()
        val tombstone = CredentialDeletionTombstone(
            workspaceId = "workspace-1",
            connectionId = "connection-1",
            provider = SocialProvider.THREADS,
            providerConnectionRef = "threads-user-1",
            credentialReference = null,
            activityId = "pa-006",
            policyVersion = "",
            reason = CredentialDeletionReason.DISCONNECT,
            deletedAt = fixedInstant,
        )
        val handler = DisconnectProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(),
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
            socialConnectionRepository = MissingConnectionRepository(),
            socialAccountRepository = RecordingSocialAccountRepository(order),
            credentialGateway = RecordingProviderCredentialGateway(order),
            channelEventPublisher = RecordingChannelEventPublisher(order),
            transactionRunner = DirectTransactionRunner(),
            clock = fixedClock,
            tombstoneRepository = FixedTombstoneRepository(tombstone),
        )

        val result = handler.handle(DisconnectProviderConnectionCommand(SocialProvider.THREADS, "connection-1"))

        assertEquals(SocialConnectionStatus.DELETED, result.status)
        assertEquals("connection-1", result.connectionId)
        assertEquals("threads-user-1", result.account.providerAccountId)
        assertTrue(order.isEmpty())
    }

    @Test
    fun `records tombstone with pa-006 traceability on first disconnect`() = runTest {
        val order = mutableListOf<String>()
        val credentialId = UUID.randomUUID()
        val tombstones = RecordingTombstoneRepository()
        val handler = DisconnectProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(),
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
            socialConnectionRepository = SingleConnectionRepository(
                SocialConnection(
                    id = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.THREADS,
                    providerConnectionRef = "threads-user-1",
                    status = SocialConnectionStatus.ACTIVE,
                    credentialReference = credentialId.toString(),
                ),
                order,
            ),
            socialAccountRepository = RecordingSocialAccountRepository(order),
            credentialGateway = RecordingProviderCredentialGateway(order),
            channelEventPublisher = RecordingChannelEventPublisher(order),
            transactionRunner = DirectTransactionRunner(),
            clock = fixedClock,
            tombstoneRepository = tombstones,
        )

        val result = handler.handle(DisconnectProviderConnectionCommand(SocialProvider.THREADS, "connection-1"))

        assertEquals(SocialConnectionStatus.DELETED, result.status)
        assertEquals(listOf("deleteAccount", "invalidate", "delete"), order.filter { it != "event" })
        assertEquals(1, tombstones.recorded.size)
        assertEquals("pa-006", tombstones.recorded.single().activityId)
        assertEquals(CredentialDeletionReason.DISCONNECT, tombstones.recorded.single().reason)
        assertEquals("threads-user-1", tombstones.recorded.single().providerConnectionRef)
    }

    @Test
    fun `re-deletes restored connection when tombstone already exists`() = runTest {
        val order = mutableListOf<String>()
        val tombstone = CredentialDeletionTombstone(
            workspaceId = "workspace-1",
            connectionId = "connection-1",
            provider = SocialProvider.THREADS,
            providerConnectionRef = "threads-user-1",
            credentialReference = null,
            activityId = "pa-006",
            policyVersion = "",
            reason = CredentialDeletionReason.DISCONNECT,
            deletedAt = fixedInstant,
        )
        val handler = DisconnectProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(),
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
            socialConnectionRepository = SingleConnectionRepository(
                SocialConnection(
                    id = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.THREADS,
                    providerConnectionRef = "threads-user-1",
                    status = SocialConnectionStatus.ACTIVE,
                    credentialReference = null,
                ),
                order,
            ),
            socialAccountRepository = RecordingSocialAccountRepository(order),
            credentialGateway = RecordingProviderCredentialGateway(order),
            channelEventPublisher = RecordingChannelEventPublisher(order),
            transactionRunner = DirectTransactionRunner(),
            clock = fixedClock,
            tombstoneRepository = FixedTombstoneRepository(tombstone),
        )

        val result = handler.handle(DisconnectProviderConnectionCommand(SocialProvider.THREADS, "connection-1"))

        assertEquals(SocialConnectionStatus.DELETED, result.status)
        assertTrue(order.contains("deleteAccount"))
        assertTrue(order.contains("delete"))
    }

    @Test
    fun `rejects provider mismatch on tombstone replay`() = runTest {
        val tombstone = CredentialDeletionTombstone(
            workspaceId = "workspace-1",
            connectionId = "connection-1",
            provider = SocialProvider.THREADS,
            providerConnectionRef = "threads-user-1",
            credentialReference = null,
            activityId = "pa-006",
            policyVersion = "",
            reason = CredentialDeletionReason.DISCONNECT,
            deletedAt = fixedInstant,
        )
        val handler = DisconnectProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(),
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
            socialConnectionRepository = MissingConnectionRepository(),
            socialAccountRepository = RecordingSocialAccountRepository(mutableListOf()),
            credentialGateway = RecordingProviderCredentialGateway(mutableListOf()),
            channelEventPublisher = RecordingChannelEventPublisher(mutableListOf()),
            transactionRunner = DirectTransactionRunner(),
            clock = fixedClock,
            tombstoneRepository = FixedTombstoneRepository(tombstone),
        )

        shouldThrow<IllegalArgumentException> {
            handler.handle(DisconnectProviderConnectionCommand(SocialProvider.LINKEDIN, "connection-1"))
        }
    }

    @Test
    fun `throws not found when connection is unknown and no tombstone exists`() = runTest {
        val handler = DisconnectProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(),
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
            socialConnectionRepository = MissingConnectionRepository(),
            socialAccountRepository = RecordingSocialAccountRepository(mutableListOf()),
            credentialGateway = RecordingProviderCredentialGateway(mutableListOf()),
            channelEventPublisher = RecordingChannelEventPublisher(mutableListOf()),
            transactionRunner = DirectTransactionRunner(),
            clock = fixedClock,
            tombstoneRepository = FixedTombstoneRepository(null),
        )

        shouldThrow<IllegalArgumentException> {
            handler.handle(DisconnectProviderConnectionCommand(SocialProvider.THREADS, "missing"))
        }
    }

    private class FixedPrincipalContextProvider : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext =
            PrincipalContext("principal-1", PrincipalType.USER, "principal@example.com")
    }

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
    }

    private class DirectTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    private class MissingConnectionRepository : SocialConnectionRepository {
        override suspend fun upsert(connection: SocialConnection): SocialConnection = connection
        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? = null
        override suspend fun deleteByWorkspaceAndId(workspaceId: String, connectionId: String) = Unit
    }

    private class SingleConnectionRepository(
        private val connection: SocialConnection,
        private val order: MutableList<String>,
    ) : SocialConnectionRepository {
        override suspend fun upsert(connection: SocialConnection): SocialConnection = connection
        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? =
            connection.takeIf { it.workspaceId == workspaceId && it.id == connectionId }
        override suspend fun deleteByWorkspaceAndId(workspaceId: String, connectionId: String) {
            order += "delete"
        }
    }

    private class RecordingSocialAccountRepository(private val order: MutableList<String>) : SocialAccountRepository {
        override suspend fun upsert(account: com.profiletailors.smp.publishing.domain.SocialAccount) = account
        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String) = null
        override suspend fun findFirstActiveByWorkspace(workspaceId: String) = null
        override suspend fun listActiveByWorkspace(
            workspaceId: String,
        ): List<com.profiletailors.smp.publishing.domain.SocialAccount> = emptyList()
        override suspend fun deleteByConnectionId(connectionId: String) {
            order += "deleteAccount"
        }
    }

    private class RecordingProviderCredentialGateway(private val order: MutableList<String>) :
        ProviderCredentialGateway {
        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID =
            ownerId
        override suspend fun resolveCredential(id: UUID): ProviderCredentials = error("Not used")
        override suspend fun invalidateCredential(id: UUID) {
            order += "invalidate"
        }
    }

    private class RecordingChannelEventPublisher(private val order: MutableList<String>) : ChannelEventPublisher {
        override fun publish(event: ChannelEvent) {
            order += "event"
        }
    }

    private class FixedTombstoneRepository(private val tombstone: CredentialDeletionTombstone?) :
        CredentialDeletionTombstoneRepository {
        override suspend fun findByWorkspaceAndConnection(workspaceId: String, connectionId: String) = tombstone
        override suspend fun record(tombstone: CredentialDeletionTombstone) = tombstone
    }

    private class RecordingTombstoneRepository : CredentialDeletionTombstoneRepository {
        val recorded = mutableListOf<CredentialDeletionTombstone>()
        override suspend fun findByWorkspaceAndConnection(workspaceId: String, connectionId: String) = null
        override suspend fun record(tombstone: CredentialDeletionTombstone): CredentialDeletionTombstone {
            recorded += tombstone
            return tombstone
        }
    }
}
