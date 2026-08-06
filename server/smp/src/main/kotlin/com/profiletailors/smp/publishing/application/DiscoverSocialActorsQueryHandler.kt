package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorCandidate
import com.profiletailors.smp.publishing.domain.SocialContentCapabilityResolver
import com.profiletailors.smp.publishing.domain.SocialContentProvider

/** Query handler that discovers only administered organization pages. */
class DiscoverSocialActorsQueryHandler(
    private val provider: SocialContentProvider,
    private val capabilityResolver: SocialContentCapabilityResolver,
    private val retention: RetentionRequirements,
) {
    suspend fun handle(actor: SocialContentActor): List<SocialContentActor> {
        requireSocialContentCapability(actor, CapabilityOperation.DISCOVER_ACTORS, capabilityResolver, retention)
        return provider.discoverActors(actor.scope, actor.connectionId)
            .asSequence()
            .filter { it.kind == SocialAccountKind.ORGANIZATION_PAGE }
            .filter { it.roleState == com.profiletailors.smp.publishing.domain.ActorRoleState.ADMIN }
            .map { it.toActor(actor) }
            .toList()
    }

    private fun SocialContentActorCandidate.toActor(source: SocialContentActor) = SocialContentActor(
        id = id,
        scope = source.scope,
        connectionId = source.connectionId,
        provider = source.provider,
        externalActorId = externalActorId,
        kind = kind,
        displayName = displayName,
        roleState = roleState,
        grantedScopes = grantedScopes,
    )
}
