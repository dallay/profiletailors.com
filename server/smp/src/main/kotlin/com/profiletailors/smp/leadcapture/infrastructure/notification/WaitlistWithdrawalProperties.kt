package com.profiletailors.smp.leadcapture.infrastructure.notification

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.waitlist.withdrawal")
data class WaitlistWithdrawalProperties(
    val encryptionKey: String = "",
    val publicUrlBase: String = "https://profiletailors.com/waitlist/withdraw",
)
