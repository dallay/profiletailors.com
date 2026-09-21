package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.command.RetryNotificationCommand
import com.profiletailors.smp.platformadmin.application.contracts.NotificationAdminQuery
import com.profiletailors.smp.platformadmin.application.handler.RetryNotificationHandler
import com.profiletailors.smp.platformadmin.application.model.NotificationSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.domain.NotificationNotFoundForRetryException
import com.profiletailors.smp.platformadmin.domain.NotificationNotRetryableException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

internal class AdminNotificationControllerWebTestClientTest {

    private val operatorId = UUID.randomUUID()
    private val notificationId = UUID.randomUUID()

    private val notificationQuery = mockk<NotificationAdminQuery>()
    private val retryHandler = mockk<RetryNotificationHandler>()
    private val operatorAccessResolver = mockk<OperatorAccessResolver>()

    @Test
    fun `listNotifications returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/notifications")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `listNotifications returns 403 when operator lacks notifications read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/notifications")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `listNotifications returns 200 with paginated results`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        val summary = notificationSummary()
        coEvery { notificationQuery.list(any(), any(), any()) } returns PagedResult.of(
            items = listOf(summary),
            page = 0,
            size = 20,
            totalElements = 1,
        )

        webClient()
            .get()
            .uri("/api/admin/notifications")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data[0].id").isEqualTo(notificationId.toString())
            .jsonPath("$.data[0].channel").isEqualTo("EMAIL")
            .jsonPath("$.data[0].status").isEqualTo("FAILED")
            .jsonPath("$.meta.totalElements").isEqualTo(1)
    }

    @Test
    fun `listNotifications respects page and size parameters`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { notificationQuery.list(any(), 2, 10) } returns PagedResult.of(
            items = emptyList(),
            page = 2,
            size = 10,
            totalElements = 0,
        )

        webClient()
            .get()
            .uri("/api/admin/notifications?page=2&size=10")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk

        coVerify { notificationQuery.list(any(), 2, 10) }
    }

    @Test
    fun `listNotifications respects filter parameters`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        val summary = notificationSummary()
        coEvery { notificationQuery.list(any(), any(), any()) } returns PagedResult.of(
            items = listOf(summary),
            page = 0,
            size = 20,
            totalElements = 1,
        )

        webClient()
            .get()
            .uri("/api/admin/notifications?status=FAILED&channel=EMAIL&recipient=test@example.com")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk

        coVerify {
            notificationQuery.list(
                match { filters ->
                    filters.status == "FAILED" &&
                        filters.channel == "EMAIL" &&
                        filters.recipient == "test@example.com"
                },
                0,
                20,
            )
        }
    }

    @Test
    fun `listNotifications enforces max page size`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { notificationQuery.list(any(), any(), 100) } returns PagedResult.of(
            items = emptyList(),
            page = 0,
            size = 100,
            totalElements = 0,
        )

        webClient()
            .get()
            .uri("/api/admin/notifications?size=500")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk

        coVerify { notificationQuery.list(any(), 0, 100) }
    }

    @Test
    fun `getNotification returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/notifications/$notificationId")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `getNotification returns 403 when operator lacks notifications read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/notifications/$notificationId")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `getNotification returns 404 when notification does not exist`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { notificationQuery.findById(any()) } returns null

        webClient()
            .get()
            .uri("/api/admin/notifications/$notificationId")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `getNotification returns 200 with notification details`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        val summary = notificationSummary()
        coEvery { notificationQuery.findById(any()) } returns summary

        webClient()
            .get()
            .uri("/api/admin/notifications/$notificationId")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.id").isEqualTo(notificationId.toString())
            .jsonPath("$.data.channel").isEqualTo("EMAIL")
            .jsonPath("$.data.status").isEqualTo("FAILED")
            .jsonPath("$.data.redactedPayload").isMap
    }

    @Test
    fun `retryNotification returns 401 without principal context`() {
        webClient(principal = null)
            .post()
            .uri("/api/admin/notifications/$notificationId/retry")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `retryNotification returns 403 when operator lacks notifications manage permission`() {
        grantRoles(emptyList())

        webClient()
            .post()
            .uri("/api/admin/notifications/$notificationId/retry")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `retryNotification returns 400 when notification is not retryable`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { retryHandler.handle(any()) } throws NotificationNotRetryableException(
            id = notificationId.toString(),
            status = "FAILED",
            templateId = "platform.invitation",
        )

        webClient()
            .post()
            .uri("/api/admin/notifications/$notificationId/retry")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("NOTIFICATION_NOT_RETRYABLE")
    }

    @Test
    fun `retryNotification returns 404 when notification is not found`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { retryHandler.handle(any()) } throws NotificationNotFoundForRetryException(
            id = notificationId.toString(),
        )

        webClient()
            .post()
            .uri("/api/admin/notifications/$notificationId/retry")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.code").isEqualTo("NOTIFICATION_NOT_FOUND")
    }

    @Test
    fun `retryNotification returns 200 and delegates to handler`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        val summary = notificationSummary()
        coEvery { retryHandler.handle(any()) } returns summary

        webClient()
            .post()
            .uri("/api/admin/notifications/$notificationId/retry")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.id").isEqualTo(notificationId.toString())

        coVerify {
            retryHandler.handle(
                match<RetryNotificationCommand> { cmd ->
                    cmd.notificationId.value == notificationId.toString()
                },
            )
        }
    }

    @Test
    fun `retryNotification accepts idempotency key header`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        val summary = notificationSummary()
        val idempotencyKey = "test-idempotency-key"
        coEvery { retryHandler.handle(any()) } returns summary

        webClient()
            .post()
            .uri("/api/admin/notifications/$notificationId/retry")
            .header("Accept", "application/vnd.api.v1+json")
            .header("X-Idempotency-Key", idempotencyKey)
            .exchange()
            .expectStatus().isOk

        coVerify {
            retryHandler.handle(
                match<RetryNotificationCommand> { cmd ->
                    cmd.idempotencyKey == idempotencyKey
                },
            )
        }
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminNotificationController(
                notificationQuery = notificationQuery,
                retryHandler = retryHandler,
                operatorAccessResolver = operatorAccessResolver,
                requestContextStore = FakeRequestContextStore(principal),
            ),
        )
        .controllerAdvice(AdminProblemDetailsHandler())
        .build()

    private fun grantRoles(roles: List<PlatformRole>) {
        coEvery { operatorAccessResolver.resolve(any()) } returns OperatorAccess(operatorId, roles.toSet())
    }

    private fun operatorPrincipal() = PrincipalContext(
        principalId = operatorId.toString(),
        principalType = PrincipalType.USER,
        subject = "operator@example.com",
        provider = "jwt",
    )

    private fun notificationSummary() = NotificationSummary(
        id = notificationId.toString(),
        channel = "EMAIL",
        templateId = "platform.password-recovery",
        recipient = "test@example.com",
        status = "FAILED",
        errorMessage = "Delivery failed",
        createdAt = Instant.now(),
        sentAt = null,
        failedAt = Instant.now(),
        redactedPayload = mapOf("message" to "Redacted"),
    )

    private class FakeRequestContextStore(private val principal: PrincipalContext?) : RequestContextStore {
        override fun currentPrincipalContext(): PrincipalContext? = principal
        override fun setPrincipalContext(context: PrincipalContext?) = Unit
        override fun currentResourceContext(): ResourceContext? = null
        override fun setResourceContext(context: ResourceContext?) = Unit
        override fun currentRequestPath(): String? = null
        override fun setRequestPath(path: String?) = Unit
        override fun clear() = Unit
    }
}
