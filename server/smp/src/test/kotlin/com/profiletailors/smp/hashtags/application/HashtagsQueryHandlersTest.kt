package com.profiletailors.smp.hashtags.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.hashtags.domain.HashtagAnalysis
import com.profiletailors.smp.hashtags.domain.HashtagAnalysisPort
import com.profiletailors.smp.hashtags.domain.HashtagPopularity
import com.profiletailors.smp.hashtags.domain.HashtagSavedSet
import com.profiletailors.smp.hashtags.domain.HashtagSavedSetRepository
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipOperationRequiresWorkspaceContextException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class HashtagsQueryHandlersTest {
    @Test
    fun `analysis port can be supplied as a suspend function`() = runTest {
        val port = HashtagAnalysisPort { content ->
            HashtagAnalysis(
                content = content,
                detectedTopics = listOf("testing"),
                suggestedHashtags = emptyList(),
            )
        }

        val result = AnalyzeHashtagsHandler(port).handle(AnalyzeHashtagsQuery("Post"))

        assertEquals("Post", result.content)
        assertEquals(listOf("testing"), result.detectedTopics)
    }

    @Test
    fun `trending handler keeps only trending suggestions`() = runTest {
        val port = HashtagAnalysisPort {
            HashtagAnalysis(
                content = "",
                detectedTopics = emptyList(),
                suggestedHashtags = listOf(
                    suggestion("#trending", HashtagPopularity.TRENDING),
                    suggestion("#high", HashtagPopularity.HIGH),
                ),
            )
        }

        val result = GetTrendingHashtagsHandler(port).handle(GetTrendingHashtagsQuery)

        assertEquals(listOf("#trending"), result.hashtags.map { it.hashtag })
    }

    @Test
    fun `saved sets are read only from the current workspace`() = runTest {
        val repository = FakeSavedSetRepository(
            listOf(savedSet("workspace-1", "set-1")),
        )
        val handler = ListHashtagSavedSetsHandler(
            FixedResourceContextProvider("workspace-1"),
            repository,
        )

        val result = handler.handle(ListHashtagSavedSetsQuery(workspaceId = "ignored"))

        assertEquals(listOf("set-1"), result.sets.map { it.id })
        assertEquals(listOf("workspace-1"), repository.requestedWorkspaces)
    }

    @Test
    fun `analysis handler maps all returned fields`() = runTest {
        val port = HashtagAnalysisPort {
            HashtagAnalysis(
                content = "Content",
                detectedTopics = listOf("technology"),
                suggestedHashtags = listOf(suggestion("#tech", HashtagPopularity.HIGH)),
                maxRecommended = 7,
            )
        }

        val result = AnalyzeHashtagsHandler(port).handle(AnalyzeHashtagsQuery("Content"))

        assertEquals("Content", result.content)
        assertEquals(listOf("technology"), result.detectedTopics)
        assertEquals(7, result.maxRecommended)
        assertEquals("#tech", result.suggestedHashtags.single().hashtag)
    }

    @Test
    fun `saved-set query rejects missing workspace context`() {
        assertThrows(WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java) {
            kotlinx.coroutines.runBlocking {
                ListHashtagSavedSetsHandler(
                    FixedResourceContextProvider(null),
                    FakeSavedSetRepository(emptyList()),
                ).handle(ListHashtagSavedSetsQuery("ignored"))
            }
        }
    }

    private fun suggestion(hashtag: String, popularity: HashtagPopularity) =
        com.profiletailors.smp.hashtags.domain.HashtagSuggestion(
            hashtag = hashtag,
            relevanceScore = 0.9f,
            popularity = popularity,
            category = "test",
            usageCount = 1,
        )

    private fun savedSet(workspaceId: String, id: String) = HashtagSavedSet(
        id = id,
        workspaceId = workspaceId,
        name = "Set",
        hashtags = listOf("#testing"),
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private class FixedResourceContextProvider(private val workspaceId: String?) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }

    private class FakeSavedSetRepository(private val sets: List<HashtagSavedSet>) : HashtagSavedSetRepository {
        val requestedWorkspaces = mutableListOf<String>()

        override suspend fun listByWorkspace(workspaceId: String): List<HashtagSavedSet> {
            requestedWorkspaces += workspaceId
            return sets.filter { it.workspaceId == workspaceId }
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, setId: String): HashtagSavedSet? =
            sets.firstOrNull { it.workspaceId == workspaceId && it.id == setId }

        override suspend fun create(set: HashtagSavedSet): HashtagSavedSet = set

        override suspend fun delete(workspaceId: String, setId: String): Boolean = false
    }
}
