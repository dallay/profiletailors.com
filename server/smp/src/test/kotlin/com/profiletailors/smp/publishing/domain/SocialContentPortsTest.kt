package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class SocialContentPortsTest {
    private val workspace = WorkspaceScope("workspace-1")
    private val actor = SocialContentActor(
        id = "actor-1",
        scope = workspace,
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf(
            "r_organization_social",
            "r_organization_social_social_actions",
            "w_organization_social",
        ),
    )
    private val retention = RetentionRequirements(Duration.ofHours(48), Duration.ofHours(24))

    @Test
    fun `capability policy reports missing scope before allowing replies`() {
        val withoutReplyScope = actor.copy(grantedScopes = setOf("r_organization_social"))
        val policy = DefaultCapabilityResolver(
            SocialContentFeatureGates(importEnabled = true, inboxEnabled = true, repliesEnabled = true),
        )

        policy.resolve(withoutReplyScope, CapabilityOperation.REPLY, retention) shouldBe
            CapabilityDecision.Denied(CapabilityFailure.MISSING_SCOPE)
        policy.resolve(actor, CapabilityOperation.REPLY, retention) shouldBe CapabilityDecision.Allowed
    }

    @Test
    fun `capability policy keeps every operation disabled when feature gates are off`() {
        val policy = DefaultCapabilityResolver(SocialContentFeatureGates())

        policy.resolve(actor, CapabilityOperation.READ_POSTS, retention) shouldBe
            CapabilityDecision.Denied(CapabilityFailure.UNSUPPORTED)
    }

    @Test
    fun `capability policy distinguishes reauthentication and role failures`() {
        val policy = DefaultCapabilityResolver(
            SocialContentFeatureGates(discoveryEnabled = true, importEnabled = true, repliesEnabled = true),
        )

        policy.resolve(
            actor.copy(roleState = ActorRoleState.REVOKED),
            CapabilityOperation.READ_POSTS,
            retention,
        ) shouldBe CapabilityDecision.Denied(CapabilityFailure.REAUTH_REQUIRED)
        policy.resolve(
            actor.copy(roleState = ActorRoleState.MEMBER),
            CapabilityOperation.REPLY,
            retention,
        ) shouldBe CapabilityDecision.Denied(CapabilityFailure.ROLE_REQUIRED)
    }

    @Test
    fun `pagination limits reject invalid values`() {
        shouldThrow<IllegalArgumentException> { SocialContentSyncLimits(pageSize = 0, maxPages = 1) }
        shouldThrow<IllegalArgumentException> { SocialContentSyncLimits(pageSize = 101, maxPages = 1) }
        shouldThrow<IllegalArgumentException> { SocialContentSyncLimits(pageSize = 1, maxPages = 0) }
    }


    @Test
    fun `cursor and checkpoint preserve workspace and only advance after success`() {
        val cursor = PageCursor("next-1")
        val checkpoint = SyncCheckpoint(
            scope = workspace,
            actorId = actor.id,
            resource = SyncResource.POSTS,
            cursor = cursor,
            lastSuccessfulAt = null,
        )

        checkpoint.advance(PageCursor("next-2"), java.time.Instant.parse("2026-08-01T12:00:00Z")).cursor shouldBe
            PageCursor("next-2")
        checkpoint.scope shouldBe workspace
        cursor.value shouldBe "next-1"
    }
}
