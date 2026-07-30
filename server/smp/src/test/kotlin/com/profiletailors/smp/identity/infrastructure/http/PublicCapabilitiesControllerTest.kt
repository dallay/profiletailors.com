package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.identity.application.GetPublicCapabilitiesQuery
import com.profiletailors.smp.identity.application.PublicCapabilities
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PublicCapabilitiesControllerTest {

    @Test
    fun `returns only disabled registration capability`() = runTest {
        val response = controller(registrationEnabled = false, passwordRecoveryEnabled = true).publicCapabilities()

        response shouldBe PublicCapabilitiesResponse(
            registrationEnabled = false,
            passwordRecoveryEnabled = true,
        )
    }

    @Test
    fun `returns only enabled registration capability`() = runTest {
        val response = controller(registrationEnabled = true, passwordRecoveryEnabled = true).publicCapabilities()

        response shouldBe PublicCapabilitiesResponse(
            registrationEnabled = true,
            passwordRecoveryEnabled = true,
        )
    }

    @Test
    fun `returns passwordRecoveryEnabled true when configured`() = runTest {
        val response = controller(registrationEnabled = true, passwordRecoveryEnabled = true).publicCapabilities()

        response.passwordRecoveryEnabled shouldBe true
    }

    @Test
    fun `returns passwordRecoveryEnabled false when disabled`() = runTest {
        val response = controller(registrationEnabled = true, passwordRecoveryEnabled = false).publicCapabilities()

        response.passwordRecoveryEnabled shouldBe false
    }

    private fun controller(registrationEnabled: Boolean, passwordRecoveryEnabled: Boolean) =
        PublicCapabilitiesController(
            mediator = FakeMediator(registrationEnabled, passwordRecoveryEnabled),
        )

    private class FakeMediator(
        private val registrationEnabled: Boolean,
        private val passwordRecoveryEnabled: Boolean,
    ) : Mediator {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = when (query) {
            is GetPublicCapabilitiesQuery -> PublicCapabilities(
                registrationEnabled = registrationEnabled,
                passwordRecoveryEnabled = passwordRecoveryEnabled,
            ) as TResponse
            else -> error("Unexpected query: $query")
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used in this test")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            error("Not used in this test")
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used in this test")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used in this test")
        }
    }
}
