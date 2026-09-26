package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentialGateway
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

class DisconnectProviderConnectionHandlerTest {
    private val credentialId = UUID.randomUUID()
    private val connection = SocialConnection(
        id = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.THREADS,
        providerConnectionRef = "threads-user-1",
        status = SocialConnectionStatus.ACTIVE,
        credentialReference = credentialId.toString(),
    )
    private val connections = mockk<SocialConnectionRepository>(relaxed = true)
    private val accounts = mockk<SocialAccountRepository>(relaxed = true)
    private val credentials = mockk<ProviderCredentialGateway>(relaxed = true)
    private val events = mockk<ChannelEventPublisher>(relaxed = true)
    private val handler = DisconnectProviderConnectionHandler(
        principalContextProvider = object : PrincipalContextProvider {
            override suspend fun current() =
                PrincipalContext("principal-1", PrincipalType.USER, "principal@example.com")
        },
        resourceContextProvider = object : ResourceContextProvider {
            override fun current() = ResourceContext(ResourceContextType.WORKSPACE, workspaceId = "workspace-1")
        },
        socialConnectionRepository = connections,
        socialAccountRepository = accounts,
        credentialGateway = credentials,
        channelEventPublisher = events,
        transactionRunner = object : AtomicTransactionRunner {
            override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
        },
        clock = Clock.systemUTC(),
    )
    private val command = DisconnectProviderConnectionCommand(SocialProvider.THREADS, connection.id)

    @Test
    fun `should invalidate credentials when the last referencing connection is deleted`() = runTest {
        coEvery { connections.findByWorkspaceAndId("workspace-1", connection.id) } returns connection
        coEvery { connections.existsByCredentialReference(credentialId.toString()) } returns false

        handler.handle(command).status shouldBe SocialConnectionStatus.DELETED

        coVerifyOrder {
            accounts.deleteByConnectionId(connection.id)
            connections.deleteByWorkspaceAndId("workspace-1", connection.id)
            connections.existsByCredentialReference(credentialId.toString())
            credentials.invalidateCredential(credentialId)
            events.publish(any())
        }
    }

    @Test
    fun `should retain credentials when another workspace still references them`() = runTest {
        coEvery { connections.findByWorkspaceAndId("workspace-1", connection.id) } returns connection
        coEvery { connections.existsByCredentialReference(credentialId.toString()) } returns true

        handler.handle(command)

        coVerify(exactly = 1) { connections.deleteByWorkspaceAndId("workspace-1", connection.id) }
        coVerify(exactly = 0) { credentials.invalidateCredential(any()) }
    }

    @Test
    fun `should report not found when the connection belongs to another workspace`() = runTest {
        coEvery { connections.findByWorkspaceAndId("workspace-1", connection.id) } returns null

        shouldThrow<IllegalArgumentException> { handler.handle(command) }.message shouldBe
            "Publishing connection was not found."

        verifyNoMutations()
    }

    @Test
    fun `should reject disconnect when the provider does not match`() = runTest {
        coEvery { connections.findByWorkspaceAndId("workspace-1", connection.id) } returns connection

        shouldThrow<IllegalArgumentException> {
            handler.handle(command.copy(provider = SocialProvider.LINKEDIN))
        }.message shouldBe "Publishing provider does not match the connection."

        verifyNoMutations()
    }

    private fun verifyNoMutations() {
        coVerify(exactly = 0) {
            accounts.deleteByConnectionId(any())
            connections.deleteByWorkspaceAndId(any(), any())
            connections.existsByCredentialReference(any())
            credentials.invalidateCredential(any())
        }
        verify(exactly = 0) { events.publish(any()) }
    }
}
