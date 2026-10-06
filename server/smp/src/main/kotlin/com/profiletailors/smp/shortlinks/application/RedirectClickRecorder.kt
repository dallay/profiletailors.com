package com.profiletailors.smp.shortlinks.application

import com.profiletailors.smp.shortlinks.domain.LinkId

interface RedirectClickRecorder {
    suspend fun record(linkId: LinkId)
}
