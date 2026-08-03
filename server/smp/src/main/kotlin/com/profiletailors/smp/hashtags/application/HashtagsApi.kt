package com.profiletailors.smp.hashtags.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import java.time.Instant

data class AnalyzeHashtagsQuery(val content: String) : Query<HashtagAnalysisResult>

data object GetTrendingHashtagsQuery : Query<TrendingHashtagsResult>

data class ListHashtagSavedSetsQuery(val workspaceId: String) : Query<HashtagSavedSetsResult>

data class SaveHashtagSetCommand(val name: String, val hashtags: List<String>) :
    CommandWithResult<HashtagSavedSetResult>

data class DeleteHashtagSetCommand(val setId: String) : CommandWithResult<Unit>

data class HashtagSuggestionResult(
    val hashtag: String,
    val relevanceScore: Float,
    val popularity: String,
    val category: String,
    val usageCount: Int,
)

data class HashtagAnalysisResult(
    val content: String,
    val detectedTopics: List<String>,
    val suggestedHashtags: List<HashtagSuggestionResult>,
    val maxRecommended: Int,
)

data class TrendingHashtagsResult(val hashtags: List<HashtagSuggestionResult>)

data class HashtagSavedSetResult(
    val id: String,
    val workspaceId: String,
    val name: String,
    val hashtags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class HashtagSavedSetsResult(val sets: List<HashtagSavedSetResult>)
