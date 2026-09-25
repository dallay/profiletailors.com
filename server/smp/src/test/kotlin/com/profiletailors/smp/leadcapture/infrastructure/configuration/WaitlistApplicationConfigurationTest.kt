package com.profiletailors.smp.leadcapture.infrastructure.configuration

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.JoinResult
import com.profiletailors.leadcapture.waitlist.application.WaitlistWithdrawalTokenIssuer
import com.profiletailors.leadcapture.waitlist.application.WaitlistWithdrawalUrlProvider
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistConsentRecorder
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryJoinedNotification
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryJoinedNotifier
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.Waitlist
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WaitlistApplicationConfigurationTest {

    private val configuration = WaitlistApplicationConfiguration()

    @Test
    fun `waitlistEntryIdGenerator produces a fresh UUID for each join attempt`() {
        val generator = configuration.waitlistEntryIdGenerator()

        val waitlistId = WaitlistId("waitlist-1")
        val email = NormalizedEmail.fromPersisted("user@example.com")
        val firstId = generator.generate(waitlistId, email).value
        val secondId = generator.generate(waitlistId, email).value

        assertNotEquals(firstId, secondId)
        assertTrue(firstId.length == 36)
    }

    @Test
    fun `waitlistEntryIdGenerator distinguishes different emails for the same waitlist`() {
        val generator = configuration.waitlistEntryIdGenerator()

        val waitlistId = WaitlistId("waitlist-1")
        val userA = generator.generate(waitlistId, NormalizedEmail.fromPersisted("a@example.com")).value
        val userB = generator.generate(waitlistId, NormalizedEmail.fromPersisted("b@example.com")).value

        assertNotEquals(userA, userB)
    }

    @Test
    fun `waitlistEntryIdGenerator distinguishes different waitlists for the same email`() {
        val generator = configuration.waitlistEntryIdGenerator()

        val email = NormalizedEmail.fromPersisted("user@example.com")
        val waitlistA = generator.generate(WaitlistId("waitlist-A"), email).value
        val waitlistB = generator.generate(WaitlistId("waitlist-B"), email).value

        assertNotEquals(waitlistA, waitlistB)
    }

    @Test
    fun `joinWaitlistHandler notifies once with the saved entry payload`() = runTest {
        val notifier = RecordingWaitlistEntryJoinedNotifier()
        val handler = configuration.joinWaitlistHandler(
            waitlistRepository = StubWaitlistRepository,
            entryRepository = RecordingWaitlistEntryRepository(),
            idGenerator = { _, _ -> WaitlistEntryId("test-id") },
            transactionRunner = AtomicTransactionRunner.noop,
            withdrawalTokenIssuer = { now ->
                WaitlistWithdrawalTokenIssuer.IssuedToken(
                    raw = "raw-token",
                    persisted = WaitlistEntryRepository.WithdrawalToken(
                        candidate = "candidate",
                        hash = "hash",
                        expiresAt = now.plusSeconds(90),
                    ),
                )
            },
            withdrawalUrlProvider = WaitlistWithdrawalUrlProvider.noop,
            consentRecorder = WaitlistConsentRecorder.noop,
            notifier = notifier,
        )

        val result = handler.handle(joinCommand())

        assertEquals(JoinResult.JOINED_NEW, result)
        assertEquals(
            listOf(
                WaitlistEntryJoinedNotification(
                    waitlistEntryId = WaitlistEntryId("test-id"),
                    waitlistKey = WaitlistKey("profile-tailors-launch"),
                    waitlistName = "Profile Tailors Launch",
                    normalizedEmail = NormalizedEmail.fromPersisted("user@example.com"),
                    locale = null,
                ),
            ),
            notifier.notifications,
        )
    }

    @Test
    fun `joinWaitlistHandler does not notify when the entry already exists`() = runTest {
        val notifier = RecordingWaitlistEntryJoinedNotifier()
        val handler = configuration.joinWaitlistHandler(
            waitlistRepository = StubWaitlistRepository,
            entryRepository = RecordingWaitlistEntryRepository(alreadyExists = true),
            idGenerator = { _, _ -> WaitlistEntryId("test-id") },
            transactionRunner = AtomicTransactionRunner.noop,
            withdrawalTokenIssuer = { now ->
                WaitlistWithdrawalTokenIssuer.IssuedToken(
                    raw = "raw-token",
                    persisted = WaitlistEntryRepository.WithdrawalToken(
                        candidate = "candidate",
                        hash = "hash",
                        expiresAt = now.plusSeconds(90),
                    ),
                )
            },
            withdrawalUrlProvider = WaitlistWithdrawalUrlProvider.noop,
            consentRecorder = WaitlistConsentRecorder.noop,
            notifier = notifier,
        )

        val result = handler.handle(joinCommand())

        assertEquals(JoinResult.ALREADY_JOINED, result)
        assertTrue(notifier.notifications.isEmpty())
    }

    private fun joinCommand() = com.profiletailors.leadcapture.waitlist.application.JoinWaitlistCommand(
        waitlistKey = WaitlistKey("profile-tailors-launch"),
        email = EmailAddress("user@example.com"),
        source = com.profiletailors.leadcapture.common.CaptureSource("marketing-site"),
        formId = null,
        locale = null,
        metadata = com.profiletailors.leadcapture.common.LeadMetadata(),
        consent = com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent(
            earlyAccess = true,
            version = "2026-07-17",
        ),
    )

    private object StubWaitlistRepository : WaitlistRepository {
        override fun findByKey(key: WaitlistKey): Waitlist? = Waitlist(
            id = WaitlistId("waitlist-1"),
            key = key,
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
            createdAt = Instant.parse("2026-07-17T09:00:00Z"),
            updatedAt = Instant.parse("2026-07-17T09:00:00Z"),
        )
    }

    private class RecordingWaitlistEntryRepository(private val alreadyExists: Boolean = false) :
        WaitlistEntryRepository {
        override fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry? = null

        override fun save(entry: WaitlistEntry): WaitlistEntry = entry

        override suspend fun withdrawByToken(candidate: String, hash: String, now: Instant): WaitlistEntry? = null

        override suspend fun saveIfNotExists(
            entry: WaitlistEntry,
            withdrawalToken: WaitlistEntryRepository.WithdrawalToken,
        ): WaitlistEntryRepository.SaveResult = if (alreadyExists) {
            WaitlistEntryRepository.SaveResult.AlreadyExists(entry)
        } else {
            WaitlistEntryRepository.SaveResult.Saved(entry)
        }
    }

    private class RecordingWaitlistEntryJoinedNotifier : WaitlistEntryJoinedNotifier {
        val notifications = mutableListOf<WaitlistEntryJoinedNotification>()

        override suspend fun notify(notification: WaitlistEntryJoinedNotification) {
            notifications += notification
        }
    }
}
