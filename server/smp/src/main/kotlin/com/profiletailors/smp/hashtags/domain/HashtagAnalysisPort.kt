package com.profiletailors.smp.hashtags.domain

fun interface HashtagAnalysisPort {
    suspend fun analyze(content: String): HashtagAnalysis
}
