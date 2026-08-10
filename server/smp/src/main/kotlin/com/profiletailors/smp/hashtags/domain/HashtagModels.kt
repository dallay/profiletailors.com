package com.profiletailors.smp.hashtags.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.time.Instant

const val LINKEDIN_HASHTAG_LIMIT = 30

@ValueObject
enum class HashtagPopularity { TRENDING, HIGH, MEDIUM, LOW }

data class HashtagSuggestion(
    val hashtag: String,
    val relevanceScore: Float,
    val popularity: HashtagPopularity,
    val category: String,
    val usageCount: Int,
)

data class HashtagAnalysis(
    val content: String,
    val detectedTopics: List<String>,
    val suggestedHashtags: List<HashtagSuggestion>,
    val maxRecommended: Int = 10,
)

@AggregateRoot
data class HashtagSavedSet(
    val id: String,
    val workspaceId: String,
    val name: String,
    val hashtags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
