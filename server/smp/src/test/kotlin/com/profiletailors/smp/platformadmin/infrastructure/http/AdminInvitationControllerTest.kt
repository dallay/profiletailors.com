package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.command.CreateInvitationCommand
import com.profiletailors.smp.platformadmin.application.command.ResendInvitationCommand
import com.profiletailors.smp.platformadmin.application.command.RevokeInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdminInvitationQuery
import com.profiletailors.smp.platformadmin.application.handler.CreateInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.ResendInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.ResendWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.result.CreateInvitationResult
import com.profiletailors.smp.platformadmin.application.result.ResendInvitationResult
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminInvitationControllerTest {

    private val clock = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val invitationId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val entryId = "entry-abc-123"

    private val invitationQuery = mockk<AdminInvitationQuery>()
    private val createInvitationHandler = mockk<CreateInvitationHandler>(relaxed = true)
    private val revokeInvitationHandler = mockk<RevokeInvitationHandler>(relaxed = true)
    private val resendInvitationHandler = mockk<ResendInvitationHandler>(relaxed = true)
    private val resendWaitlistHandler = mockk<ResendWaitlistInvitationHandler>(relaxed = true)
    private val revokeWaitlistHandler = mockk<RevokeWaitlistInvitationHandler>(relaxed = true)
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
        coEvery { resendWaitlistHandler.handle(any()) } returns invitationSummary()

        webClient()
            .post()
            .uri("/api/admin/invitations/$invitationId/resend")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(invitationId.toString())
            .jsonPath("$.status").isEqualTo("ACTIVE")

        coVerify { resendWaitlistHandler.handle(match { it.invitationId == invitationId }) }
    }

    @Test
    fun `create direct returns 401 without principal context`() = runTest {
        webClient(principal = null)
            .post()
            .uri("/api/admin/invitations/direct")
            .contentType(MediaType.valueOf("application/vnd.api.v1+json"))
            .bodyValue("""{"email":"user@example.com","target":"EXISTING_WORKSPACE","workspaceId":"ws-1"}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `create direct returns 201 and delegates request`() = runTest {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { createInvitationHandler.handle(any()) } returns
            CreateInvitationResult(invitationId, "ACTIVE", "2026-08-06T10:00:00Z", 0)

        webClient()
            .post()
            .uri("/api/admin/invitations/direct")
            .contentType(MediaType.valueOf("application/vnd.api.v1+json"))
            .bodyValue("""{"email":"user@example.com","target":"EXISTING_WORKSPACE","workspaceId":"ws-1"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.invitationId").isEqualTo(invitationId.toString())
            .jsonPath("$.version").isEqualTo(0)

        coVerify {
            createInvitationHandler.handle(
                match<CreateInvitationCommand> {
                    it.email == "user@example.com" && it.workspaceId == "ws-1"
                },
            )
        }
    }

    @Test
    fun `revoke direct returns 401 without principal context`() = runTest {
        webClient(principal = null)
            .post()
            .uri("/api/admin/invitations/$invitationId/direct-revoke")
            .contentType(MediaType.valueOf("application/vnd.api.v1+json"))
            .bodyValue("""{"expectedVersion":0}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `revoke direct returns 200 and delegates version`() = runTest {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/invitations/$invitationId/direct-revoke")
            .contentType(MediaType.valueOf("application/vnd.api.v1+json"))
            .bodyValue("""{"expectedVersion":3}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.invitationId").isEqualTo(invitationId.toString())

        coVerify {
            revokeInvitationHandler.handle(match<RevokeInvitationCommand> { it.expectedVersion == 3L })
        }
    }

    @Test
    fun `resend direct returns 401 without principal context`() = runTest {
        webClient(principal = null)
            .post()
            .uri("/api/admin/invitations/$invitationId/direct-resend")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `resend direct returns 200 and delegates invitation`() = runTest {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { resendInvitationHandler.handle(any()) } returns
            ResendInvitationResult(invitationId, "ACTIVE", "2026-08-06T10:00:00Z", 1)

        webClient()
            .post()
            .uri("/api/admin/invitations/$invitationId/direct-resend")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.invitationId").isEqualTo(invitationId.toString())
            .jsonPath("$.version").isEqualTo(1)

        coVerify {
            resendInvitationHandler.handle(match<ResendInvitationCommand> { it.invitationId == invitationId })
        }
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

        coVerify { revokeWaitlistHandler.handle(match { it.invitationId == invitationId }) }
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminInvitationController(
                invitationQuery = invitationQuery,
                createInvitationHandler = createInvitationHandler,
                revokeInvitationHandler = revokeInvitationHandler,
                resendInvitationHandler = resendInvitationHandler,
                resendWaitlistHandler = resendWaitlistHandler,
                revokeWaitlistHandler = revokeWaitlistHandler,
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
