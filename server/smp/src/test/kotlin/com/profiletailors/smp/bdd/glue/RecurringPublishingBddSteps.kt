package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class RecurringPublishingBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private val objectMapper: ObjectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

    @Before
    fun resetRecurringState() {
        RecurringPublishingState.reset()
    }

    @Given("a connected LinkedIn social account exists")
    fun givenConnectedLinkedInSocialAccountExists() = runBlocking {
        bddDatabaseSupport.seedWorkspace()
        bddDatabaseSupport.seedSocialConnection("social-conn-1", "LINKEDIN", "ACTIVE")
        bddDatabaseSupport.seedSocialAccount(
            accountId = "social-acc-1",
            connectionId = "social-conn-1",
            provider = "LINKEDIN",
            providerAccountId = "linkedin-profile-1",
            accountKind = "PERSONAL_PROFILE",
            displayName = "Yuniel Acosta",
        )
    }

    @Given("a scheduled publication exists")
    fun givenScheduledPublicationExists() = runBlocking {
        givenConnectedLinkedInSocialAccountExists()
        bddDatabaseSupport.seedScheduledPublication(
            publicationId = "pub-bdd-scheduled-1",
            socialAccountId = "social-acc-1",
            scheduledFor = Instant.now().plus(java.time.Duration.ofDays(7)),
            title = "Scheduled Post",
            bodyText = "Scheduled body",
        )
    }

    @When("the client creates a daily recurring schedule")
    fun whenClientCreatesDailyRecurringSchedule() {
        val templatePostId = "pub-bdd-scheduled-1"
        val now = Instant.now()
        val startsAt = now.plus(java.time.Duration.ofHours(1))
        val endDate = java.time.LocalDate.now().plusDays(3)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "templatePostId" to templatePostId,
                "frequency" to "daily",
                "interval" to 1,
                "startsAt" to startsAt.toString(),
                "endDate" to endDate.toString(),
                "timezone" to "UTC",
            ),
        )
        RecurringPublishingState.latestPublishingResponse = webTestClient.post()
            .uri(bddDatabaseSupport.recurringSchedulesPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult()
        RecurringPublishingState.currentRecurringScheduleId =
            RecurringPublishingState.parseField<String>("id")
    }

    @When("the client creates a weekly recurring schedule on Monday and Wednesday")
    fun whenClientCreatesWeeklyRecurringScheduleOnMondayAndWednesday() {
        val templatePostId = "pub-bdd-scheduled-1"
        val now = Instant.now()
        val startsAt = now.plus(java.time.Duration.ofHours(1))
        val endDate = java.time.LocalDate.now().plusDays(30)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "templatePostId" to templatePostId,
                "frequency" to "weekly",
                "interval" to 1,
                "daysOfWeek" to listOf(1, 3),
                "startsAt" to startsAt.toString(),
                "endDate" to endDate.toString(),
                "timezone" to "UTC",
            ),
        )
        RecurringPublishingState.latestPublishingResponse = webTestClient.post()
            .uri(bddDatabaseSupport.recurringSchedulesPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult()
        RecurringPublishingState.currentRecurringScheduleId =
            RecurringPublishingState.parseField<String>("id")
    }

    @When("the client creates a monthly recurring schedule on day 15")
    fun whenClientCreatesMonthlyRecurringScheduleOnDay15() {
        val templatePostId = "pub-bdd-scheduled-1"
        val now = Instant.now()
        val startsAt = now.plus(java.time.Duration.ofHours(1))
        val endDate = java.time.LocalDate.now().plusMonths(6)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "templatePostId" to templatePostId,
                "frequency" to "monthly",
                "interval" to 1,
                "dayOfMonth" to 15,
                "startsAt" to startsAt.toString(),
                "endDate" to endDate.toString(),
                "timezone" to "UTC",
            ),
        )
        RecurringPublishingState.latestPublishingResponse = webTestClient.post()
            .uri(bddDatabaseSupport.recurringSchedulesPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult()
        RecurringPublishingState.currentRecurringScheduleId =
            RecurringPublishingState.parseField<String>("id")
    }

    @When("the client deletes the recurring schedule")
    fun whenClientDeletesRecurringSchedule() {
        val scheduleId = RecurringPublishingState.currentRecurringScheduleId
            ?: error("No recurring schedule ID available")
        RecurringPublishingState.latestPublishingResponse = webTestClient.delete()
            .uri("${bddDatabaseSupport.recurringSchedulesPath()}/$scheduleId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client resumes the recurring schedule")
    fun whenClientResumesRecurringSchedule() {
        val scheduleId = RecurringPublishingState.currentRecurringScheduleId
            ?: error("No recurring schedule ID available")
        val body = objectMapper.writeValueAsString(mapOf("status" to "active"))
        RecurringPublishingState.latestPublishingResponse = webTestClient.patch()
            .uri("${bddDatabaseSupport.recurringSchedulesPath()}/$scheduleId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client pauses the recurring schedule")
    fun whenClientPausesRecurringSchedule() {
        val scheduleId = RecurringPublishingState.currentRecurringScheduleId
            ?: error("No recurring schedule ID available")
        val body = objectMapper.writeValueAsString(mapOf("status" to "paused"))
        RecurringPublishingState.latestPublishingResponse = webTestClient.patch()
            .uri("${bddDatabaseSupport.recurringSchedulesPath()}/$scheduleId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client lists recurring schedules")
    fun whenClientListsRecurringSchedules() {
        RecurringPublishingState.latestPublishingResponse = webTestClient.get()
            .uri(bddDatabaseSupport.recurringSchedulesPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the recurring response should contain a schedule id")
    fun thenRecurringResponseShouldContainScheduleId() {
        assertTrue(RecurringPublishingState.responseBodyText().contains("\"id\":"))
    }

    @Then("the recurring response status should be {string}")
    fun thenRecurringResponseStatusShouldBe(status: String) {
        val actualStatus = RecurringPublishingState.parseRecurringResponseStatus()
        assertEquals(status.uppercase(), actualStatus)
    }

    @Then("the recurring schedules list should contain {int} schedule")
    fun thenRecurringSchedulesListShouldContainCount(expected: Int) {
        val body = RecurringPublishingState.responseBodyText()
        val map: Map<String, Any?> = objectMapper.readValue(body)

        @Suppress("UNCHECKED_CAST")
        val schedules = map["schedules"] as? List<*>
        assertNotNull(schedules, "Field 'schedules' is null in response: $body")
        assertEquals(expected, schedules!!.size) {
            "Expected $expected schedules but got ${schedules.size}. Body: $body"
        }
    }

    @Then("at least {int} recurring publications should be scheduled")
    fun thenAtLeastRecurringPublicationsShouldBeScheduled(expected: Int) = runBlocking {
        assertTrue(bddDatabaseSupport.countScheduledPublications() >= expected)
    }
}
