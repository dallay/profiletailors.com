package com.profiletailors.smp.shortlinks.domain

fun interface LinkClickRepository {
    suspend fun record(linkId: LinkId)
}
