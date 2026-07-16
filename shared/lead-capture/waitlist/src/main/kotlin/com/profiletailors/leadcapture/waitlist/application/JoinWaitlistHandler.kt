package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.WaitlistClosedException
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistNotFoundException
import java.time.Instant

class JoinWaitlistHandler(
    private val waitlistRepository: WaitlistRepository,
    private val entryRepository: WaitlistEntryRepository,
    private val idGenerator: WaitlistEntryIdGenerator,
    private val clock: () -> Instant = Instant::now,
) {

    fun handle(command: JoinWaitlistCommand): JoinResult {
        val waitlist = waitlistRepository.findByKey(command.waitlistKey)
            ?: throw WaitlistNotFoundException(command.waitlistKey)
        if (!waitlist.status.acceptsEntries()) {
            throw WaitlistClosedException(command.waitlistKey, waitlist.status)
        }

        val normalized = command.normalizedEmail()
        val existing = entryRepository.findByNormalizedEmail(waitlist.id, normalized)
        if (existing != null) {
            return JoinResult.ALREADY_JOINED
        }

        val entry = WaitlistEntry(
            id = idGenerator.generate(waitlist.id, normalized),
            waitlistId = waitlist.id,
            email = command.email,
            normalizedEmail = normalized,
            source = command.source,
            formId = command.formId,
            locale = command.locale,
            metadata = command.metadata,
            consent = command.consent,
            joinedAt = clock(),
        )
        entryRepository.save(entry)
        return JoinResult.JOINED_NEW
    }
}
