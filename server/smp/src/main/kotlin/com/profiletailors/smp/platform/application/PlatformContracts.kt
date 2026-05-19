package com.profiletailors.smp.platform.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.domain.ResourceContext

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
    suspend fun current(): PrincipalContext?

    suspend fun require(): PrincipalContext =
        current() ?: throw MissingPrincipalContextException()
}

interface ResourceContextProvider {
    fun current(): ResourceContext?

    fun require(): ResourceContext =
        current() ?: throw MissingResourceContextException()
}

interface AuditHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)

    suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact)

    suspend fun onMutation(fact: MutationAuditFact)
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
    val decision: AuthorizationDecision,
    val reasonCode: AuthorizationReasonCode,
    val roleKeys: List<String> = emptyList(),
)

data class MutationAuditFact(
    val action: String,
    val targetType: String,
    val targetId: String,
    val actorPrincipalId: String,
    val workspaceId: String?,
    val outcome: MutationAuditOutcome,
    val details: Map<String, String> = emptyMap(),
)

enum class MutationAuditOutcome {
    SUCCESS,
    REJECTED,
}

enum class AuthorizationReasonCode {
    ROLE_PERMISSION,
    DIRECT_ALLOW,
    DIRECT_DENY,
    MISSING_MEMBERSHIP,
    MISSING_PERMISSION,
    MISSING_ENTITLEMENT,
    REVOKED_CREDENTIAL,
    SCOPE_REDUCED_TARGET,
}

class MissingPrincipalContextException(
    message: String = "Authenticated principal context is required.",
) : IllegalStateException(message)

class MissingResourceContextException(
    message: String = "Resolved resource context is required.",
) : IllegalStateException(message)
