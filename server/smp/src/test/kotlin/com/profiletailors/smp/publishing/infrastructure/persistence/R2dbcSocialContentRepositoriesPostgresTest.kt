package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.CalendarCursorVersion
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.InvalidSocialContentCursorException
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.PostOrigin
import com.profiletailors.smp.publishing.domain.SocialContentCalendarCursor
import com.profiletailors.smp.publishing.domain.SocialContentCalendarCursorCodec
import com.profiletailors.smp.publishing.domain.SocialContentCalendarQuery
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcSocialContentRepositoriesPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var repository: R2dbcSocialContentRepositories

    @BeforeEach
    fun setUpRepository() = runTest {
        seedPrincipalWorkspaceAndAccount()
        repository = R2dbcSocialContentRepositories(databaseClient)
    }

    @Test
    fun `upsert persists and reads imported social post`() = runTest {
        repository.upsert(samplePost("linkedin-post-1"))

        val loaded = repository.findByWorkspaceAndExternalId(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            externalPostId = ExternalPostId("linkedin-post-1"),
        )

        assertNotNull(loaded)
        assertEquals("soacc-1", loaded?.actorId)
        assertEquals("Hello from LinkedIn", loaded?.body)
        assertEquals(PostLifecycle.PUBLISHED, loaded?.lifecycle)
        assertEquals(PostOrigin.EXTERNAL_OR_UNKNOWN, loaded?.origin)
    }

    @Test
    fun `upsert updates existing post on identity conflict`() = runTest {
        repository.upsert(samplePost("linkedin-post-conflict"))
        repository.upsert(
            samplePost("linkedin-post-conflict").copy(
                body = "Updated body",
                lifecycle = PostLifecycle.TOMBSTONED,
            ),
        )

        val loaded = repository.findByWorkspaceAndExternalId(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            externalPostId = ExternalPostId("linkedin-post-conflict"),
        )

        assertNotNull(loaded)
        assertEquals("Updated body", loaded?.body)
        assertEquals(PostLifecycle.TOMBSTONED, loaded?.lifecycle)
    }

    @Test
    fun `findByWorkspaceAndExternalId returns null for unknown post`() = runTest {
        val loaded = repository.findByWorkspaceAndExternalId(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            externalPostId = ExternalPostId("linkedin-post-unknown"),
        )

        assertNull(loaded)
    }

    @Test
    fun `tombstoneMissing marks unseen posts as tombstoned`() = runTest {
        repository.upsert(samplePost("linkedin-post-seen-1"))
        repository.upsert(samplePost("linkedin-post-seen-2"))
        repository.upsert(samplePost("linkedin-post-gone"))

        repository.tombstoneMissing(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            seenExternalIds = setOf(
                ExternalPostId("linkedin-post-seen-1"),
                ExternalPostId("linkedin-post-seen-2"),
            ),
        )

        val seen = repository.findByWorkspaceAndExternalId(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            externalPostId = ExternalPostId("linkedin-post-seen-1"),
        )
        val gone = repository.findByWorkspaceAndExternalId(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            externalPostId = ExternalPostId("linkedin-post-gone"),
        )

        assertEquals(PostLifecycle.PUBLISHED, seen?.lifecycle)
        assertEquals(PostLifecycle.TOMBSTONED, gone?.lifecycle)
    }

    @Test
    fun `tombstoneMissing works with single seen external id`() = runTest {
        repository.upsert(samplePost("linkedin-post-solo"))
        repository.upsert(samplePost("linkedin-post-gone-solo"))

        repository.tombstoneMissing(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            seenExternalIds = setOf(ExternalPostId("linkedin-post-solo")),
        )

        val gone = repository.findByWorkspaceAndExternalId(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            externalPostId = ExternalPostId("linkedin-post-gone-solo"),
        )

        assertEquals(PostLifecycle.TOMBSTONED, gone?.lifecycle)
    }

    @Test
    fun `tombstoneMissing with empty seen set leaves posts untouched`() = runTest {
        repository.upsert(samplePost("linkedin-post-keep"))

        repository.tombstoneMissing(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            seenExternalIds = emptySet(),
        )

        val loaded = repository.findByWorkspaceAndExternalId(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "soacc-1",
            externalPostId = ExternalPostId("linkedin-post-keep"),
        )

        assertEquals(PostLifecycle.PUBLISHED, loaded?.lifecycle)
    }

    @Test
    fun `save and find persist sync checkpoint`() = runTest {
        val checkpoint = SyncCheckpoint(
            scope = WorkspaceScope("workspace-1"),
            actorId = "soacc-1",
            resource = SyncResource.POSTS,
            cursor = PageCursor("cursor-abc"),
            highWaterMark = Instant.parse("2026-08-01T00:00:00Z"),
            lastSuccessfulAt = Instant.parse("2026-08-01T01:00:00Z"),
        )

        repository.save(checkpoint)

        val loaded = repository.find(
            scope = WorkspaceScope("workspace-1"),
            actorId = "soacc-1",
            resource = SyncResource.POSTS,
        )

        assertNotNull(loaded)
        assertEquals("cursor-abc", loaded?.cursor?.value)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), loaded?.highWaterMark)
        assertEquals(Instant.parse("2026-08-01T01:00:00Z"), loaded?.lastSuccessfulAt)
    }

    @Test
    fun `save updates existing comment checkpoint on post-scoped identity conflict`() = runTest {
        val postId = ExternalPostId("linkedin-post-comments")
        val base = SyncCheckpoint(
            scope = WorkspaceScope("workspace-1"),
            actorId = "soacc-1",
            resource = SyncResource.COMMENTS,
            cursor = PageCursor("cursor-first"),
            lastSuccessfulAt = Instant.parse("2026-08-01T01:00:00Z"),
            postId = postId,
        )
        repository.save(base)
        repository.save(base.copy(cursor = PageCursor("cursor-second")))

        val loaded = repository.find(
            scope = WorkspaceScope("workspace-1"),
            actorId = "soacc-1",
            resource = SyncResource.COMMENTS,
            postId = postId,
        )

        assertNotNull(loaded)
        assertEquals("cursor-second", loaded?.cursor?.value)
    }

    @Test
    fun `comment checkpoints remain isolated by post identity`() = runTest {
        val first = SyncCheckpoint(
            scope = WorkspaceScope("workspace-1"),
            actorId = "soacc-1",
            resource = SyncResource.COMMENTS,
            cursor = PageCursor("cursor-first"),
            lastSuccessfulAt = Instant.parse("2026-08-01T01:00:00Z"),
            postId = ExternalPostId("linkedin-post-first"),
        )
        val second = first.copy(
            cursor = PageCursor("cursor-second"),
            postId = ExternalPostId("linkedin-post-second"),
        )

        repository.save(first)
        repository.save(second)

        assertEquals(
            "cursor-first",
            repository.find(
                WorkspaceScope("workspace-1"),
                "soacc-1",
                SyncResource.COMMENTS,
                first.postId,
            )?.cursor?.value,
        )
        assertEquals(
            "cursor-second",
            repository.find(
                WorkspaceScope("workspace-1"),
                "soacc-1",
                SyncResource.COMMENTS,
                second.postId,
            )?.cursor?.value,
        )
    }

    @Test
    fun `find returns null for unknown checkpoint resource`() = runTest {
        val loaded = repository.find(
            scope = WorkspaceScope("workspace-1"),
            actorId = "soacc-1",
            resource = SyncResource.COMMENTS,
            postId = ExternalPostId("linkedin-post-unknown"),
        )

        assertNull(loaded)
    }

    @Test
    fun `findImportedPosts returns posts within range for actor`() = runTest {
        repository.upsert(
            samplePost("linkedin-cal-1").copy(publishedAt = Instant.parse("2026-08-01T10:00:00Z")),
        )
        repository.upsert(
            samplePost("linkedin-cal-2").copy(publishedAt = Instant.parse("2026-08-02T10:00:00Z")),
        )
        repository.upsert(
            samplePost("linkedin-cal-3").copy(publishedAt = Instant.parse("2026-08-05T10:00:00Z")),
        )

        val page = repository.findImportedPosts(
            SocialContentCalendarQuery(
                scope = WorkspaceScope("workspace-1"),
                from = Instant.parse("2026-08-01T00:00:00Z"),
                to = Instant.parse("2026-08-03T00:00:00Z"),
                actorId = "soacc-1",
            ),
        )

        assertEquals(2, page.items.size)
        assertEquals(
            listOf("linkedin-cal-1", "linkedin-cal-2"),
            page.items.map { it.externalPostId.value },
        )
    }

    @Test
    fun `findImportedPosts walks keyset pages without overlap and omits final cursor`() = runTest {
        val publishedAt = Instant.parse("2026-08-01T10:00:00Z")
        listOf("calendar-page-1", "calendar-page-2", "calendar-page-3").forEach { externalId ->
            repository.upsert(samplePost(externalId).copy(publishedAt = publishedAt))
        }

        val query = SocialContentCalendarQuery(
            scope = WorkspaceScope("workspace-1"),
            from = Instant.parse("2026-08-01T00:00:00Z"),
            to = Instant.parse("2026-08-02T00:00:00Z"),
            limit = 2,
        )
        val firstPage = repository.findImportedPosts(query)
        val secondPage = repository.findImportedPosts(query.copy(cursor = firstPage.nextCursor))

        assertEquals(listOf("calendar-page-1", "calendar-page-2"), firstPage.items.map { it.externalPostId.value })
        assertNotNull(firstPage.nextCursor)
        assertEquals(listOf("calendar-page-3"), secondPage.items.map { it.externalPostId.value })
        assertNull(secondPage.nextCursor)
        assertEquals(3, (firstPage.items + secondPage.items).map { it.externalPostId.value }.distinct().size)
    }

    @Test
    fun `findImportedPosts rejects a cursor bound to another workspace before querying`() = runTest {
        val cursor = SocialContentCalendarCursor(
            version = CalendarCursorVersion(CalendarCursorVersion.V1),
            workspaceId = "workspace-foreign",
            publishedAt = Instant.parse("2026-08-01T10:00:00Z"),
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "soacc-1",
            externalPostId = "calendar-page-1",
        )

        val exception = org.junit.jupiter.api.Assertions.assertThrows(InvalidSocialContentCursorException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.findImportedPosts(
                    SocialContentCalendarQuery(
                        scope = WorkspaceScope("workspace-1"),
                        from = Instant.parse("2026-08-01T00:00:00Z"),
                        to = Instant.parse("2026-08-02T00:00:00Z"),
                        cursor = PageCursor(SocialContentCalendarCursorCodec.encode(cursor)),
                    ),
                )
            }
        }

        assertEquals("Invalid social content cursor", exception.message)
    }

    @Test
    fun `findImportedPosts preserves cursor pagination for actor and lifecycle filters`() = runTest {
        val publishedAt = Instant.parse("2026-08-01T10:00:00Z")
        repository.upsert(
            samplePost("filtered-1").copy(publishedAt = publishedAt, lifecycle = PostLifecycle.PUBLISHED),
        )
        repository.upsert(
            samplePost("filtered-2").copy(publishedAt = publishedAt, lifecycle = PostLifecycle.TOMBSTONED),
        )
        repository.upsert(
            samplePost("filtered-3").copy(publishedAt = publishedAt, lifecycle = PostLifecycle.PUBLISHED),
        )
        repository.upsert(
            samplePost("other-actor").copy(
                publishedAt = publishedAt,
                actorId = "soacc-2",
                lifecycle = PostLifecycle.PUBLISHED,
            ),
        )

        val query = SocialContentCalendarQuery(
            scope = WorkspaceScope("workspace-1"),
            from = Instant.parse("2026-08-01T00:00:00Z"),
            to = Instant.parse("2026-08-02T00:00:00Z"),
            actorId = "soacc-1",
            lifecycle = PostLifecycle.PUBLISHED,
            limit = 1,
        )
        val firstPage = repository.findImportedPosts(query)
        val secondPage = repository.findImportedPosts(query.copy(cursor = firstPage.nextCursor))

        assertEquals(listOf("filtered-1"), firstPage.items.map { it.externalPostId.value })
        assertEquals(listOf("filtered-3"), secondPage.items.map { it.externalPostId.value })
        assertNull(secondPage.nextCursor)
    }

    @Test
    fun `findImportedPosts uses every tuple field as a strict boundary`() = runTest {
        val before = Instant.parse("2026-08-01T10:00:00Z")
        val boundary = Instant.parse("2026-08-01T11:00:00Z")
        val after = Instant.parse("2026-08-01T12:00:00Z")
        repository.upsert(samplePost("strict-before").copy(publishedAt = before))
        repository.upsert(samplePost("strict-boundary").copy(publishedAt = boundary))
        repository.upsert(samplePost("strict-after").copy(publishedAt = after))

        val cursor = SocialContentCalendarCursor(
            version = CalendarCursorVersion(CalendarCursorVersion.V1),
            workspaceId = "workspace-1",
            publishedAt = boundary,
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "soacc-1",
            externalPostId = "strict-boundary",
        )

        val page = repository.findImportedPosts(
            SocialContentCalendarQuery(
                scope = WorkspaceScope("workspace-1"),
                from = Instant.parse("2026-08-01T00:00:00Z"),
                to = Instant.parse("2026-08-02T00:00:00Z"),
                cursor = PageCursor(SocialContentCalendarCursorCodec.encode(cursor)),
            ),
        )

        assertEquals(listOf("strict-after"), page.items.map { it.externalPostId.value })
    }

    @Test
    fun `findImportedPosts filters by lifecycle`() = runTest {
        repository.upsert(samplePost("linkedin-life-1"))
        repository.upsert(samplePost("linkedin-life-2").copy(lifecycle = PostLifecycle.TOMBSTONED))

        val page = repository.findImportedPosts(
            SocialContentCalendarQuery(
                scope = WorkspaceScope("workspace-1"),
                from = Instant.parse("2026-08-01T00:00:00Z"),
                to = Instant.parse("2026-08-03T00:00:00Z"),
                lifecycle = PostLifecycle.PUBLISHED,
            ),
        )

        assertEquals(1, page.items.size)
        assertEquals("linkedin-life-1", page.items.single().externalPostId.value)
    }

    private fun samplePost(externalId: String): SocialPost = SocialPost(
        scope = WorkspaceScope("workspace-1"),
        provider = SocialProvider.LINKEDIN,
        actorId = "soacc-1",
        externalPostId = ExternalPostId(externalId),
        publishedAt = Instant.parse("2026-08-01T10:00:00Z"),
        body = "Hello from LinkedIn",
        origin = PostOrigin.EXTERNAL_OR_UNKNOWN,
        lifecycle = PostLifecycle.PUBLISHED,
        expiresAt = Instant.parse("2026-08-03T10:00:00Z"),
    )

    private suspend fun seedPrincipalWorkspaceAndAccount() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'local:owner@example.com', NULL, 'owner')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-1', 'Workspace 1', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        seedSocialAccount(
            accountId = "soacc-1",
            connectionId = "soconn-1",
            providerAccountId = "linkedin-account-1",
            displayName = "Yuniel",
        )
        seedSocialAccount(
            accountId = "soacc-2",
            connectionId = "soconn-2",
            providerAccountId = "linkedin-account-2",
            displayName = "Other actor",
        )
    }

    private suspend fun seedSocialAccount(
        accountId: String,
        connectionId: String,
        providerAccountId: String,
        displayName: String,
    ) {
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference
            ) VALUES (
                :connectionId, 'workspace-1', 'LINKEDIN', :connectionRef,
                'ACTIVE', '00000000-0000-0000-0000-000000000000'
            )
            """.trimIndent(),
        )
            .bind("connectionId", connectionId)
            .bind("connectionRef", "linkedin-conn-$connectionId")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (
                id, social_connection_id, workspace_id, provider, provider_account_id,
                account_type, display_name, status
            ) VALUES (
                :accountId, :connectionId, 'workspace-1', 'LINKEDIN',
                :providerAccountId, 'PERSONAL_PROFILE', :displayName, 'ACTIVE'
            )
            """.trimIndent(),
        )
            .bind("accountId", accountId)
            .bind("connectionId", connectionId)
            .bind("providerAccountId", providerAccountId)
            .bind("displayName", displayName)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("social_content_repositories")
    }
}
