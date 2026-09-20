package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.identity.application.RegistrationModeChange
import com.profiletailors.smp.identity.domain.RegistrationMode
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyCodec
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyRecord
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyService
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.handler.RegistrationModeHandlers
import com.profiletailors.smp.platformadmin.domain.InvalidRegistrationModeException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class AdminConfigurationControllerTest {

    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val operatorAccessResolver = mockk<OperatorAccessResolver>()
    private val registrationModeHandlers = mockk<RegistrationModeHandlers>()
    private val configurationIdempotencyService =
        ConfigurationIdempotencyService(FakeConfigurationIdempotencyStore(), FakeConfigurationIdempotencyCodec())

    @Test
    fun `getRegistrationMode returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/configuration/registration-mode")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `getRegistrationMode returns 200 with the current mode when authorized`() {
        grantRoles(listOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OPERATOR))
        coEvery { registrationModeHandlers.currentMode(any()) } returns RegistrationMode.OPEN

        webClient()
            .get()
            .uri("/api/admin/configuration/registration-mode")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.mode").isEqualTo("OPEN")
    }

    @Test
    fun `getRegistrationMode returns 403 without disclosing a value when permission is missing`() {
        grantRoles(listOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.SUPPORT_AGENT))
        coEvery { registrationModeHandlers.currentMode(any()) } throws
            PlatformAccessDeniedException(PlatformPermission.CONFIGURATION_READ)

        webClient()
            .get()
            .uri("/api/admin/configuration/registration-mode")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
            .jsonPath("$.mode").doesNotExist()
    }

    @Test
    fun `changeRegistrationMode requires an idempotency key`() {
        grantRoles(listOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/configuration/registration-mode")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("mode" to "CLOSED"))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `changeRegistrationMode returns 401 without principal context`() {
        webClient(principal = null)
            .post()
            .uri("/api/admin/configuration/registration-mode")
            .header("Idempotency-Key", "change-key")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("mode" to "CLOSED"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `changeRegistrationMode returns 403 when operator lacks manage permission`() {
        grantRoles(listOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OPERATOR))
        coEvery { registrationModeHandlers.changeMode(any()) } throws
            PlatformAccessDeniedException(PlatformPermission.CONFIGURATION_MANAGE)

        webClient()
            .post()
            .uri("/api/admin/configuration/registration-mode")
            .header("Idempotency-Key", "change-key")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("mode" to "CLOSED"))
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `changeRegistrationMode returns 400 for an invalid mode value`() {
        grantRoles(listOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OWNER))
        coEvery { registrationModeHandlers.changeMode(any()) } throws InvalidRegistrationModeException("BOGUS")

        webClient()
            .post()
            .uri("/api/admin/configuration/registration-mode")
            .header("Idempotency-Key", "change-key")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("mode" to "BOGUS"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `changeRegistrationMode returns 200 and the new mode on success`() {
        grantRoles(listOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OWNER))
        coEvery { registrationModeHandlers.changeMode(any()) } returns
            RegistrationModeChange(RegistrationMode.OPEN, RegistrationMode.CLOSED)

        webClient()
            .post()
            .uri("/api/admin/configuration/registration-mode")
            .header("Idempotency-Key", "change-key")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("mode" to "CLOSED"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.mode").isEqualTo("CLOSED")
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminConfigurationController(
                registrationModeHandlers = registrationModeHandlers,
                operatorAccessResolver = operatorAccessResolver,
                requestContextStore = FakeRequestContextStore(principal),
                configurationIdempotencyService = configurationIdempotencyService,
            ),
        )
        .controllerAdvice(AdminProblemDetailsHandler())
        .build()

    private fun grantRoles(roles: List<com.profiletailors.smp.platformadmin.domain.PlatformRole>) {
        coEvery { operatorAccessResolver.resolve(any()) } returns OperatorAccess(operatorId, roles.toSet())
    }

    private fun operatorPrincipal() = PrincipalContext(
        principalId = operatorId.toString(),
        principalType = PrincipalType.USER,
        subject = "operator@example.com",
        provider = "jwt",
    )

    private class FakeConfigurationIdempotencyStore : ConfigurationIdempotencyStore {
        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): ConfigurationIdempotencyRecord? =
            null

        override suspend fun claim(record: ConfigurationIdempotencyRecord): Boolean = true

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) = Unit

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) = Unit
    }

    private class FakeConfigurationIdempotencyCodec : ConfigurationIdempotencyCodec {
        override fun encode(value: Any): String = value.toString()

        override fun <T : Any> decode(responseJson: String, responseType: Class<T>): T = error("not used")
    }

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
