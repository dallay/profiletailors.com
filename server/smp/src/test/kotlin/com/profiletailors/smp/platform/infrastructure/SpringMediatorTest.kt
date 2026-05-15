package com.profiletailors.smp.platform.infrastructure

import com.profiletailors.smp.platform.application.Command
import com.profiletailors.smp.platform.application.CommandHandler
import com.profiletailors.smp.platform.application.Mediator
import com.profiletailors.smp.platform.application.Query
import com.profiletailors.smp.platform.application.QueryHandler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext

class SpringMediatorTest {

    @Test
    fun `dispatches command to matching handler`() = runTest {
        val context = GenericApplicationContext().apply {
            beanFactory.registerSingleton("sampleCommandHandler", SampleCommandHandler())
            refresh()
        }
        val mediator: Mediator = SpringMediator(context)

        val result = mediator.dispatch(SampleCommand("bootstrap"))

        assertEquals("handled:bootstrap", result)
    }

    @Test
    fun `dispatches query to matching handler`() = runTest {
        val context = GenericApplicationContext().apply {
            beanFactory.registerSingleton("sampleQueryHandler", SampleQueryHandler())
            refresh()
        }
        val mediator: Mediator = SpringMediator(context)

        val result = mediator.dispatch(SampleQuery("workspace-1"))

        assertEquals("query:workspace-1", result)
    }

    @Test
    fun `fails when no handler matches request`() = runTest {
        val context = GenericApplicationContext().apply {
            refresh()
        }
        val mediator: Mediator = SpringMediator(context)

        val error = assertThrows(NoHandlerForRequestException::class.java) {
            kotlinx.coroutines.runBlocking {
                mediator.dispatch(SampleCommand("missing"))
            }
        }

        assertEquals(SampleCommand::class.java.name, error.requestType)
    }

    private data class SampleCommand(val value: String) : Command<String>

    private class SampleCommandHandler : CommandHandler<SampleCommand, String> {
        override suspend fun handle(command: SampleCommand): String = "handled:${command.value}"
    }

    private data class SampleQuery(val workspaceId: String) : Query<String>

    private class SampleQueryHandler : QueryHandler<SampleQuery, String> {
        override suspend fun handle(query: SampleQuery): String = "query:${query.workspaceId}"
    }
}
