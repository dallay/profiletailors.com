package com.profiletailors.smp.platform.infrastructure

import com.profiletailors.smp.platform.application.CommandHandler
import com.profiletailors.smp.platform.application.Mediator
import com.profiletailors.smp.platform.application.QueryHandler
import com.profiletailors.smp.platform.application.Request
import org.springframework.context.ApplicationContext
import org.springframework.core.ResolvableType

class SpringMediator(
    private val applicationContext: ApplicationContext,
) : Mediator {

    @Suppress("UNCHECKED_CAST")
    override suspend fun <RESPONSE> dispatch(request: Request<RESPONSE>): RESPONSE {
        val handler = resolveHandler(request)
            ?: throw NoHandlerForRequestException(request::class.java.name)

        return when (handler) {
            is QueryHandler<*, *> -> (handler as QueryHandler<Request<RESPONSE>, RESPONSE>).handle(request)
            is CommandHandler<*, *> -> (handler as CommandHandler<Request<RESPONSE>, RESPONSE>).handle(request)
            else -> throw NoHandlerForRequestException(request::class.java.name)
        }
    }

    private fun resolveHandler(request: Request<*>): Any? =
        applicationContext.getBeanProvider(QueryHandler::class.java)
            .orderedStream()
            .filter { candidate -> supports(candidate, QueryHandler::class.java, request) }
            .findFirst()
            .orElse(null)
            ?: applicationContext.getBeanProvider(CommandHandler::class.java)
                .orderedStream()
                .filter { candidate -> supports(candidate, CommandHandler::class.java, request) }
                .findFirst()
                .orElse(null)

    private fun supports(candidate: Any, contractType: Class<*>, request: Request<*>): Boolean {
        val type = ResolvableType.forClass(candidate.javaClass).`as`(contractType)
        val requestType = type.getGeneric(0).resolve() ?: return false
        return requestType.isAssignableFrom(request::class.java)
    }
}

class NoHandlerForRequestException(
    val requestType: String,
) : IllegalStateException("No handler registered for request type $requestType")
