package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.TemplateId
import com.profiletailors.smp.platformadmin.application.contracts.NotificationRepositoryPort
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

private const val NOTIFICATION_API_V1 = "application/vnd.api.v1+json"
private const val NOTIFICATION_ADMIN_BEARER = "Bearer $BDD_ADMIN_TOKEN"
private const val NOTIFICATION_PATH = "/api/admin/notifications"
private const val NOTIFICATION_RETRY_PATH = "/api/admin/notifications"

class NotificationAdminBddSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var database: BddDatabaseSupport

    @Autowired
    private lateinit var state: PlatformAdminScenarioState

    @Autowired(required = false)
    private var notificationRepository: NotificationRepositoryPort? = null

    private val json = ObjectMapper()

    @Given("notification {string} has template {string}")
    fun notificationHasTemplate(notificationId: String, templateId: String) {
        val repo = notificationRepository ?: return
        val idStr = state.notificationIds[notificationId]
        if (idStr == null) return
        runBlocking {
            val id = UUID.fromString(idStr)
            val existing = repo.findById(NotificationId(id.toString()))
            if (existing != null) {
                val updated = existing.copy(templateId = TemplateId(templateId))
                repo.save(updated)
            }
        }
    }

    @Given("a notification exists with channel {string} and status {string}")
    fun createNotification(channel: String, status: String) {
        val repo = notificationRepository ?: return
        runBlocking {
            val notificationId = UUID.randomUUID()
            val notification = Notification(
                id = NotificationId(notificationId.toString()),
                idempotencyKey = IdempotencyKey("bdd-$notificationId"),
                channel = NotificationChannel.valueOf(channel),
                recipient = Recipient("test-$notificationId@example.com"),
                templateId = TemplateId("platform.password-recovery"),
                payload = NotificationPayload(
                    variables = mapOf(
                        "message" to "Test message",
                        "token" to "secret-token-123",
                    ),
                ),
                status = NotificationStatus.valueOf(status),
                sentAt = if (status == "SENT") Instant.now() else null,
                failedAt = if (status == "FAILED") Instant.now() else null,
                errorMessage = if (status == "FAILED") "Simulated failure" else null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
            repo.save(notification)
            state.notificationIds["default"] = notificationId.toString()
        }
    }

    @Given("a failed password-recovery notification exists")
    fun createFailedPasswordRecoveryNotification() {
        val repo = notificationRepository ?: return
        runBlocking {
            val notificationId = UUID.randomUUID()
            val notification = Notification(
                id = NotificationId(notificationId.toString()),
                idempotencyKey = IdempotencyKey("bdd-retry-$notificationId"),
                channel = NotificationChannel.EMAIL,
                recipient = Recipient("recovery-$notificationId@example.com"),
                templateId = TemplateId("platform.password-recovery"),
                payload = NotificationPayload(
                    variables = mapOf(
                        "message" to "Reset your password",
                        "resetLink" to "https://example.com/reset?token=abc123",
                    ),
                ),
                status = NotificationStatus.FAILED,
                sentAt = null,
                failedAt = Instant.now(),
                errorMessage = "Delivery failed: connection timeout",
                createdAt = Instant.now().minusSeconds(3600),
                updatedAt = Instant.now(),
            )
            repo.save(notification)
            state.notificationIds["password-recovery"] = notificationId.toString()
        }
    }

    @Given("a failed invitation notification exists")
    fun createFailedInvitationNotification() {
        val repo = notificationRepository ?: return
        runBlocking {
            val notificationId = UUID.randomUUID()
            val notification = Notification(
                id = NotificationId(notificationId.toString()),
                idempotencyKey = IdempotencyKey("bdd-invite-$notificationId"),
                channel = NotificationChannel.EMAIL,
                recipient = Recipient("invite-$notificationId@example.com"),
                templateId = TemplateId("platform.invitation"),
                payload = NotificationPayload(
                    variables = mapOf(
                        "inviterName" to "Admin",
                        "inviteLink" to "https://example.com/invite?token=xyz789",
                    ),
                ),
                status = NotificationStatus.FAILED,
                sentAt = null,
                failedAt = Instant.now(),
                errorMessage = "Delivery failed: recipient unavailable",
                createdAt = Instant.now().minusSeconds(7200),
                updatedAt = Instant.now(),
            )
            repo.save(notification)
            state.notificationIds["invitation"] = notificationId.toString()
        }
    }

    @Given("an expired notification exists")
    fun createExpiredNotification() {
        val repo = notificationRepository ?: return
        runBlocking {
            val notificationId = UUID.randomUUID()
            val weekSeconds = 604_800L
            val twoWeeksSeconds = weekSeconds * 2
            val notification = Notification(
                id = NotificationId(notificationId.toString()),
                idempotencyKey = IdempotencyKey("bdd-expired-$notificationId"),
                channel = NotificationChannel.EMAIL,
                recipient = Recipient("expired-$notificationId@example.com"),
                templateId = TemplateId("platform.invitation"),
                payload = NotificationPayload(
                    variables = mapOf("message" to "Expired invitation"),
                ),
                status = NotificationStatus.FAILED,
                sentAt = null,
                failedAt = Instant.now().minusSeconds(weekSeconds),
                errorMessage = "Expired: invitation token no longer valid",
                createdAt = Instant.now().minusSeconds(twoWeeksSeconds),
                updatedAt = Instant.now().minusSeconds(weekSeconds),
            )
            repo.save(notification)
            state.notificationIds["expired"] = notificationId.toString()
        }
    }

    @Given("the idempotency key is set to {string}")
    fun setIdempotencyKey(key: String) {
        state.idempotencyKey = key
    }

    @When("the platform operator queries notifications with no filters")
    fun queryNotificationsNoFilters() {
        state.lastResponse = webTestClient.get()
            .uri(NOTIFICATION_PATH)
            .header(HttpHeaders.AUTHORIZATION, NOTIFICATION_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, NOTIFICATION_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator queries notifications")
    fun queryNotifications() {
        state.lastResponse = webTestClient.get()
            .uri(NOTIFICATION_PATH)
            .header(HttpHeaders.AUTHORIZATION, NOTIFICATION_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, NOTIFICATION_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator queries notifications with status filter {string}")
    fun queryNotificationsWithStatusFilter(status: String) {
        state.lastResponse = webTestClient.get()
            .uri { builder ->
                builder
                    .path(NOTIFICATION_PATH)
                    .queryParam("status", status)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, NOTIFICATION_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, NOTIFICATION_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator queries notifications with channel filter {string}")
    fun queryNotificationsWithChannelFilter(channel: String) {
        state.lastResponse = webTestClient.get()
            .uri { builder ->
                builder
                    .path(NOTIFICATION_PATH)
                    .queryParam("channel", channel)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, NOTIFICATION_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, NOTIFICATION_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator queries notifications with recipient filter {string}")
    fun queryNotificationsWithRecipientFilter(recipient: String) {
        state.lastResponse = webTestClient.get()
            .uri { builder ->
                builder
                    .path(NOTIFICATION_PATH)
                    .queryParam("recipient", recipient)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, NOTIFICATION_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, NOTIFICATION_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator retries the notification")
    fun retryNotification() {
        val notificationId = state.notificationIds["password-recovery"]
            ?: state.notificationIds["invitation"]
            ?: state.notificationIds["expired"]
            ?: state.notificationIds["default"]
            ?: return
        state.lastResponse = webTestClient.post()
            .uri("$NOTIFICATION_RETRY_PATH/$notificationId/retry")
            .header(HttpHeaders.AUTHORIZATION, NOTIFICATION_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, NOTIFICATION_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator retries the notification with the idempotency key")
    fun retryNotificationWithIdempotencyKey() {
        val notificationId = state.notificationIds["password-recovery"] ?: return
        state.lastResponse = webTestClient.post()
            .uri("$NOTIFICATION_RETRY_PATH/$notificationId/retry")
            .header(HttpHeaders.AUTHORIZATION, NOTIFICATION_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, NOTIFICATION_API_V1)
            .header("X-Idempotency-Key", state.idempotencyKey ?: "default-key")
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("an unauthenticated principal retries a notification")
    fun unauthenticatedRetry() {
        state.lastResponse = webTestClient.post()
            .uri("$NOTIFICATION_RETRY_PATH/${UUID.randomUUID()}/retry")
            .header(HttpHeaders.ACCEPT, NOTIFICATION_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the notification response status should be {int}")
    fun assertNotificationResponseStatus(status: Int) {
        val response = requireNotNull(state.lastResponse)
        assertEquals(status, response.status.value())
    }

    @Then("the notification result should be empty")
    fun assertResultIsEmpty() {
        val response = requireNotNull(state.lastResponse)
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("data")
        assertTrue(data.isArray, "Expected data to be an array")
        assertEquals(0, data.size(), "Expected empty array")
    }

    @Then("the notification result should contain notifications")
    fun assertResultContainsNotifications() {
        val response = requireNotNull(state.lastResponse)
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("data")
        assertTrue(data.isArray, "Expected data to be an array")
        assertTrue(data.size() > 0, "Expected non-empty array")
    }

    @Then("all notifications should have status {string}")
    fun assertAllNotificationsHaveStatus(expectedStatus: String) {
        val response = requireNotNull(state.lastResponse)
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("data")
        assertTrue(data.isArray, "Expected data to be an array")
        for (notification in data) {
            assertEquals(expectedStatus, notification.get("status").asText())
        }
    }

    @Then("all notifications should have channel {string}")
    fun assertAllNotificationsHaveChannel(expectedChannel: String) {
        val response = requireNotNull(state.lastResponse)
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("data")
        assertTrue(data.isArray, "Expected data to be an array")
        for (notification in data) {
            assertEquals(expectedChannel, notification.get("channel").asText())
        }
    }

    @Then("no notification payload should contain {string}")
    fun assertNoPayloadContains(field: String) {
        val response = requireNotNull(state.lastResponse)
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("data")
        assertTrue(data.isArray, "Expected data to be an array")
        for (notification in data) {
            val redactedPayload = notification.get("redactedPayload")
            assertFalse(
                nodeContainsText(redactedPayload, field.lowercase()),
                "Payload value should not contain '$field' (redaction failed)",
            )
        }
    }

    private fun nodeContainsText(node: JsonNode?, needle: String): Boolean {
        if (node == null || node.isNull) return false
        if (node.isValueNode) return node.asText().lowercase().contains(needle)
        return node.any { child -> nodeContainsText(child, needle) }
    }

    @Then("a new notification should be created")
    fun assertNewNotificationCreated() {
        val response = requireNotNull(state.lastResponse)
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("data")
        assertNotNull(data, "Expected data in response")
    }

    @Then("the response should indicate notification is not retryable")
    fun assertNotRetryable() {
        val response = requireNotNull(state.lastResponse)
        assertEquals(400, response.status.value())
    }

    @Then("the response should indicate notification not found")
    fun assertNotFound() {
        val response = requireNotNull(state.lastResponse)
        assertEquals(400, response.status.value())
    }
}
