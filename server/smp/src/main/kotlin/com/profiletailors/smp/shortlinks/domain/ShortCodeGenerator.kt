package com.profiletailors.smp.shortlinks.domain

fun interface ShortCodeGenerator {
    fun generate(): ShortCode
}
