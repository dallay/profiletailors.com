package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationScope
import com.profiletailors.smp.authorization.domain.DirectGrant
import com.profiletailors.smp.authorization.domain.DirectGrantResolver
import com.profiletailors.smp.authorization.domain.Entitlement
import com.profiletailors.smp.authorization.domain.EntitlementResolver
import com.profiletailors.smp.authorization.domain.GrantEffect
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.Role
import com.profiletailors.smp.authorization.domain.ScopeResolver
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipRoleResolver
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.application.AuthorizationReasonCode
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext
import java.time.Clock

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
        resourceContextOverride: ResourceContext?,
    ): AuthorizationDecision =
        decideDetailed(
            requiredPermission = requiredPermission,
            requiredEntitlementKey = requiredEntitlementKey,
            resourceContextOverride = resourceContextOverride,
        ).decision

    override suspend fun decideDetailed(
        requiredPermission: PermissionKey,
        requiredEntitlementKey: String?,
        resourceContextOverride: ResourceContext?,
    ): AuthorizationDecisionResult {
        val principalContext = principalContextProvider.require()
        val resourceContext = resourceContextOverride ?: resourceContextProvider.require()
        val membership = workspaceMembershipResolver.resolve(principalContext, resourceContext)
            ?.takeIf { it.isActive() }
            ?: return missingMembershipDecision()

        val roles = workspaceMembershipRoleResolver.resolve(membership)
        val roleKeys = roles.mapTo(sortedSetOf()) { it.key }
        val baseDecision = decideBaseAccess(
            requiredPermission = requiredPermission,
            requiredEntitlementKey = requiredEntitlementKey,
            resourceContext = resourceContext,
            roles = roles,
            roleKeys = roleKeys,
            principalContext = principalContext,
        )

        return finalDecision(
            baseDecision = baseDecision,
            roleKeys = roleKeys,
            requiredPermission = requiredPermission,
            resourceContext = resourceContext,
            principalContext = principalContext,
        )
    }

    private fun missingMembershipDecision(): AuthorizationDecisionResult =
        AuthorizationDecisionResult(
            decision = AuthorizationDecision.DENY,
            reasonCode = AuthorizationReasonCode.MISSING_MEMBERSHIP,
        )

    private suspend fun decideBaseAccess(
        requiredPermission: PermissionKey,
        requiredEntitlementKey: String?,
        resourceContext: ResourceContext,
        roles: Set<Role>,
        roleKeys: Set<String>,
        principalContext: PrincipalContext,
    ): AuthorizationDecisionResult {
        val rolePermissions = roles.flatMap { role -> role.permissions }.toSet()
        val directGrants = directGrantResolver.resolve(principalContext, resourceContext)
            .filter { grant -> grant.permission == requiredPermission && grant.isActive(clock.instant()) }
            .toSet()
        val entitlements = entitlementResolver.resolve(resourceContext)
        val entitlementSatisfied = requiredEntitlementKey == null || entitlements.any { entitlement ->
            entitlement.key == requiredEntitlementKey && entitlement.enabled
        }

        return when {
            !entitlementSatisfied -> decision(AuthorizationReasonCode.MISSING_ENTITLEMENT, roleKeys)
            directGrants.any { it.effect == GrantEffect.DENY } ->
                decision(AuthorizationReasonCode.DIRECT_DENY, roleKeys)
            directGrants.any { it.effect == GrantEffect.ALLOW } ->
                decision(
                    reasonCode = AuthorizationReasonCode.DIRECT_ALLOW,
                    roleKeys = roleKeys,
                    authorizationDecision = AuthorizationDecision.ALLOW,
                )
            requiredPermission in rolePermissions ->
                decision(
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                    roleKeys = roleKeys,
                    authorizationDecision = AuthorizationDecision.ALLOW,
                )
            else -> decision(AuthorizationReasonCode.MISSING_PERMISSION, roleKeys)
        }
    }

    private suspend fun finalDecision(
        baseDecision: AuthorizationDecisionResult,
        roleKeys: Set<String>,
        requiredPermission: PermissionKey,
        resourceContext: ResourceContext,
        principalContext: PrincipalContext,
    ): AuthorizationDecisionResult {
        if (baseDecision.decision != AuthorizationDecision.ALLOW) {
            return baseDecision
        }

        val scopes = scopeResolver.resolve(principalContext, resourceContext)
        val scopeDecision = evaluateScopeReduction(
            requiredPermission = requiredPermission,
            resourceContext = resourceContext,
            scopes = scopes,
        )

        return scopeDecision?.copy(roleKeys = roleKeys)
            ?: baseDecision.copy(roleKeys = roleKeys)
    }

    private fun evaluateScopeReduction(
        requiredPermission: PermissionKey,
        resourceContext: ResourceContext,
        scopes: Set<AuthorizationScope>,
    ): AuthorizationDecisionResult? {
        val targetContext = targetScopeContext(resourceContext) ?: return null
        val applicableScopes = scopes.filter { scope ->
            scope.permission == requiredPermission &&
                scope.resourceContextType == resourceContext.type &&
                scope.targetResourceType == targetContext.targetResourceType
        }

        val scopeAllowsTarget = applicableScopes.isEmpty() ||
            applicableScopes.any { scope ->
                targetContext.targetResourceId in scope.allowedTargetResourceIds
            }

        return if (scopeAllowsTarget) null
        else decision(AuthorizationReasonCode.SCOPE_REDUCED_TARGET)
    }

    private fun targetScopeContext(resourceContext: ResourceContext): TargetScopeContext? =
        resourceContext.targetResourceType
            ?.takeIf { resourceContext.targetResourceId != null }
            ?.let { targetResourceType ->
                TargetScopeContext(
                    targetResourceType = targetResourceType,
                    targetResourceId = requireNotNull(resourceContext.targetResourceId),
                )
            }

    private fun decision(
        reasonCode: AuthorizationReasonCode,
        roleKeys: Set<String> = emptySet(),
        authorizationDecision: AuthorizationDecision = AuthorizationDecision.DENY,
    ): AuthorizationDecisionResult = AuthorizationDecisionResult(
        decision = authorizationDecision,
        reasonCode = reasonCode,
        roleKeys = roleKeys,
    )

    private data class TargetScopeContext(
        val targetResourceType: String,
        val targetResourceId: String,
    )
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
