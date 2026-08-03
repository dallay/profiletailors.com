package com.profiletailors.smp.hashtags.infrastructure.analysis

import com.profiletailors.smp.hashtags.domain.HashtagPopularity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalHashtagAnalysisServiceTest {
    private val service = LocalHashtagAnalysisService()

    @Test
    fun `empty content provides a trending hashtag for the trending endpoint`() = runTest {
        val analysis = service.analyze("")

        assertTrue(
            analysis.suggestedHashtags.any { it.popularity == HashtagPopularity.TRENDING },
            "Empty-content fallback must provide at least one trending hashtag",
        )
    }

    @Test
    fun `analysis extracts topics and inline hashtags without duplicates`() = runTest {
        val analysis = service.analyze("We build software with #Kotlin and #kotlin for teams.")

        assertTrue(analysis.detectedTopics.contains("technology"))
        assertEquals(analysis.suggestedHashtags.map { it.hashtag }.distinct().size, analysis.suggestedHashtags.size)
        assertTrue(analysis.suggestedHashtags.any { it.hashtag == "#kotlin" && it.category == "content" })
    }

    @Test
    fun `analysis falls back to professional topic for unrelated content`() = runTest {
        val analysis = service.analyze("A quiet note with no mapped business keywords.")

        assertEquals(listOf("professional"), analysis.detectedTopics)
    }

    @Test
    fun `analysis recognizes multiple topics and caps suggestions`() = runTest {
        val analysis = service.analyze(
            "Technology software startup founder leadership marketing innovation career finance sustainability " +
                "#custom",
        )

        assertTrue(analysis.detectedTopics.size > 1)
        assertTrue(analysis.suggestedHashtags.size <= 15)
        assertEquals(10, analysis.maxRecommended)
    }

    @Test
    fun `analysis extracts repeated inline hashtags case insensitively and preserves unique tags`() = runTest {
        val analysis = service.analyze("A post with #Launch, #launch and #Different tags")

        assertEquals(
            analysis.suggestedHashtags.map {
                it.hashtag
            }.distinct(),
            analysis.suggestedHashtags.map { it.hashtag },
        )
        assertTrue(analysis.suggestedHashtags.any { it.hashtag == "#launch" })
        assertTrue(analysis.suggestedHashtags.any { it.hashtag == "#different" })
    }
}
