package com.profiletailors.smp.hashtags.application

import com.profiletailors.smp.hashtags.domain.HashtagSavedSet
import com.profiletailors.smp.hashtags.domain.HashtagSuggestion

internal fun HashtagSuggestion.toResult() = HashtagSuggestionResult(
    hashtag = hashtag,
    relevanceScore = relevanceScore,
    popularity = popularity.name.lowercase(),
    category = category,
    usageCount = usageCount,
)

internal fun HashtagSavedSet.toResult() = HashtagSavedSetResult(
    id = id,
    workspaceId = workspaceId,
    name = name,
    hashtags = hashtags,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
