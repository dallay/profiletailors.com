package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorCandidate
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import com.profiletailors.smp.publishing.infrastructure.fake.FakeSocialContentFixtures
import com.profiletailors.smp.publishing.infrastructure.fake.FakeSocialContentProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SocialContentDiscoveryHandlerTest {
    private val scope = WorkspaceScope("workspace-1")
    private val candidates = listOf(
        SocialContentActorCandidate(
            id = "page-1",
            externalActorId = ProviderActorId("urn:li:organization:123"),
            kind = SocialAccountKind.ORGANIZATION_PAGE,
            displayName = "Profile Tailors",
            roleState = ActorRoleState.ADMIN,
            grantedScopes = setOf("r_organization_social", "r_organization_social_feed"),
        ),
        SocialContentActorCandidate(
            id = "page-2",
            externalActorId = ProviderActorId("urn:li:organization:456"),
            kind = SocialAccountKind.ORGANIZATION_PAGE,
            displayName = "Member Page",
            roleState = ActorRoleState.MEMBER,
            grantedScopes = emptySet(),
        ),
        SocialContentActorCandidate(
            id = "member-1",
            externalActorId = ProviderActorId("urn:li:person:789"),
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Personal Profile",
            roleState = ActorRoleState.ADMIN,
            grantedScopes = setOf("w_member_social"),
        ),
    )

    @Test
    fun `discovery persists only administered organization pages and replaces stale capability metadata`() = runTest {
        val repository = RecordingActorRepository(
            existing = SocialContentActor(
                id = "local-page-1",
                scope = scope,
                connectionId = "connection-1",
                provider = SocialProvider.LINKEDIN,
                externalActorId = ProviderActorId("urn:li:organization:123"),
                kind = SocialAccountKind.ORGANIZATION_PAGE,
                displayName = "Old Name",
                roleState = ActorRoleState.UNKNOWN,
                grantedScopes = emptySet(),
            ),
        )
        val handler = SocialContentDiscoveryHandler(
            provider = FakeSocialContentProvider(FakeSocialContentFixtures(actorCandidates = candidates)),
            actorRepository = repository,
            gates = SocialContentFeatureGates(discoveryEnabled = true),
        )

        val discovered = handler.handle(scope, "connection-1", SocialProvider.LINKEDIN)

        discovered shouldHaveSize 1
        discovered.single().id shouldBe "local-page-1"
        discovered.single().externalActorId shouldBe ProviderActorId("urn:li:organization:123")
        discovered.single().roleState shouldBe ActorRoleState.ADMIN
        discovered.single().grantedScopes shouldBe setOf("r_organization_social", "r_organization_social_feed")
        repository.upserted shouldHaveSize 1
    }

    @Test
    fun `disabled discovery fails before contacting the provider`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures(actorCandidates = candidates))
        val handler = SocialContentDiscoveryHandler(
            provider = provider,
            actorRepository = RecordingActorRepository(),
            gates = SocialContentFeatureGates(),
        )

        shouldThrow<SocialContentCapabilityDeniedException> {
            handler.handle(scope, "connection-1", SocialProvider.LINKEDIN)
        }.failure.name shouldBe "UNSUPPORTED"
        provider.calls shouldBe emptyList()
    }

    private class RecordingActorRepository(existing: SocialContentActor? = null) :
        com.profiletailors.smp.publishing.domain.SocialContentActorRepository {
        private val records = mutableListOf<SocialContentActor>().also { existing?.let(it::add) }
        val upserted = mutableListOf<SocialContentActor>()

        override suspend fun findByWorkspaceAndId(scope: WorkspaceScope, actorId: String): SocialContentActor? =
            records.firstOrNull { it.scope == scope && it.id == actorId }

        override suspend fun findByWorkspaceExternalId(
            scope: WorkspaceScope,
            provider: SocialProvider,
            externalActorId: ProviderActorId,
        ): SocialContentActor? = records.firstOrNull {
            it.scope == scope && it.provider == provider && it.externalActorId == externalActorId
        }

        override suspend fun upsert(actor: SocialContentActor): SocialContentActor {
            records.removeIf {
                it.scope == actor.scope && it.provider == actor.provider && it.externalActorId == actor.externalActorId
            }
            records += actor
            upserted += actor
            return actor
        }
    }
}
