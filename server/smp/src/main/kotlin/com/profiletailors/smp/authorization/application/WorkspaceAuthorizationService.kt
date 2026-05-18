package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationScope
import com.profiletailors.smp.authorization.domain.DirectGrant
import com.profiletailors.smp.authorization.domain.Entitlement
import com.profiletailors.smp.authorization.domain.GrantEffect
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.Role
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.application.AuthorizationReasonCode
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import java.time.Clock

interface WorkspaceMembershipResolver {
    suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): WorkspaceMembership?
}

interface WorkspaceMembershipRoleResolver {
    suspend fun resolve(membership: WorkspaceMembership): Set<Role>
}

interface DirectGrantResolver {
    suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<DirectGrant>
}

interface ScopeResolver {
    suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<AuthorizationScope>
}

interface EntitlementResolver {
    suspend fun resolve(resourceContext: ResourceContext): Set<Entitlement>
}

interface WorkspaceAuthorizationDecider {
    suspend fun decide(
        requiredPermission: PermissionKey,
        requiredEntitlementKey: String? = null,
    ): AuthorizationDecision

    suspend fun decideDetailed(
        requiredPermission: PermissionKey,
        requiredEntitlementKey: String? = null,
    ): AuthorizationDecisionResult
}

data class AuthorizationDecisionResult(
    val decision: AuthorizationDecision,
    val reasonCode: AuthorizationReasonCode,
    val roleKeys: Set<String> = emptySet(),
)

class WorkspaceAuthorizationService(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceMembershipResolver: WorkspaceMembershipResolver,
    private val workspaceMembershipRoleResolver: WorkspaceMembershipRoleResolver,
    private val directGrantResolver: DirectGrantResolver = NoOpDirectGrantResolver(),
    private val scopeResolver: ScopeResolver = NoOpScopeResolver(),
    private val entitlementResolver: EntitlementResolver = NoOpEntitlementResolver(),
    private val clock: Clock = Clock.systemUTC(),
) : WorkspaceAuthorizationDecider {
    override suspend fun decide(
        requiredPermission: PermissionKey,
        requiredEntitlementKey: String?,
    ): AuthorizationDecision = decideDetailed(requiredPermission, requiredEntitlementKey).decision

    override suspend fun decideDetailed(
        requiredPermission: PermissionKey,
        requiredEntitlementKey: String?,
    ): AuthorizationDecisionResult {
        val principalContext = principalContextProvider.require()
        val resourceContext = resourceContextProvider.require()
        val membership = workspaceMembershipResolver.resolve(principalContext, resourceContext)
            ?.takeIf { it.isActive() }

        if (membership == null) {
            return AuthorizationDecisionResult(
                decision = AuthorizationDecision.DENY,
                reasonCode = AuthorizationReasonCode.MISSING_MEMBERSHIP,
            )
        }

        val roles = workspaceMembershipRoleResolver.resolve(membership)
        val rolePermissions = roles
            .flatMap { role -> role.permissions }
            .toSet()

        val directGrants = directGrantResolver.resolve(principalContext, resourceContext)
            .filter { grant -> grant.permission == requiredPermission && grant.isActive(clock.instant()) }
            .toSet()

        scopeResolver.resolve(principalContext, resourceContext)
        val entitlements = entitlementResolver.resolve(resourceContext)
        val entitlementSatisfied = requiredEntitlementKey == null || entitlements.any { entitlement ->
            entitlement.key == requiredEntitlementKey && entitlement.enabled
        }

        val (decision, reasonCode) = when {
            !entitlementSatisfied ->
                AuthorizationDecision.DENY to AuthorizationReasonCode.MISSING_ENTITLEMENT
            directGrants.any { it.effect == GrantEffect.DENY } ->
                AuthorizationDecision.DENY to AuthorizationReasonCode.DIRECT_DENY
            directGrants.any { it.effect == GrantEffect.ALLOW } ->
                AuthorizationDecision.ALLOW to AuthorizationReasonCode.DIRECT_ALLOW
            requiredPermission in rolePermissions ->
                AuthorizationDecision.ALLOW to AuthorizationReasonCode.ROLE_PERMISSION
            else ->
                AuthorizationDecision.DENY to AuthorizationReasonCode.MISSING_PERMISSION
        }

        return AuthorizationDecisionResult(
            decision = decision,
            reasonCode = reasonCode,
            roleKeys = roles.mapTo(sortedSetOf()) { it.key },
        )
    }
}

class NoOpDirectGrantResolver : DirectGrantResolver {
    override suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<DirectGrant> = emptySet()
}

class NoOpScopeResolver : ScopeResolver {
    override suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<AuthorizationScope> = emptySet()
}

class NoOpEntitlementResolver : EntitlementResolver {
    override suspend fun resolve(resourceContext: ResourceContext): Set<Entitlement> = emptySet()
}
