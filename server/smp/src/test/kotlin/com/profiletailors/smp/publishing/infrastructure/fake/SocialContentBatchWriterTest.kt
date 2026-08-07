package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class SocialContentBatchWriterTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val workspaceOne = WorkspaceScope("workspace-1")
    private val workspaceTwo = WorkspaceScope("workspace-2")
    private val actor = SocialContentActor(
        id = "account-1",
        scope = workspaceOne,
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:1"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social"),
    )

    @Test
    fun `upserts posts before saving checkpoint`() = runTest {
        val writer = FakeSocialContentBatchWriter()
        val first = post(workspaceOne, "post-1")
        val second = post(workspaceOne, "post-2")
        val checkpoint = checkpoint(workspaceOne, actor.id)

        writer.persist(
            posts = listOf(first, second),
            tombstoneIds = emptySet(),
            checkpoint = checkpoint,
        )

        writer.events shouldBe listOf("upsert:post-1", "upsert:post-2", "checkpoint")
        writer.find(workspaceOne, "post-1") shouldBe first
        writer.find(workspaceOne, "post-2") shouldBe second
        writer.savedCheckpoint(workspaceOne, actor.id) shouldBe checkpoint
    }

    @Test
    fun `tombstones absent posts only when complete sync identities are supplied`() = runTest {
        val absent = post(workspaceOne, "post-absent")
        val current = post(workspaceOne, "post-current")
        val writer = FakeSocialContentBatchWriter(initialPosts = listOf(absent, current))

        writer.persist(
            posts = listOf(current),
            tombstoneIds = setOf(current.externalPostId),
            checkpoint = checkpoint(workspaceOne, actor.id),
        )

        writer.find(workspaceOne, "post-absent")?.lifecycle shouldBe PostLifecycle.TOMBSTONED
        writer.events shouldContain "tombstone-missing"

        val incrementalWriter = FakeSocialContentBatchWriter(initialPosts = listOf(absent, current))
        incrementalWriter.persist(
            posts = listOf(current),
            tombstoneIds = emptySet(),
            checkpoint = checkpoint(workspaceOne, actor.id),
        )

        incrementalWriter.find(workspaceOne, "post-absent")?.lifecycle shouldBe PostLifecycle.PUBLISHED
        incrementalWriter.events shouldNotContain "tombstone-missing"
    }

    @Test
    fun `failed post operation rolls back posts and does not save checkpoint`() = runTest {
        val existing = post(workspaceOne, "post-existing")
        val writer = FakeSocialContentBatchWriter(
            initialPosts = listOf(existing),
            failOnExternalPostId = ExternalPostId("post-failing"),
        )

        shouldThrow<IllegalStateException> {
            writer.persist(
                posts = listOf(post(workspaceOne, "post-new"), post(workspaceOne, "post-failing")),
                tombstoneIds = emptySet(),
                checkpoint = checkpoint(workspaceOne, actor.id),
            )
        }

        writer.find(workspaceOne, "post-existing") shouldBe existing
        writer.find(workspaceOne, "post-new") shouldBe null
        writer.find(workspaceOne, "post-failing") shouldBe null
        writer.savedCheckpoint(workspaceOne, actor.id) shouldBe null
        writer.events shouldNotContain "checkpoint"
    }

    @Test
    fun `failed checkpoint operation rolls back staged posts and tombstones`() = runTest {
        val existing = post(workspaceOne, "post-existing")
        val writer = FakeSocialContentBatchWriter(
            initialPosts = listOf(existing),
            failOnCheckpoint = true,
        )

        shouldThrow<IllegalStateException> {
            writer.persist(
                posts = listOf(post(workspaceOne, "post-current")),
                tombstoneIds = setOf(ExternalPostId("post-current")),
                checkpoint = checkpoint(workspaceOne, actor.id),
            )
        }

        writer.find(workspaceOne, "post-existing") shouldBe existing
        writer.find(workspaceOne, "post-current") shouldBe null
        writer.savedCheckpoint(workspaceOne, actor.id) shouldBe null
        writer.events shouldBe emptyList()
    }

    @Test
    fun `workspace-qualified identities prevent posts and tombstones crossing workspaces`() = runTest {
        val workspaceOnePost = post(workspaceOne, "shared-external-id")
        val workspaceTwoPost = post(workspaceTwo, "shared-external-id")
        val writer = FakeSocialContentBatchWriter(initialPosts = listOf(workspaceOnePost, workspaceTwoPost))

        writer.persist(
            posts = listOf(workspaceOnePost.copy(body = "updated in workspace one")),
            tombstoneIds = setOf(workspaceOnePost.externalPostId),
            checkpoint = checkpoint(workspaceOne, actor.id),
        )

        writer.find(workspaceOne, "shared-external-id")?.body shouldBe "updated in workspace one"
        writer.find(workspaceOne, "shared-external-id")?.lifecycle shouldBe PostLifecycle.PUBLISHED
        writer.find(workspaceTwo, "shared-external-id") shouldBe workspaceTwoPost
        writer.savedCheckpoint(workspaceTwo, actor.id) shouldBe null
    }

    @Test
    fun `workspace checkpoints remain isolated across successful batches`() = runTest {
        val writer = FakeSocialContentBatchWriter()

        writer.persist(
            posts = listOf(post(workspaceOne, "post-1")),
            tombstoneIds = emptySet(),
            checkpoint = checkpoint(workspaceOne, actor.id),
        )
        writer.persist(
            posts = listOf(post(workspaceTwo, "post-2")),
            tombstoneIds = emptySet(),
            checkpoint = checkpoint(workspaceTwo, actor.id),
        )

        writer.savedCheckpoint(workspaceOne, actor.id) shouldBe checkpoint(workspaceOne, actor.id)
        writer.savedCheckpoint(workspaceTwo, actor.id) shouldBe checkpoint(workspaceTwo, actor.id)
    }

    private fun post(scope: WorkspaceScope, externalId: String): SocialPost = SocialPost.imported(
        scope = scope,
        actor = actor.copy(scope = scope),
        externalPostId = ExternalPostId(externalId),
        publishedAt = now.minusSeconds(60),
        now = now,
    )

    private fun checkpoint(scope: WorkspaceScope, actorId: String) = SyncCheckpoint(
        scope = scope,
        actorId = actorId,
        resource = SyncResource.POSTS,
        cursor = PageCursor("next"),
        highWaterMark = now,
        lastSuccessfulAt = now,
        provider = SocialProvider.LINKEDIN,
    )
}
