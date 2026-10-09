package com.profiletailors.smp.shortlinks.domain

interface LinkClickRepository {
    suspend fun record(linkId: LinkId)
}
