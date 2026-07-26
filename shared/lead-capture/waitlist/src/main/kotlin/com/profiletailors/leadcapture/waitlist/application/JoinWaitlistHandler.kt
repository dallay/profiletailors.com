package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistConsentRecordRequest
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistConsentRecorder
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryJoinedNotification
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryJoinedNotifier
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
    private val consentRecorder: WaitlistConsentRecorder = WaitlistConsentRecorder.noop,
    private val notifier: WaitlistEntryJoinedNotifier = WaitlistEntryJoinedNotifier.noop,
    private val clock: () -> Instant = Instant::now,
) {

    /**
     * Adds a new entry to the requested waitlist when the waitlist accepts entries.
     *
     * @param command The command containing the waitlist and entry details.
     * @return The join outcome: `JOINED_NEW` for a newly saved entry or `ALREADY_JOINED` when the email is already registered.
     * @throws WaitlistNotFoundException If the requested waitlist does not exist.
     * @throws WaitlistClosedException If the requested waitlist does not accept entries.
     */
    fun handle(command: JoinWaitlistCommand): JoinResult {
        val waitlist = waitlistRepository.findByKey(command.waitlistKey)
            ?: throw WaitlistNotFoundException(command.waitlistKey)
        if (!waitlist.status.acceptsEntries()) {
            throw WaitlistClosedException(command.waitlistKey, waitlist.status)
        }

        val normalized = command.normalizedEmail()

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
        return when (val result = entryRepository.saveIfNotExists(entry)) {
            is WaitlistEntryRepository.SaveResult.Saved -> {
                consentRecorder.record(
                    WaitlistConsentRecordRequest(
                        waitlistKey = command.waitlistKey,
                        entryId = result.entry.id,
                        normalizedEmail = result.entry.normalizedEmail,
                        consent = result.entry.consent,
                        locale = result.entry.locale,
                        source = result.entry.source,
                    ),
                )
                notifier.notify(
                    WaitlistEntryJoinedNotification(
                        waitlistEntryId = result.entry.id,
                        waitlistKey = command.waitlistKey,
                        waitlistName = waitlist.name,
                        normalizedEmail = result.entry.normalizedEmail,
                        locale = result.entry.locale,
                    ),
                )
                JoinResult.JOINED_NEW
            }
            is WaitlistEntryRepository.SaveResult.AlreadyExists -> {
                JoinResult.ALREADY_JOINED
            }
        }
    }
}
