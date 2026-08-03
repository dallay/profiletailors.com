package com.profiletailors.smp.hashtags.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.hashtags.domain.HashtagAnalysisPort
import com.profiletailors.smp.hashtags.domain.HashtagPopularity
import com.profiletailors.smp.hashtags.domain.HashtagSavedSetRepository
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext

@Service
internal class AnalyzeHashtagsHandler(private val analysisPort: HashtagAnalysisPort) :
    QueryHandler<AnalyzeHashtagsQuery, HashtagAnalysisResult> {
    override suspend fun handle(query: AnalyzeHashtagsQuery): HashtagAnalysisResult {
        val analysis = analysisPort.analyze(query.content)
        return HashtagAnalysisResult(
            content = analysis.content,
            detectedTopics = analysis.detectedTopics,
            suggestedHashtags = analysis.suggestedHashtags.map { it.toResult() },
            maxRecommended = analysis.maxRecommended,
        )
    }
}

@Service
internal class GetTrendingHashtagsHandler(private val analysisPort: HashtagAnalysisPort) :
    QueryHandler<GetTrendingHashtagsQuery, TrendingHashtagsResult> {
    override suspend fun handle(query: GetTrendingHashtagsQuery): TrendingHashtagsResult {
        val trending = analysisPort.analyze("").suggestedHashtags
            .filter { it.popularity == HashtagPopularity.TRENDING }
        return TrendingHashtagsResult(trending.map { it.toResult() })
    }
}

@Service
internal class ListHashtagSavedSetsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val repository: HashtagSavedSetRepository,
) : QueryHandler<ListHashtagSavedSetsQuery, HashtagSavedSetsResult> {
    override suspend fun handle(query: ListHashtagSavedSetsQuery): HashtagSavedSetsResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val sets = repository.listByWorkspace(workspaceId)
        return HashtagSavedSetsResult(sets.map { it.toResult() })
    }
}
