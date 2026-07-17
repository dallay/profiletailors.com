package com.profiletailors.smp.leadcapture.infrastructure.configuration

import com.profiletailors.leadcapture.waitlist.application.JoinWaitlistHandler
import com.profiletailors.leadcapture.waitlist.application.WaitlistEntryIdGenerator
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.util.UUID

@Configuration
class WaitlistApplicationConfiguration {

    @Bean
    fun waitlistEntryIdGenerator(): WaitlistEntryIdGenerator = WaitlistEntryIdGenerator { waitlistId, normalizedEmail ->
        WaitlistEntryId(
            UUID.nameUUIDFromBytes("${waitlistId.value}|${normalizedEmail.value}".toByteArray()).toString(),
        )
    }

    @Bean
    fun joinWaitlistHandler(
        waitlistRepository: WaitlistRepository,
        entryRepository: WaitlistEntryRepository,
        idGenerator: WaitlistEntryIdGenerator,
    ): JoinWaitlistHandler = JoinWaitlistHandler(
        waitlistRepository = waitlistRepository,
        entryRepository = entryRepository,
        idGenerator = idGenerator,
        clock = { Instant.now() },
    )
}
