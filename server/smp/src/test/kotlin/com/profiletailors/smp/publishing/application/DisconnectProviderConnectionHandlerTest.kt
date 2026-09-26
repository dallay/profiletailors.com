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
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentialGateway
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentials
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DisconnectProviderConnectionHandlerTest {
    @Test
    fun `invalidates credentials before removing the workspace connection`() = runTest {
        val order = mutableListOf<String>()
        val connectionId = "connection-1"
        val credentialId = UUID.randomUUID()
        val connectionRepository = RecordingConnectionRepository(
            SocialConnection(
                id = connectionId,
                workspaceId = "workspace-1",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-user-1",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = credentialId.toString(),
            ),
            order,
        )
        val credentialGateway = RecordingProviderCredentialGateway(order)
        val socialAccountRepository = RecordingSocialAccountRepository(order)
        val handler = DisconnectProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(
                PrincipalContext("principal-1", PrincipalType.USER, "principal@example.com"),
            ),
            resourceContextProvider = FixedResourceContextProvider(
                ResourceContext(ResourceContextType.WORKSPACE, workspaceId = "workspace-1"),
            ),
            socialConnectionRepository = connectionRepository,
            socialAccountRepository = socialAccountRepository,
            credentialGateway = credentialGateway,
            channelEventPublisher = RecordingChannelEventPublisher(order),
            transactionRunner = object : AtomicTransactionRunner {
                override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
            },
            clock = java.time.Clock.systemUTC(),
        )

        val result = handler.handle(DisconnectProviderConnectionCommand(SocialProvider.THREADS, connectionId))

        assertEquals(SocialConnectionStatus.DELETED, result.status)
        assertEquals(listOf("deleteAccount", "invalidate", "delete", "event"), order)
        assertTrue(credentialGateway.invalidated.contains(credentialId))
    }

    private class RecordingConnectionRepository(
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
        override suspend fun upsert(
            account: com.profiletailors.smp.publishing.domain.SocialAccount,
        ): com.profiletailors.smp.publishing.domain.SocialAccount = account
        override suspend fun findByWorkspaceAndId(
            workspaceId: String,
            accountId: String,
        ): com.profiletailors.smp.publishing.domain.SocialAccount? = null
        override suspend fun findFirstActiveByWorkspace(
            workspaceId: String,
        ): com.profiletailors.smp.publishing.domain.SocialAccount? = null
        override suspend fun deleteByConnectionId(connectionId: String) {
            order += "deleteAccount"
        }
    }

    private class RecordingProviderCredentialGateway(private val order: MutableList<String>) :
        ProviderCredentialGateway {
        val invalidated = mutableListOf<UUID>()
        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID =
            ownerId
        override suspend fun resolveCredential(id: UUID): ProviderCredentials = error("Not used")
        override suspend fun invalidateCredential(id: UUID) {
            order += "invalidate"
            invalidated += id
        }
    }

    private class RecordingChannelEventPublisher(private val order: MutableList<String>) : ChannelEventPublisher {
        override fun publish(event: ChannelEvent) {
            order += "event"
        }
    }

    private class FixedPrincipalContextProvider(private val context: PrincipalContext) : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = context
    }

    private class FixedResourceContextProvider(private val context: ResourceContext) : ResourceContextProvider {
        override fun current(): ResourceContext = context
    }
}
