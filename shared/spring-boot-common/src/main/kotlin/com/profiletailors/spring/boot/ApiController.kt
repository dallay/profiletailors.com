package com.profiletailors.spring.boot

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandHandlerExecutionError
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandlerExecutionError
import com.profiletailors.common.domain.bus.query.Response
import com.profiletailors.config.ContextKeys.WORKSPACE_CONTEXT_KEY
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.HtmlUtils
import reactor.core.publisher.Mono
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

/**
 * Abstract base class for API controllers.
 * Provides common functionality for handling commands, queries, and authentication.
 *
 * @property mediator The mediator used for sending commands and queries.
 */
@SecurityRequirement(name = "Keycloak")
abstract class ApiController(private val mediator: Mediator) {

    /**
     * Dispatches a command using the mediator.
     *
     * @param command The command to be dispatched.
     * @throws CommandHandlerExecutionError if an error occurs while handling the command.
     */
    @Throws(CommandHandlerExecutionError::class)
    protected suspend fun dispatch(command: Command) = mediator.send(command)

    /**
     * Dispatches a command with result using the mediator.
     *
     * @param [TResult] The type of the result returned by the command.
     * @param command The command to be dispatched.
     * @return The result from the command handler.
     * @throws CommandHandlerExecutionError if an error occurs while handling the command.
     */
    @Throws(CommandHandlerExecutionError::class)
    protected suspend fun <TResult> dispatch(command: CommandWithResult<TResult>): TResult = mediator.send(command)

    /**
     * Sends a query using the mediator and returns the response.
     *
     * @param TResponse The type of the response.
     * @param query The query to be sent.
     * @return The response from the query.
     * @throws QueryHandlerExecutionError if an error occurs while handling the query.
     */
    @Throws(QueryHandlerExecutionError::class)
    protected suspend fun <TResponse : Response> ask(query: Query<TResponse>): TResponse = mediator.send(query)

    /**
     * Retrieves the current authentication information.
     *
     * @return The current authentication, or null if not authenticated.
     */
    protected suspend fun authentication(): Authentication? {
        val securityContext = ReactiveSecurityContextHolder.getContext().awaitSingleOrNull()
        return securityContext?.authentication
    }

    /**
     * Retrieves the current user ID (from the JWT "sub" claim).
     * If the authentication is not a JwtAuthenticationToken, this method returns null,
     * as other token types like UsernamePasswordAuthenticationToken do not inherently provide
     * a JWT 'sub' claim.
     *
     * @return The current user ID (JWT "sub" claim), or null if not available.
     */
    protected suspend fun userId(): String? {
        val authentication = ReactiveSecurityContextHolder.getContext().awaitSingleOrNull()?.authentication

        return when (authentication) {
            is JwtAuthenticationToken -> authentication.token.subject
            is UsernamePasswordAuthenticationToken -> null
            else -> null
        }
    }

    /**
     * Retrieves the current user email from the JWT token attributes.
     * If the authentication is not a JwtAuthenticationToken or the email claim is missing,
     * this method returns null.
     *
     * @return The current user email from JWT token attributes, or null if not available.
     */
    protected suspend fun userEmail(): String? {
        val authentication = ReactiveSecurityContextHolder.getContext().awaitSingleOrNull()?.authentication

        return when (authentication) {
            is JwtAuthenticationToken -> authentication.tokenAttributes["email"] as? String
            is UsernamePasswordAuthenticationToken -> null
            else -> null
        }
    }

    protected suspend fun userIdFromToken(): UUID {
        val userId = try {
            UUID.fromString(
                userId() ?: throw ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing user ID in token",
                ),
            )
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user ID format in token", e)
        }
        return userId
    }

    /**
     * Retrieves the current workspace ID from the reactive context.
     *
     * The workspace ID is expected to be set by [com.profiletailors.config.WorkspaceContextWebFilter] from
     * the `X-Workspace-Id` HTTP header. This is the primary method for obtaining
     * the workspace context in controllers.
     *
     * @return The workspace UUID from the reactive context
     * @throws ResponseStatusException with 400 BAD REQUEST if workspace ID is not in context
     */
    protected suspend fun workspaceIdFromContext(): UUID = Mono.deferContextual { contextView ->
        if (contextView.hasKey(WORKSPACE_CONTEXT_KEY)) {
            Mono.just(contextView.get<UUID>(WORKSPACE_CONTEXT_KEY))
        } else {
            Mono.error(
                ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing X-Workspace-Id header. All workspace-scoped endpoints require this header.",
                ),
            )
        }
    }.awaitSingle()

    /**
     * Validates a path variable against an allow-list regex (^[a-zA-Z0-9_-]+$)
     * to prevent path traversal and other injection attacks.
     *
     * @param pathVariable The path variable to validate.
     * @return The validated path variable.
     * @throws IllegalArgumentException if the pathVariable contains invalid characters.
     */
    protected fun sanitizePathVariable(pathVariable: String): String {
        val regex = "^[a-zA-Z0-9_-]+$".toRegex()
        require(pathVariable.matches(regex)) {
            "Invalid path variable. Only alphanumeric characters, underscores, and hyphens are allowed."
        }
        return HtmlUtils.htmlEscape(URLEncoder.encode(pathVariable, "UTF-8"))
    }

    /**
     * Gets a localized message from the message source using the request's locale.
     *
     * @param key The message key to look up.
     * @param request The HTTP request to extract the locale from.
     * @param messageSource The message source to retrieve the message from.
     * @return The localized message string.
     */
    protected fun getLocalizedMessage(key: String, request: ServerHttpRequest, messageSource: MessageSource): String {
        val locale = request.headers.acceptLanguageAsLocales.firstOrNull() ?: Locale.ENGLISH
        return messageSource.getMessage(key, null, locale)
    }
}
