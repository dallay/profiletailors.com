package com.profiletailors.smp.hashtags.domain

import java.time.Instant

const val LINKEDIN_HASHTAG_LIMIT = 30

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

data class HashtagSavedSet(
    val id: String,
    val workspaceId: String,
    val name: String,
    val hashtags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
