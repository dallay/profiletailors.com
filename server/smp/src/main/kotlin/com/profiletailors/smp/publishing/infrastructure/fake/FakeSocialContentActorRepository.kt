package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorRepository
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope

class FakeSocialContentActorRepository(initial: List<SocialContentActor> = emptyList()) : SocialContentActorRepository {
    private val actors = initial.toMutableList()

    override suspend fun findByWorkspaceAndId(scope: WorkspaceScope, actorId: String): SocialContentActor? =
        actors.firstOrNull { it.scope == scope && it.id == actorId }

    override suspend fun findByWorkspaceExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        externalActorId: ProviderActorId,
    ): SocialContentActor? = actors.firstOrNull {
        it.scope == scope && it.provider == provider && it.externalActorId == externalActorId
    }

    override suspend fun upsert(actor: SocialContentActor): SocialContentActor {
        actors.removeIf {
            it.scope == actor.scope && it.provider == actor.provider && it.externalActorId == actor.externalActorId
        }
        actors += actor
        return actor
    }

    fun all(): List<SocialContentActor> = actors.toList()
}
