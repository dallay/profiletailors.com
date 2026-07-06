package com.profiletailors.common.domain.bus

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandHandler
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.notification.NotificationHandler
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `should send query and return response`() = runBlocking {
        val handler = PingQueryHandler()
        val mediator = createMediator(PingQuery::class.java to handler)

        val result = mediator.send(PingQuery())

        assertEquals("pong", result)
    }

    @Test
    fun `should send command`() = runBlocking {
        val handler = HelloCommandHandler()
        val mediator = createMediator(HelloCommand::class.java to handler)

        mediator.send(HelloCommand())

        assertTrue(handler.handled)
    }

    @Test
    fun `should send command with result`() = runBlocking {
        val handler = AddCommandHandler()
        val mediator = createMediator(AddCommand::class.java to handler)

        val result = mediator.send(AddCommand(2, 3))

        assertEquals(5, result)
    }

    @Test
    fun `should publish notification to all handlers`() = runBlocking {
        val handler1 = UserCreatedHandler1()
        val handler2 = UserCreatedHandler2()

        val map = HashMap<Class<*>, Any>()
        map[UserCreatedHandler1::class.java] = handler1
        map[UserCreatedHandler2::class.java] = handler2

        val dependencyProvider = ManualDependencyProvider(map)
        val registry = RegistryImpl(dependencyProvider)
        val mediator = MediatorImpl(registry)

        mediator.publish(UserCreatedNotification())

        assertEquals(1, handler1.callCount)
        assertEquals(1, handler2.callCount)
    }

    private fun createMediator(vararg handlers: Pair<Class<*>, Any>): Mediator {
        val map = HashMap<Class<*>, Any>()
        handlers.forEach { (clazz, handler) ->
            map[handler.javaClass] = handler
        }
        val dependencyProvider = ManualDependencyProvider(map)
        val registry = RegistryImpl(dependencyProvider)
        return MediatorImpl(registry)
    }
}
