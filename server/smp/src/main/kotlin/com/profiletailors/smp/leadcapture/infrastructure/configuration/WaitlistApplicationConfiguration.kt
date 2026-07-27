package com.profiletailors.smp.leadcapture.infrastructure.configuration

import com.profiletailors.leadcapture.waitlist.application.JoinWaitlistHandler
import com.profiletailors.leadcapture.waitlist.application.WaitlistEntryIdGenerator
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistConsentRecorder
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryJoinedNotifier
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@Configuration
class WaitlistApplicationConfiguration {

    @Bean
    fun waitlistEntryIdGenerator(): WaitlistEntryIdGenerator = WaitlistEntryIdGenerator { waitlistId, normalizedEmail ->
        WaitlistEntryId(
            UUID.nameUUIDFromBytes(
                "${waitlistId.value}|${normalizedEmail.value}".toByteArray(StandardCharsets.UTF_8),
            ).toString(),
        )
    }

    /**
     * Creates the handler for joining a waitlist.
     *
     * @param waitlistRepository Repository for waitlist data.
     * @param entryRepository Repository for waitlist entry data.
     * @param idGenerator Generator for waitlist entry identifiers.
     * @param consentRecorder Records waitlist consent.
     * @param notifier Notifies subscribers when an entry joins a waitlist.
     * @return A configured waitlist join handler.
     */
    @Bean
    fun joinWaitlistHandler(
        waitlistRepository: WaitlistRepository,
        entryRepository: WaitlistEntryRepository,
        idGenerator: WaitlistEntryIdGenerator,
        consentRecorder: WaitlistConsentRecorder,
        notifier: WaitlistEntryJoinedNotifier,
    ): JoinWaitlistHandler = JoinWaitlistHandler(
        waitlistRepository = waitlistRepository,
        entryRepository = entryRepository,
        idGenerator = idGenerator,
        consentRecorder = consentRecorder,
        notifier = notifier,
        clock = { Instant.now() },
    )
}
