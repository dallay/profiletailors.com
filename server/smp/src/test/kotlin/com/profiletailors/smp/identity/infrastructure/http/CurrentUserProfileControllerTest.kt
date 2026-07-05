package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.identity.application.CurrentUserProfile
import com.profiletailors.smp.identity.application.GetCurrentUserProfileQuery
import com.profiletailors.smp.identity.domain.EmailStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurrentUserProfileControllerTest {

    @Test
    fun `returns current user profile`() = runTest {
        val expected = CurrentUserProfile(
            principalId = "user-1",
            email = "yuniel@example.com",
            username = "yuniel",
            displayIdentity = "yuniel",
            emailStatus = EmailStatus.PENDING,
        )
        val controller = CurrentUserProfileController(CapturingMediator(expected))

        val response = controller.currentUser()

        assertEquals(expected, response.body)
        assertEquals(EmailStatus.PENDING, response.body?.emailStatus)
    }

    private class CapturingMediator(private val result: CurrentUserProfile) : Mediator {
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            @Suppress("UNCHECKED_CAST")
            if (query is GetCurrentUserProfileQuery) return result as TResponse
            error("Unexpected query: $query")
        }

        override suspend fun <TCommand : Command> send(command: TCommand) = Unit
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult =
            error("Not implemented")

        override suspend fun <T : Notification> publish(notification: T) = Unit
        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) = Unit
    }
}
