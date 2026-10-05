package com.profiletailors.smp.shortlinks.application

interface ShortLinksConfigProperties {
    val publicHost: String

    val shortUrlBase: String
        get() = "https://$publicHost"
}
