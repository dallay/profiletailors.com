package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.application.ConsentRecordNotFoundException
import com.profiletailors.smp.governance.application.GetConsentHistoryQuery
import com.profiletailors.smp.governance.application.GetWorkspaceConsentRecordsQuery
import com.profiletailors.smp.governance.application.RecordConsentOutcome
import com.profiletailors.smp.governance.application.RecordWorkspaceConsentCommand
import com.profiletailors.smp.governance.application.WithdrawWorkspaceConsentCommand
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentStatus
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class ConsentControllerWebTest {

    private val mediator = StubMediator()
    private val controller = ConsentController(mediator)
    private val client = WebTestClient
        .bindToController(controller)
        .controllerAdvice(GovernanceProblemDetailsHandler())
        .build()

    @Test
    fun `POST consent returns 201 when consent is created`() {
        mediator.recordOutcome = RecordConsentOutcome(
            created = true,
            record = ConsentRecord(
                id = ConsentRecordId("cs-new-001"),
                workspaceId = "ws-001",
                subjectReference = SubjectReference.user("user-123"),
                consentType = ConsentType.CONTRACT_ACCEPTANCE,
                purpose = "terms.v1",
                policyVersion = "2026-07-01",
                source = "registration",
                locale = "en",
                givenAt = Instant.parse("2026-07-19T12:00:00Z"),
                status = ConsentStatus.ACTIVE,
            ),
        )

        client.post().uri("/api/governance/consent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "subjectValue": "user-123",
                    "subjectKind": "USER",
                    "consentType": "CONTRACT_ACCEPTANCE",
                    "purpose": "terms.v1",
                    "policyVersion": "2026-07-01",
                    "source": "registration",
                    "locale": "en"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isEqualTo("cs-new-001")
            .jsonPath("$.status").isEqualTo("ACTIVE")
    }

    @Test
    fun `POST consent returns 200 when consent is already active (idempotent replay)`() {
        mediator.recordOutcome = RecordConsentOutcome(
            created = false,
            record = ConsentRecord(
                id = ConsentRecordId("cs-existing"),
                workspaceId = "ws-001",
                subjectReference = SubjectReference.user("user-123"),
                consentType = ConsentType.CONSENT,
                purpose = "marketing.emails",
                policyVersion = "2026-07-01",
                source = "settings",
                locale = "es",
                givenAt = Instant.parse("2026-07-15T10:00:00Z"),
                status = ConsentStatus.ACTIVE,
            ),
        )

        client.post().uri("/api/governance/consent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "subjectValue": "user-123",
                    "subjectKind": "USER",
                    "consentType": "CONSENT",
                    "purpose": "marketing.emails",
                    "policyVersion": "2026-07-01",
                    "source": "settings",
                    "locale": "es"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo("cs-existing")
    }

    @Test
    fun `POST consent returns 400 when subjectKind is invalid`() {
        client.post().uri("/api/governance/consent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "subjectValue": "user-123",
                    "subjectKind": "ALIEN",
                    "consentType": "CONSENT",
                    "purpose": "terms.v1",
                    "policyVersion": "2026-07-01",
                    "source": "registration",
                    "locale": "en"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST consent returns 400 when locale is invalid`() {
        client.post().uri("/api/governance/consent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "subjectValue": "user-123",
                    "subjectKind": "USER",
                    "consentType": "CONSENT",
                    "purpose": "terms.v1",
                    "policyVersion": "2026-07-01",
                    "source": "registration",
                    "locale": "zz-ZZ-bogus"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST consent returns 400 when request body is malformed`() {
        client.post().uri("/api/governance/consent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("not-json")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST withdraw returns 200 with withdrawn status`() {
        mediator.withdrawResult = ConsentRecord(
            id = ConsentRecordId("cs-001"),
            workspaceId = "ws-001",
            subjectReference = SubjectReference.user("user-123"),
            consentType = ConsentType.CONSENT,
            purpose = "marketing.emails",
            policyVersion = "2026-07-01",
            source = "settings",
            locale = "es",
            givenAt = Instant.parse("2026-07-15T10:00:00Z"),
            status = ConsentStatus.WITHDRAWN,
            withdrawnAt = Instant.parse("2026-07-19T14:00:00Z"),
            withdrawalReason = "user_request",
        )

        client.post().uri("/api/governance/consent/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "subjectValue": "user-123",
                    "subjectKind": "USER",
                    "purpose": "marketing.emails",
                    "policyVersion": "2026-07-01",
                    "reason": "user_request"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("WITHDRAWN")
            .jsonPath("$.withdrawnAt").exists()
            .jsonPath("$.withdrawalReason").isEqualTo("user_request")
    }

    @Test
    fun `POST withdraw returns 404 when no active consent exists`() {
        val msg = "Active consent record not found for workspaceId=ws-001, " +
            "purpose=marketing.emails, policyVersion=2026-07-01"
        mediator.withdrawError = ConsentRecordNotFoundException(msg)

        client.post().uri("/api/governance/consent/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "subjectValue": "user-123",
                    "subjectKind": "USER",
                    "purpose": "marketing.emails",
                    "policyVersion": "2026-07-01"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.title").isEqualTo("Consent record not found")
    }

    @Test
    fun `GET consent returns workspace consent records`() {
        mediator.workspaceRecords = listOf(
            ConsentRecord(
                id = ConsentRecordId("cs-001"),
                workspaceId = "ws-001",
                subjectReference = SubjectReference.user("user-123"),
                consentType = ConsentType.CONSENT,
                purpose = "marketing.emails",
                policyVersion = "2026-07-01",
                source = "settings",
                locale = "en",
                givenAt = Instant.parse("2026-07-15T10:00:00Z"),
                status = ConsentStatus.ACTIVE,
            ),
        )

        client.get().uri("/api/governance/consent")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo("cs-001")
            .jsonPath("$[0].status").isEqualTo("ACTIVE")
    }

    @Test
    fun `GET consent history returns lifecycle records`() {
        mediator.historyRecords = listOf(
            ConsentRecord(
                id = ConsentRecordId("cs-001"),
                workspaceId = "ws-001",
                subjectReference = SubjectReference.user("user-123"),
                consentType = ConsentType.CONSENT,
                purpose = "marketing.emails",
                policyVersion = "2026-07-01",
                source = "settings",
                locale = "en",
                givenAt = Instant.parse("2026-07-15T10:00:00Z"),
                status = ConsentStatus.WITHDRAWN,
                withdrawnAt = Instant.parse("2026-07-19T14:00:00Z"),
            ),
        )

        client.get().uri(
            "/api/governance/consent/history?subjectKind=USER&subjectValue=user-123&purpose=marketing.emails",
        )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo("cs-001")
            .jsonPath("$[0].status").isEqualTo("WITHDRAWN")
    }

    private class StubMediator : Mediator {
        var recordOutcome: RecordConsentOutcome? = null
        var withdrawResult: ConsentRecord? = null
        var withdrawError: Exception? = null
        var workspaceRecords: List<ConsentRecord>? = null
        var historyRecords: List<ConsentRecord>? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = when (query) {
            is GetWorkspaceConsentRecordsQuery -> workspaceRecords as TResponse
            is GetConsentHistoryQuery -> historyRecords as TResponse
            else -> error("unexpected query: $query")
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("not used")
        }

        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            @Suppress("UNCHECKED_CAST")
            return when (command) {
                is RecordWorkspaceConsentCommand -> {
                    recordOutcome as TResult
                }
                is WithdrawWorkspaceConsentCommand -> {
                    withdrawError?.let { throw it }
                    withdrawResult as TResult
                }
                else -> {
                    error("unexpected command: $command")
                }
            }
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("not used")
        }
        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("not used")
        }
    }
}
