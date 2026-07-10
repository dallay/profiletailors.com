package com.profiletailors.common.domain.bus

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandHandler
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.notification.NotificationHandler
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MediatorImplTest {

    private class PingQuery : Query<String>
    private class PingQueryHandler : QueryHandler<PingQuery, String> {
        override suspend fun handle(query: PingQuery): String = "pong"
    }

    private class HelloCommand : Command
    private class HelloCommandHandler : CommandHandler<HelloCommand> {
        var handled = false
        override suspend fun handle(command: HelloCommand) {
            handled = true
        }
    }

    private class AddCommand(val a: Int, val b: Int) : CommandWithResult<Int>
    private class AddCommandHandler : CommandWithResultHandler<AddCommand, Int> {
        override suspend fun handle(command: AddCommand): Int = command.a + command.b
    }

    class UserCreatedNotification : Notification

    class UserCreatedHandler1 : NotificationHandler<UserCreatedNotification> {
        var callCount = 0
        override suspend fun handle(notification: UserCreatedNotification) {
            callCount++
        }
    }

    class UserCreatedHandler2 : NotificationHandler<UserCreatedNotification> {
        var callCount = 0
        override suspend fun handle(notification: UserCreatedNotification) {
            callCount++
        }
    }

    @Test
    fun `should send query and return response`() = runTest {
        val handler = PingQueryHandler()
        val mediator = createMediator(handler)

        val result = mediator.send(PingQuery())

        result shouldBe "pong"
    }

    @Test
    fun `should send command`() = runTest {
        val handler = HelloCommandHandler()
        val mediator = createMediator(handler)

        mediator.send(HelloCommand())

        handler.handled shouldBe true
    }

    @Test
    fun `should send command with result`() = runTest {
        val handler = AddCommandHandler()
        val mediator = createMediator(handler)

        val result = mediator.send(AddCommand(2, 3))

        result shouldBe 5
    }

    @Test
    fun `should publish notification to all handlers`() = runTest {
        val handler1 = UserCreatedHandler1()
        val handler2 = UserCreatedHandler2()

        val mediator = createMediator(handler1, handler2)

        mediator.publish(UserCreatedNotification())

        handler1.callCount shouldBe 1
        handler2.callCount shouldBe 1
    }

    @Test
    fun `should throw when no handler registered`() = runTest {
        val mediator = createMediator()

        shouldThrow<HandlerNotFoundException> {
            mediator.send(PingQuery())
        }
    }

    private fun createMediator(vararg handlers: Any): Mediator {
        val map = HashMap<Class<*>, Any>()
        handlers.forEach { handler ->
            map[handler.javaClass] = handler
        }
        val dependencyProvider = ManualDependencyProvider(map)
        val registry = RegistryImpl(dependencyProvider)
        return MediatorImpl(registry)
    }
}
