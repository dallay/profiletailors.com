package com.profiletailors.smp.hashtags.domain

interface HashtagAnalysisPort {
    suspend fun analyze(content: String): HashtagAnalysis
}
