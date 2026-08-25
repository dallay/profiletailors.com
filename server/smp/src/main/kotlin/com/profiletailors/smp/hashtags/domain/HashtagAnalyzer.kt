package com.profiletailors.smp.hashtags.domain

fun interface HashtagAnalyzer {
    suspend fun analyze(content: String): HashtagAnalysis
}
