package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeSocialContentActorRepositoryTest {
    @Test
    fun `should find actor by workspace and id when present`() = runTest {
        val actor = fixtureActor()
        val repository = FakeSocialContentActorRepository(listOf(actor))

        repository.findByWorkspaceAndId(actor.scope, actor.id) shouldBe actor
    }

    @Test
    fun `should return null when actor id is not present in workspace`() = runTest {
        val actor = fixtureActor()
        val repository = FakeSocialContentActorRepository(listOf(actor))

        repository.findByWorkspaceAndId(actor.scope, "actor-missing") shouldBe null
    }

    @Test
    fun `should find actor by external id when present`() = runTest {
        val actor = fixtureActor()
        val repository = FakeSocialContentActorRepository(listOf(actor))

        repository.findByWorkspaceExternalId(actor.scope, actor.provider, actor.externalActorId) shouldBe actor
    }

    @Test
    fun `should return null when external id is not present`() = runTest {
        val actor = fixtureActor()
        val repository = FakeSocialContentActorRepository(listOf(actor))

        repository.findByWorkspaceExternalId(
            actor.scope,
            actor.provider,
            ProviderActorId("urn:li:organization:missing"),
        ) shouldBe null
    }

    @Test
    fun `should upsert new actor and expose all records`() = runTest {
        val actor = fixtureActor()
        val repository = FakeSocialContentActorRepository()

        val saved = repository.upsert(actor)

        saved shouldBe actor
        repository.all() shouldContainExactly listOf(actor)
    }

    @Test
    fun `should replace existing actor with same identity on upsert`() = runTest {
        val original = fixtureActor(id = "actor-1", displayName = "Original Name")
        val replacement = fixtureActor(id = "actor-2", displayName = "Updated Name")
        val repository = FakeSocialContentActorRepository(listOf(original))

        repository.upsert(replacement)

        repository.all().single() shouldBe replacement
        repository.findByWorkspaceAndId(original.scope, "actor-1") shouldBe null
        repository.findByWorkspaceAndId(replacement.scope, "actor-2") shouldNotBe null
    }

    private fun fixtureActor(id: String = "actor-1", displayName: String = "Profile Tailors") = SocialContentActor(
        id = id,
        scope = WorkspaceScope("workspace-1"),
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = displayName,
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social"),
    )
}
