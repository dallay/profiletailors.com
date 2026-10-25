package com.profiletailors.smp.hashtags.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.time.Instant

const val LINKEDIN_HASHTAG_LIMIT = 30

@ValueObject
enum class HashtagPopularity { TRENDING, HIGH, MEDIUM, LOW }

@ValueObject
data class HashtagSuggestion(
    val hashtag: String,
    val relevanceScore: Float,
    val popularity: HashtagPopularity,
    val category: String,
    val usageCount: Int,
) {
    init {
        require(hashtag.isNotBlank()) { "Hashtag must not be blank" }
        require(category.isNotBlank()) { "Category must not be blank" }
        require(usageCount >= 0) { "Usage count must not be negative" }
    }
}

@ValueObject
data class HashtagAnalysis(
    val content: String,
    val detectedTopics: List<String>,
    val suggestedHashtags: List<HashtagSuggestion>,
    val maxRecommended: Int = 10,
) {
    init {
        require(maxRecommended > 0) { "maxRecommended must be positive" }
    }
}

@AggregateRoot
data class HashtagSavedSet(
    val id: String,
    val workspaceId: String,
    val name: String,
    val hashtags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
