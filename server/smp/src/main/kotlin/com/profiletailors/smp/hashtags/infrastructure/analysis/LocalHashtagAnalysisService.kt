package com.profiletailors.smp.hashtags.infrastructure.analysis

import com.profiletailors.smp.hashtags.domain.HashtagAnalysis
import com.profiletailors.smp.hashtags.domain.HashtagAnalysisPort
import com.profiletailors.smp.hashtags.domain.HashtagPopularity
import com.profiletailors.smp.hashtags.domain.HashtagSuggestion
import org.springframework.stereotype.Service

/**
 * Topic-to-hashtag mapping for local, dependency-free analysis.
 * Replace or extend this adapter when an external AI service is integrated.
 */
@Service
internal class LocalHashtagAnalysisService : HashtagAnalysisPort {

    override suspend fun analyze(content: String): HashtagAnalysis {
        val topics = extractTopics(content)
        val suggestions = buildSuggestions(topics, content)
        return HashtagAnalysis(
            content = content,
            detectedTopics = topics,
            suggestedHashtags = suggestions.take(MAX_SUGGESTIONS),
            maxRecommended = MAX_RECOMMENDED,
        )
    }

    private fun extractTopics(content: String): List<String> {
        val lower = content.lowercase()
        return TOPIC_KEYWORDS
            .filter { (_, keywords) -> keywords.any { lower.contains(it) } }
            .map { (topic, _) -> topic }
            .ifEmpty { listOf(TOPIC_PROFESSIONAL) }
    }

    private fun buildSuggestions(topics: List<String>, content: String): List<HashtagSuggestion> {
        val topicSuggestions = topics.flatMap { TOPIC_HASHTAGS[it] ?: emptyList() }
        val contentHashtags = extractInlineHashtags(content)
        val combined = (topicSuggestions + contentHashtags).distinctBy { it.hashtag }

        return combined
            .sortedWith(
                compareByDescending<HashtagSuggestion> { it.popularity.ordinal.unaryMinus() }
                    .thenByDescending { it.relevanceScore },
            )
    }

    private fun extractInlineHashtags(content: String): List<HashtagSuggestion> = HASHTAG_REGEX.findAll(content)
        .map { it.value.lowercase() }
        .distinct()
        .map { tag ->
            HashtagSuggestion(
                hashtag = tag,
                relevanceScore = 0.9f,
                popularity = HashtagPopularity.MEDIUM,
                category = "content",
                usageCount = 0,
            )
        }
        .toList()

    companion object {
        private const val MAX_SUGGESTIONS = 15
        private const val MAX_RECOMMENDED = 10
        private val HASHTAG_REGEX = Regex("""#\w+""")

        private const val TOPIC_TECHNOLOGY = "technology"
        private const val TOPIC_STARTUPS = "startups"
        private const val TOPIC_LEADERSHIP = "leadership"
        private const val TOPIC_MARKETING = "marketing"
        private const val TOPIC_INNOVATION = "innovation"
        private const val TOPIC_CAREER = "career"
        private const val TOPIC_FINANCE = "finance"
        private const val TOPIC_SUSTAINABILITY = "sustainability"
        private const val TOPIC_PROFESSIONAL = "professional"

        private val TOPIC_KEYWORDS: Map<String, List<String>> = mapOf(
            TOPIC_TECHNOLOGY to listOf(
                "tech",
                "software",
                "code",
                "developer",
                "programming",
                "data",
                "cloud",
                "ai",
                "machine learning",
            ),
            TOPIC_STARTUPS to listOf(
                "startup",
                "founder",
                "venture",
                "fundrais",
                "seed",
                "series a",
                "entrepreneurship",
            ),
            TOPIC_LEADERSHIP to listOf("leader", "management", "ceo", "cto", "executive", "team", "hire", "culture"),
            TOPIC_MARKETING to listOf("marketing", "brand", "content", "campaign", "seo", "growth", "audience"),
            TOPIC_INNOVATION to listOf("innovat", "disrupt", "transform", "future", "next", "breakthrough"),
            TOPIC_CAREER to listOf("career", "job", "hiring", "opportunity", "resume", "interview", "promotion"),
            TOPIC_FINANCE to listOf("invest", "finance", "revenue", "profit", "funding", "stock", "valuation"),
            TOPIC_SUSTAINABILITY to listOf("sustainab", "green", "climate", "esg", "carbon", "environment"),
        )

        private val TOPIC_HASHTAGS: Map<String, List<HashtagSuggestion>> = mapOf(
            TOPIC_TECHNOLOGY to listOf(
                HashtagSuggestion("#technology", 0.95f, HashtagPopularity.TRENDING, TOPIC_TECHNOLOGY, 5_200_000),
                HashtagSuggestion("#tech", 0.93f, HashtagPopularity.TRENDING, TOPIC_TECHNOLOGY, 4_800_000),
                HashtagSuggestion("#softwaredevelopment", 0.85f, HashtagPopularity.HIGH, TOPIC_TECHNOLOGY, 2_100_000),
                HashtagSuggestion(
                    "#artificialintelligence",
                    0.88f,
                    HashtagPopularity.TRENDING,
                    TOPIC_TECHNOLOGY,
                    3_500_000,
                ),
                HashtagSuggestion("#cloudcomputing", 0.80f, HashtagPopularity.HIGH, TOPIC_TECHNOLOGY, 1_900_000),
            ),
            TOPIC_STARTUPS to listOf(
                HashtagSuggestion("#startups", 0.95f, HashtagPopularity.TRENDING, TOPIC_STARTUPS, 4_100_000),
                HashtagSuggestion("#entrepreneurship", 0.90f, HashtagPopularity.HIGH, TOPIC_STARTUPS, 3_200_000),
                HashtagSuggestion("#founder", 0.85f, HashtagPopularity.HIGH, TOPIC_STARTUPS, 1_800_000),
                HashtagSuggestion("#venturecapital", 0.75f, HashtagPopularity.MEDIUM, TOPIC_STARTUPS, 980_000),
            ),
            TOPIC_LEADERSHIP to listOf(
                HashtagSuggestion("#leadership", 0.95f, HashtagPopularity.TRENDING, TOPIC_LEADERSHIP, 6_700_000),
                HashtagSuggestion("#management", 0.88f, HashtagPopularity.HIGH, TOPIC_LEADERSHIP, 3_100_000),
                HashtagSuggestion("#teamwork", 0.82f, HashtagPopularity.HIGH, TOPIC_LEADERSHIP, 2_400_000),
                HashtagSuggestion("#culture", 0.78f, HashtagPopularity.MEDIUM, TOPIC_LEADERSHIP, 1_600_000),
            ),
            TOPIC_MARKETING to listOf(
                HashtagSuggestion("#marketing", 0.95f, HashtagPopularity.TRENDING, TOPIC_MARKETING, 7_200_000),
                HashtagSuggestion("#digitalmarketing", 0.90f, HashtagPopularity.HIGH, TOPIC_MARKETING, 4_500_000),
                HashtagSuggestion("#contentmarketing", 0.85f, HashtagPopularity.HIGH, TOPIC_MARKETING, 2_800_000),
                HashtagSuggestion("#growthhacking", 0.75f, HashtagPopularity.MEDIUM, TOPIC_MARKETING, 1_100_000),
            ),
            TOPIC_INNOVATION to listOf(
                HashtagSuggestion("#innovation", 0.95f, HashtagPopularity.TRENDING, TOPIC_INNOVATION, 5_900_000),
                HashtagSuggestion("#futureofwork", 0.85f, HashtagPopularity.HIGH, TOPIC_INNOVATION, 2_200_000),
                HashtagSuggestion("#disruption", 0.78f, HashtagPopularity.MEDIUM, TOPIC_INNOVATION, 1_300_000),
            ),
            TOPIC_CAREER to listOf(
                HashtagSuggestion("#career", 0.90f, HashtagPopularity.HIGH, TOPIC_CAREER, 4_000_000),
                HashtagSuggestion("#hiring", 0.88f, HashtagPopularity.TRENDING, TOPIC_CAREER, 3_800_000),
                HashtagSuggestion("#jobsearch", 0.82f, HashtagPopularity.HIGH, TOPIC_CAREER, 2_600_000),
                HashtagSuggestion("#professionaldevelopment", 0.80f, HashtagPopularity.HIGH, TOPIC_CAREER, 2_100_000),
            ),
            TOPIC_FINANCE to listOf(
                HashtagSuggestion("#finance", 0.90f, HashtagPopularity.HIGH, TOPIC_FINANCE, 3_400_000),
                HashtagSuggestion("#investing", 0.88f, HashtagPopularity.HIGH, TOPIC_FINANCE, 3_100_000),
                HashtagSuggestion("#fintech", 0.85f, HashtagPopularity.TRENDING, TOPIC_FINANCE, 2_700_000),
            ),
            TOPIC_SUSTAINABILITY to listOf(
                HashtagSuggestion(
                    "#sustainability",
                    0.92f,
                    HashtagPopularity.TRENDING,
                    TOPIC_SUSTAINABILITY,
                    4_600_000,
                ),
                HashtagSuggestion("#esg", 0.85f, HashtagPopularity.HIGH, TOPIC_SUSTAINABILITY, 1_800_000),
                HashtagSuggestion("#climateaction", 0.80f, HashtagPopularity.HIGH, TOPIC_SUSTAINABILITY, 1_500_000),
            ),
            TOPIC_PROFESSIONAL to listOf(
                HashtagSuggestion("#linkedin", 0.70f, HashtagPopularity.TRENDING, TOPIC_PROFESSIONAL, 8_100_000),
                HashtagSuggestion("#networking", 0.72f, HashtagPopularity.HIGH, TOPIC_PROFESSIONAL, 3_900_000),
                HashtagSuggestion("#business", 0.75f, HashtagPopularity.HIGH, TOPIC_PROFESSIONAL, 7_500_000),
            ),
        )
    }
}
