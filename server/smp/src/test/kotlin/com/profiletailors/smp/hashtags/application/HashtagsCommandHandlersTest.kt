package com.profiletailors.smp.hashtags.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.application.WorkspaceMembershipGate
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.hashtags.domain.HashtagSavedSet
import com.profiletailors.smp.hashtags.domain.HashtagSavedSetRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HashtagsCommandHandlersTest {
    private val repository = FakeRepository()
    private val context = FixedResourceContextProvider("workspace-1")
    private val clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `save handler trims names and normalizes hashtag prefixes`() = runTest {
        val result = SaveHashtagSetHandler(
            resourceContextProvider = context,
            repository = repository,
            clock = clock,
            membershipGate = mockk(relaxed = true),
        )
            .handle(SaveHashtagSetCommand("  Engineering  ", listOf("testing", "#quality")))

        assertEquals("Engineering", result.name)
        assertEquals(listOf("#testing", "#quality"), result.hashtags)
        assertEquals("workspace-1", repository.created.single().workspaceId)
    }

    @Test
    fun `save handler rejects blank names and empty sets`() = runTest {
        val handler = SaveHashtagSetHandler(
            resourceContextProvider = context,
            repository = repository,
            clock = clock,
            membershipGate = mockk(relaxed = true),
        )

        assertThrows(HashtagSetNameBlankException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(SaveHashtagSetCommand(" ", listOf("#tag"))) }
        }
        assertThrows(HashtagSetEmptyException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(SaveHashtagSetCommand("Set", emptyList())) }
        }
    }

    @Test
    fun `delete handler rejects a set outside the workspace`() = runTest {
        val handler = DeleteHashtagSetHandler(
            resourceContextProvider = context,
            repository = repository,
            membershipGate = mockk(relaxed = true),
        )

        assertThrows(HashtagSavedSetNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(DeleteHashtagSetCommand("missing")) }
        }
    }

    @Test
    fun `save handler denies without active membership`() = runTest {
        val freshRepository = FakeRepository()
        val handler = SaveHashtagSetHandler(
            resourceContextProvider = context,
            repository = freshRepository,
            clock = clock,
            membershipGate = denyingGate(),
        )

        assertThrows(AuthorizationDeniedException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(SaveHashtagSetCommand("Set", listOf("#tag"))) }
        }
        assertEquals(true, freshRepository.created.isEmpty())
    }

    @Test
    fun `delete handler denies without active membership`() = runTest {
        val handler = DeleteHashtagSetHandler(
            resourceContextProvider = context,
            repository = repository,
            membershipGate = denyingGate(),
        )

        assertThrows(AuthorizationDeniedException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(DeleteHashtagSetCommand("set-1")) }
        }
    }

    private fun denyingGate(): WorkspaceMembershipGate {
        val gate = mockk<WorkspaceMembershipGate>()
        coEvery { gate.requireActiveMember(any()) } throws AuthorizationDeniedException()
        return gate
    }

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
    }

    private class FakeRepository : HashtagSavedSetRepository {
        val created = mutableListOf<HashtagSavedSet>()

        override suspend fun listByWorkspace(workspaceId: String): List<HashtagSavedSet> = created.filter {
            it.workspaceId ==
                workspaceId
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, setId: String): HashtagSavedSet? =
            created.firstOrNull { it.workspaceId == workspaceId && it.id == setId }

        override suspend fun create(set: HashtagSavedSet): HashtagSavedSet {
            created += set
            return set
        }

        override suspend fun delete(workspaceId: String, setId: String): Boolean =
            created.removeIf { it.workspaceId == workspaceId && it.id == setId }
    }
}
