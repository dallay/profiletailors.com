package com.profiletailors.smp.bdd.glue

import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.smp.leadcapture.infrastructure.governance.GovernanceWaitlistConsentRecorder
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class ConsentScopeBddSteps {
    @Autowired
    private lateinit var database: BddDatabaseSupport

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var client: WebTestClient

    private val firstId = UUID.fromString(BddDatabaseSupport.WORKSPACE_ID)
    private val secondId = UUID.fromString("00000000-0000-0000-0000-000000000192")
    private val firstKey = "consent-first-list"
    private val secondKey = "consent-second-list"
    private val email = "consent-scope@example.com"
    private val subjectHash = GovernanceWaitlistConsentRecorder.anonymousSubjectHash(
        NormalizedEmail.from(EmailAddress(email)),
    )

    @Before("@consent-scopes")
    fun resetConsentScopeScenario() = runBlocking { database.resetDatabase() }

    @Given("two public consent lists and an authorized tenant workspace exist")
    fun prepareConsentScopes() = runBlocking {
        database.seedAuthenticatedUserWithWorkspace()
        database.seedDirectGrant("ALLOW", "workspace:consent:read")
        database.seedDirectGrant("ALLOW", "workspace:consent:write")
        listOf(firstId to firstKey, secondId to secondKey).forEach { (id, key) ->
            databaseClient.sql(
                """
                INSERT INTO waitlists (id, key, name, context, status)
                VALUES (:id, :key, :key, 'profile-tailors', 'ACTIVE')
                """.trimIndent(),
            ).bind("id", id).bind("key", key).fetch().rowsUpdated().awaitSingle()
        }
    }

    @When("one person joins both public consent lists and repeats the first join")
    fun joinPublicLists() {
        listOf(firstKey, secondKey, firstKey).forEach { key ->
            client.post().uri("/api/waitlists/$key/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to email,
                        "source" to "marketing-site",
                        "locale" to "en",
                        "consent" to mapOf("earlyAccess" to true, "marketing" to false, "version" to "2026-10-26"),
                    ),
                ).exchange().expectStatus().isAccepted
        }
    }

    @When("the tenant queries and attempts to withdraw the public consent identity")
    fun queryAndWithdrawPublicIdentityAsTenant() {
        assertEquals(UUID.fromString(BddDatabaseSupport.WORKSPACE_ID), firstId)
        client.get().uri("/api/governance/consent?subjectKind=ANONYMOUS&purpose=waitlist.early_access")
            .headers(::tenantHeaders).exchange().expectStatus().isOk.expectBody().json("[]")
        client.get().uri { builder ->
            builder.path("/api/governance/consent/history")
                .queryParam("subjectKind", "ANONYMOUS")
                .queryParam("subjectValue", subjectHash)
                .queryParam("purpose", "waitlist.early_access")
                .build()
        }.headers(::tenantHeaders).exchange().expectStatus().isOk.expectBody().json("[]")
        client.post().uri("/api/governance/consent/withdraw").headers(::tenantHeaders)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "subjectKind" to "ANONYMOUS",
                    "subjectValue" to subjectHash,
                    "purpose" to "waitlist.early_access",
                    "policyVersion" to "2026-10-26",
                ),
            ).exchange().expectStatus().isNotFound
    }

    @Then("each public list has one active consent and one scoped event")
    fun verifyPublicScopeEvidence() = runBlocking {
        listOf(firstId, secondId).forEach { id ->
            listOf("consent_records", "consent_record_events").forEach { table ->
                val count = databaseClient.sql(
                    """
                    SELECT COUNT(*) FROM $table
                    WHERE waitlist_id = :id AND workspace_id IS NULL AND subject_value = :subject
                      AND purpose = 'waitlist.early_access' AND status = 'ACTIVE'
                    """.trimIndent(),
                ).bind("id", id).bind("subject", subjectHash)
                    .map { row, _ -> requireNotNull(row.get(0, Long::class.javaObjectType)) }.one().awaitSingle()
                assertEquals(1L, count)
            }
        }
    }

    private fun tenantHeaders(headers: HttpHeaders) {
        headers.set(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
        headers.set(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
        headers.set(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
    }
}
