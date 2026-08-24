package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.handler.ResendWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.ports.AdminInvitationQuery
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminInvitationControllerTest {

    private val clock = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val invitationId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val entryId = "entry-abc-123"

    private val invitationQuery = mockk<AdminInvitationQuery>()
    private val resendHandler = mockk<ResendWaitlistInvitationHandler>(relaxed = true)
    private val revokeHandler = mockk<RevokeWaitlistInvitationHandler>(relaxed = true)
    private val operatorAccessResolver = mockk<OperatorAccessResolver>()

    @Test
    fun `getInvitation returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/invitations/$invitationId")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `getInvitation returns 403 when operator lacks invitations read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/invitations/$invitationId")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `getInvitation returns summary for existing invitation`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { invitationQuery.findById(invitationId) } returns invitationSummary()

        webClient()
            .get()
            .uri("/api/admin/invitations/$invitationId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(invitationId.toString())
            .jsonPath("$.waitlistEntryId").isEqualTo(entryId)
            .jsonPath("$.status").isEqualTo("ACTIVE")
    }

    @Test
    fun `getInvitation returns 404 when invitation does not exist`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { invitationQuery.findById(invitationId) } returns null

        webClient()
            .get()
            .uri("/api/admin/invitations/$invitationId")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `resend returns 200 with resend handler result`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { resendHandler.handle(any()) } returns invitationSummary()

        webClient()
            .post()
            .uri("/api/admin/invitations/$invitationId/resend")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(invitationId.toString())
            .jsonPath("$.status").isEqualTo("ACTIVE")

        coVerify { resendHandler.handle(match { it.invitationId == invitationId }) }
    }

    @Test
    fun `revoke returns 200 with revoked status and delegates command`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/invitations/$invitationId/revoke")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("revoked")

        coVerify { revokeHandler.handle(match { it.invitationId == invitationId }) }
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminInvitationController(
                invitationQuery = invitationQuery,
                resendHandler = resendHandler,
                revokeHandler = revokeHandler,
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

    private fun invitationSummary() = AdminInvitationSummary(
        id = invitationId,
        waitlistEntryId = entryId,
        status = "ACTIVE",
        issuedAt = clock,
        expiresAt = clock.plusSeconds(604_800),
        acceptedAt = null,
        revokedAt = null,
        revokedBy = null,
        createdBy = operatorId,
        deliveryStatus = "PENDING",
        deliveryAttemptCount = 0,
        version = 0,
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
