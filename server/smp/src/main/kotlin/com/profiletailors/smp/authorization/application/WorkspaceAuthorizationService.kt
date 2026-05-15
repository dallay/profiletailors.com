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
    suspend fun decide(requiredPermission: PermissionKey): AuthorizationDecision

    suspend fun decideDetailed(requiredPermission: PermissionKey): AuthorizationDecisionResult
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
    override suspend fun decide(requiredPermission: PermissionKey): AuthorizationDecision =
        decideDetailed(requiredPermission).decision

    override suspend fun decideDetailed(requiredPermission: PermissionKey): AuthorizationDecisionResult {
        val principalContext = principalContextProvider.require()
        val resourceContext = resourceContextProvider.require()
        val membership = workspaceMembershipResolver.resolve(principalContext, resourceContext)
            ?.takeIf { it.isActive() }
            ?: return AuthorizationDecisionResult(
                decision = AuthorizationDecision.DENY,
                reasonCode = AuthorizationReasonCode.MISSING_MEMBERSHIP,
            )

        val roles = workspaceMembershipRoleResolver.resolve(membership)
        val rolePermissions = roles
            .flatMap { role -> role.permissions }
            .toSet()

        val directGrants = directGrantResolver.resolve(principalContext, resourceContext)
            .filter { grant -> grant.permission == requiredPermission && grant.isActive(clock.instant()) }

        scopeResolver.resolve(principalContext, resourceContext)
        entitlementResolver.resolve(resourceContext)

        if (directGrants.any { grant -> grant.effect == GrantEffect.DENY }) {
            return AuthorizationDecisionResult(
                decision = AuthorizationDecision.DENY,
                reasonCode = AuthorizationReasonCode.DIRECT_DENY,
                roleKeys = roles.mapTo(sortedSetOf()) { role -> role.key },
            )
        }

        if (directGrants.any { grant -> grant.effect == GrantEffect.ALLOW }) {
            return AuthorizationDecisionResult(
                decision = AuthorizationDecision.ALLOW,
                reasonCode = AuthorizationReasonCode.DIRECT_ALLOW,
                roleKeys = roles.mapTo(sortedSetOf()) { role -> role.key },
            )
        }

        return if (requiredPermission in rolePermissions) {
            AuthorizationDecisionResult(
                decision = AuthorizationDecision.ALLOW,
                reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                roleKeys = roles.mapTo(sortedSetOf()) { role -> role.key },
            )
        } else {
            AuthorizationDecisionResult(
                decision = AuthorizationDecision.DENY,
                reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
                roleKeys = roles.mapTo(sortedSetOf()) { role -> role.key },
            )
        }
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
