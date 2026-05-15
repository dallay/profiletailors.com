package com.profiletailors.smp.platform.application

interface Request<out RESPONSE>

interface Command<out RESPONSE> : Request<RESPONSE>

interface Query<out RESPONSE> : Request<RESPONSE>

interface Mediator {
    suspend fun <RESPONSE> dispatch(request: Request<RESPONSE>): RESPONSE
}

interface CommandHandler<in COMMAND : Command<RESPONSE>, RESPONSE> {
    suspend fun handle(command: COMMAND): RESPONSE
}

interface QueryHandler<in QUERY : Query<RESPONSE>, RESPONSE> {
    suspend fun handle(query: QUERY): RESPONSE
}

interface PrincipalContextProvider {
    suspend fun current(): com.profiletailors.smp.identity.domain.PrincipalContext?

    suspend fun require(): com.profiletailors.smp.identity.domain.PrincipalContext =
        current() ?: throw MissingPrincipalContextException()
}

interface ResourceContextProvider {
    fun current(): com.profiletailors.smp.platform.domain.ResourceContext?

    fun require(): com.profiletailors.smp.platform.domain.ResourceContext =
        current() ?: throw MissingResourceContextException()
}

interface AuditHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)

    suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact)
}

interface MetricsHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)
}

interface RateLimitHook {
    suspend fun onRequestReceived(requestName: String)
}

enum class RequestOutcome {
    SUCCESS,
    FAILURE,
}

data class AuthorizationDecisionAuditFact(
    val requestName: String,
    val requestPath: String,
    val permission: String,
    val principalId: String,
    val workspaceId: String?,
    val decision: com.profiletailors.smp.authorization.domain.AuthorizationDecision,
    val reasonCode: AuthorizationReasonCode,
    val roleKeys: List<String> = emptyList(),
)

enum class AuthorizationReasonCode {
    ROLE_PERMISSION,
    DIRECT_ALLOW,
    DIRECT_DENY,
    MISSING_MEMBERSHIP,
    MISSING_PERMISSION,
}

class MissingPrincipalContextException(
    message: String = "Authenticated principal context is required.",
) : IllegalStateException(message)

class MissingResourceContextException(
    message: String = "Resolved resource context is required.",
) : IllegalStateException(message)
