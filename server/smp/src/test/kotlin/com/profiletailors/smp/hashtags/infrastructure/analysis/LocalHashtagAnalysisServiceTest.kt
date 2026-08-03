package com.profiletailors.smp.hashtags.infrastructure.analysis

import com.profiletailors.smp.hashtags.domain.HashtagPopularity
import kotlinx.coroutines.test.runTest
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
}
