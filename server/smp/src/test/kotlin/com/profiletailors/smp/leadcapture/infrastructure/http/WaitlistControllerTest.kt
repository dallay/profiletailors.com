package com.profiletailors.smp.leadcapture.infrastructure.http

import com.profiletailors.controllers.GlobalExceptionHandler
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.JoinWaitlistHandler
import com.profiletailors.leadcapture.waitlist.application.WaitlistEntryIdGenerator
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.Waitlist
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistStatus
import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class WaitlistControllerTest {

    @Test
    fun `join returns accepted public response for new email`() {
        webClient()
            .post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest(email = "new@example.com"))
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.status").isEqualTo("accepted")
            .jsonPath("$.message").isEqualTo("You're on the waitlist")
            .jsonPath("$.duplicate").doesNotExist()
    }

    @Test
    fun `join returns same accepted public response for duplicate email`() {
        webClient(existingEmails = setOf("duplicate@example.com"))
            .post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest(email = "duplicate@example.com"))
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.status").isEqualTo("accepted")
            .jsonPath("$.message").isEqualTo("You're on the waitlist")
            .jsonPath("$.duplicate").doesNotExist()
    }

    @Test
    fun `join returns 400 for invalid email`() {
        webClient()
            .post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest(email = "not-an-email"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("invalid_email")
    }

    @Test
    fun `join returns 400 when early access consent is missing`() {
        webClient()
            .post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "email": "user@example.com",
                  "source": "marketing-site",
                  "formId": "waitlist-hero",
                  "locale": "en",
                  "consent": { "marketing": false },
                  "metadata": { "utm_source": "newsletter" }
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("consent_required")
    }

    @Test
    fun `join returns 400 when early access consent is false`() {
        webClient()
            .post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest(email = "user@example.com", earlyAccess = false))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("consent_required")
    }

    @Test
    fun `join returns 404 for unknown waitlist key`() {
        webClient(knownWaitlistKey = "profile-tailors-launch")
            .post()
            .uri("/api/waitlists/unknown-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest(email = "user@example.com"))
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("waitlist_not_found")
    }

    @Test
    fun `join returns 409 for paused waitlist`() {
        webClient(waitlistStatus = WaitlistStatus.PAUSED)
            .post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest(email = "user@example.com"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("waitlist_closed")
    }

    @Test
    fun `join returns 409 for closed waitlist`() {
        webClient(waitlistStatus = WaitlistStatus.CLOSED)
            .post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest(email = "user@example.com"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("waitlist_closed")
    }

    @Test
    fun `join returns 500 when handler fails unexpectedly`() {
        webClient(entryRepository = FailingWaitlistEntryRepository)
            .post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest(email = "user@example.com"))
            .exchange()
            .expectStatus().is5xxServerError
    }

    private fun webClient(
        knownWaitlistKey: String = "profile-tailors-launch",
        waitlistStatus: WaitlistStatus = WaitlistStatus.ACTIVE,
        existingEmails: Set<String> = emptySet(),
        entryRepository: WaitlistEntryRepository = InMemoryWaitlistEntryRepository(existingEmails),
    ): WebTestClient {
        val handler = JoinWaitlistHandler(
            waitlistRepository = InMemoryWaitlistRepository(knownWaitlistKey, waitlistStatus),
            entryRepository = entryRepository,
            idGenerator = WaitlistEntryIdGenerator { _, normalizedEmail ->
                WaitlistEntryId("entry-${normalizedEmail.value.hashCode().toUInt()}")
            },
            clock = { Instant.parse("2026-07-17T10:00:00Z") },
        )
        return WebTestClient.bindToController(WaitlistController(handler))
            .controllerAdvice(GlobalExceptionHandler(StaticMessageSource()))
            .build()
    }

    private fun validJoinRequest(email: String, earlyAccess: Boolean = true): String =
        """
        {
          "email": "$email",
          "source": "marketing-site",
          "formId": "waitlist-hero",
          "locale": "en",
          "consent": {
            "earlyAccess": $earlyAccess,
            "marketing": false,
            "version": "2026-07-17"
          },
          "metadata": { "utm_source": "newsletter" }
        }
        """.trimIndent()

    private class InMemoryWaitlistRepository(private val knownKey: String, private val status: WaitlistStatus) :
        WaitlistRepository {
        override fun findByKey(key: WaitlistKey): Waitlist? = if (key.value == knownKey) {
            Waitlist(
                id = WaitlistId("waitlist-1"),
                key = key,
                name = "Profile Tailors Launch",
                context = "profile-tailors",
                status = status,
                createdAt = Instant.parse("2026-07-17T09:00:00Z"),
                updatedAt = Instant.parse("2026-07-17T09:00:00Z"),
            )
        } else {
            null
        }
    }

    private class InMemoryWaitlistEntryRepository(existingEmails: Set<String>) : WaitlistEntryRepository {
        private val normalizedEmails = existingEmails.toMutableSet()

        override fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry? = null

        override fun save(entry: WaitlistEntry): WaitlistEntry = entry

        override fun saveIfNotExists(entry: WaitlistEntry): WaitlistEntryRepository.SaveResult =
            if (normalizedEmails.add(entry.normalizedEmail.value)) {
                WaitlistEntryRepository.SaveResult.Saved(entry)
            } else {
                WaitlistEntryRepository.SaveResult.AlreadyExists(entry)
            }
    }

    private object FailingWaitlistEntryRepository : WaitlistEntryRepository {
        override fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry? = null

        override fun save(entry: WaitlistEntry): WaitlistEntry = entry

        override fun saveIfNotExists(entry: WaitlistEntry): WaitlistEntryRepository.SaveResult =
            throw IllegalStateException("database unavailable")
    }
}
