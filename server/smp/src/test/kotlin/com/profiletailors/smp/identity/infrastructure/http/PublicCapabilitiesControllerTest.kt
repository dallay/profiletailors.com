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
        val response = controller(registrationEnabled = false).publicCapabilities()

        response shouldBe PublicCapabilitiesResponse(false)
    }

    @Test
    fun `returns only enabled registration capability`() = runTest {
        val response = controller(registrationEnabled = true).publicCapabilities()

        response shouldBe PublicCapabilitiesResponse(true)
    }

    private fun controller(registrationEnabled: Boolean) = PublicCapabilitiesController(
        mediator = FakeMediator(registrationEnabled),
    )

    private class FakeMediator(private val registrationEnabled: Boolean) : Mediator {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            return when (query) {
                is GetPublicCapabilitiesQuery -> PublicCapabilities(registrationEnabled = registrationEnabled) as TResponse
                else -> error("Unexpected query: $query")
            }
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
