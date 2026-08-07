package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import java.time.Duration

interface LinkedInPageDiscoveryHandler {
    suspend fun handle(scope: WorkspaceScope, connectionId: String, provider: SocialProvider): List<SocialContentActor>
}

class SocialContentDiscoveryHandler(
    private val provider: SocialContentProvider,
    private val actorRepository: SocialContentActorRepository,
    private val gates: SocialContentFeatureGates,
) {
    suspend fun handle(
        scope: WorkspaceScope,
        connectionId: String,
        providerName: SocialProvider,
    ): List<SocialContentActor> {
        requireAllowed(scope, connectionId, providerName)
        val discovered = mutableListOf<SocialContentActor>()
        provider.discoverActors(scope, connectionId, connectionId)
            .asSequence()
            .filter { it.kind == SocialAccountKind.ORGANIZATION_PAGE }
            .filter { it.roleState == ActorRoleState.ADMIN }
            .forEach { candidate ->
                val existing = actorRepository.findByWorkspaceExternalId(
                    scope = scope,
                    provider = providerName,
                    externalActorId = candidate.externalActorId,
                )
                discovered += actorRepository.upsert(
                    SocialContentActor(
                        id = existing?.id ?: candidate.id,
                        scope = scope,
                        connectionId = connectionId,
                        provider = providerName,
                        externalActorId = candidate.externalActorId,
                        kind = candidate.kind,
                        displayName = candidate.displayName,
                        roleState = candidate.roleState,
                        grantedScopes = candidate.grantedScopes,
                    ),
                )
            }
        return discovered
    }

    private fun requireAllowed(scope: WorkspaceScope, connectionId: String, providerName: SocialProvider) {
        val placeholder = SocialContentActor(
            id = "discovery-placeholder",
            scope = scope,
            connectionId = connectionId,
            provider = providerName,
            externalActorId = ProviderActorId("urn:li:organization:discovery"),
            kind = SocialAccountKind.ORGANIZATION_PAGE,
            displayName = "Discovery",
            roleState = ActorRoleState.ADMIN,
            grantedScopes = setOf("rw_organization_admin"),
        )
        when (
            val decision = DefaultCapabilityResolver(gates).resolve(
                placeholder,
                CapabilityOperation.DISCOVER_ACTORS,
                com.profiletailors.smp.publishing.domain.RetentionRequirements(
                    Duration.ofHours(ACTIVITY_TTL_HOURS),
                    Duration.ofHours(COMMENTER_PROFILE_TTL_HOURS),
                ),
            )
        ) {
            com.profiletailors.smp.publishing.domain.CapabilityDecision.Allowed -> Unit
            is com.profiletailors.smp.publishing.domain.CapabilityDecision.Denied ->
                throw SocialContentCapabilityDeniedException(CapabilityOperation.DISCOVER_ACTORS, decision.failure)
        }
    }

    private companion object {
        const val ACTIVITY_TTL_HOURS = 48L
        const val COMMENTER_PROFILE_TTL_HOURS = 24L
    }
}
