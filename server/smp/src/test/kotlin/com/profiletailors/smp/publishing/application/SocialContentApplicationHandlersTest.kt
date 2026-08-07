package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.DependencyProvider
import com.profiletailors.common.domain.bus.MediatorBuilder
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorRepository
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCalendarQuery
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentReader
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import com.profiletailors.smp.publishing.infrastructure.fake.FakeSocialContentPostRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@Suppress("UNCHECKED_CAST")
class SocialContentApplicationHandlersTest {
    private val workspace = WorkspaceScope("workspace-1")
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val actor = SocialContentActor(
        id = "page-1",
        scope = workspace,
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social"),
    )
    private val contextProvider = FixedResourceContextProvider(workspace.value)

    @Test
    fun `application handlers are Spring discoverable Mediator handlers`() {
        listOf(
            SocialContentSyncCommandHandler::class.java,
            SocialContentPostQueryHandler::class.java,
            WorkspaceSocialContentCalendarQueryHandler::class.java,
        ).forEach { handlerType ->
            check(handlerType.isAnnotationPresent(Service::class.java)) {
                "${handlerType.simpleName} must be annotated with the project Service marker"
            }
        }
        check(CommandWithResultHandler::class.java.isAssignableFrom(SocialContentSyncCommandHandler::class.java))
        check(QueryHandler::class.java.isAssignableFrom(SocialContentPostQueryHandler::class.java))
        check(QueryHandler::class.java.isAssignableFrom(WorkspaceSocialContentCalendarQueryHandler::class.java))
    }

    @Test
    fun `mediator registers sync detail and calendar handlers`() = runTest {
        val mediator = MediatorBuilder(
            HandlerDependencyProvider(
                listOf(
                    SocialContentSyncCommandHandler(
                        resourceContextProvider = contextProvider,
                        actorRepository = RecordingActorRepository(actor),
                        syncHandler = syncHandler(RecordingCheckpointRepository()),
                        clock = Clock.fixed(now, ZoneOffset.UTC),
                        featureGates = SocialContentFeatureGates(importEnabled = true),
                    ),
                    SocialContentPostQueryHandler(
                        resourceContextProvider = contextProvider,
                        reader = RecordingReader(null),
                    ),
                    WorkspaceSocialContentCalendarQueryHandler(
                        resourceContextProvider = contextProvider,
                        calendarQueryHandler = SocialContentCalendarQueryHandler(RecordingReader(null)),
                    ),
                ),
            ),
        ).build()

        mediator.send(SocialContentSyncCommand(actor.id)).actorId shouldBe actor.id
        shouldThrow<SocialContentPostNotFoundException> {
            mediator.send(SocialContentPostQuery("missing-post"))
        }
        mediator.send(
            WorkspaceSocialContentCalendarQuery(
                from = now.minusSeconds(60),
                to = now.plusSeconds(60),
            ),
        ).items shouldBe emptyList()
    }

    @Test
    fun `sync dispatches the current clock and returns imported count and high water mark`() = runTest {
        val checkpointRepository = RecordingCheckpointRepository()
        val syncHandler = syncHandler(checkpointRepository)
        val handler = SocialContentSyncCommandHandler(
            resourceContextProvider = contextProvider,
            actorRepository = RecordingActorRepository(actor),
            syncHandler = syncHandler,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            featureGates = SocialContentFeatureGates(importEnabled = true),
        )

        val result = handler.handle(SocialContentSyncCommand(actor.id))

        result shouldBe SocialContentSyncResult(
            actorId = actor.id,
            importedCount = 1,
            highWaterMark = now,
            status = SocialContentSyncStatus.COMPLETED,
        )
        checkpointRepository.saved.single().lastSuccessfulAt shouldBe now
    }

    @Test
    fun `sync blocks when the actor is missing`() = runTest {
        val handler = SocialContentSyncCommandHandler(
            resourceContextProvider = contextProvider,
            actorRepository = RecordingActorRepository(null),
            syncHandler = syncHandler(RecordingCheckpointRepository()),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            featureGates = SocialContentFeatureGates(importEnabled = true),
        )

        val exception = shouldThrow<SocialContentActorNotFoundException> {
            handler.handle(SocialContentSyncCommand("missing-page"))
        }

        exception.actorId shouldBe "missing-page"
    }

    @Test
    fun `post detail returns a post from the current workspace`() = runTest {
        val post = importedPost(workspace, actor, "post-1")
        val handler = SocialContentPostQueryHandler(
            resourceContextProvider = contextProvider,
            reader = RecordingReader(post),
        )

        handler.handle(SocialContentPostQuery(post.externalPostId.value)) shouldBe post
    }

    @Test
    fun `post detail reports a typed not found error when the reader has no post`() = runTest {
        val handler = SocialContentPostQueryHandler(
            resourceContextProvider = contextProvider,
            reader = RecordingReader(null),
        )

        val exception = shouldThrow<SocialContentPostNotFoundException> {
            handler.handle(SocialContentPostQuery("missing-post"))
        }

        exception.externalPostId shouldBe "missing-post"
    }

    @Test
    fun `post detail rejects a foreign post with a typed isolation error`() = runTest {
        val foreignWorkspace = WorkspaceScope("workspace-2")
        val foreignActor = actor.copy(scope = foreignWorkspace)
        val handler = SocialContentPostQueryHandler(
            resourceContextProvider = contextProvider,
            reader = RecordingReader(importedPost(foreignWorkspace, foreignActor, "foreign-post")),
        )

        shouldThrow<SocialContentPostIsolationException> {
            handler.handle(SocialContentPostQuery("foreign-post"))
        }
    }

    @Test
    fun `calendar wrapper derives workspace and preserves filters and opaque cursor`() = runTest {
        val reader = RecordingReader(null)
        val calendarHandler = SocialContentCalendarQueryHandler(reader)
        val handler = WorkspaceSocialContentCalendarQueryHandler(contextProvider, calendarHandler)
        val from = now.minusSeconds(60)
        val to = now.plusSeconds(60)
        val cursor = PageCursor("opaque.cursor")
        val request = SocialContentCalendarRequest(
            from = from,
            to = to,
            actorId = actor.id,
            lifecycle = PostLifecycle.PUBLISHED,
            cursor = cursor,
            limit = 25,
        )

        handler.handle(request)

        reader.queries.single() shouldBe SocialContentCalendarQuery(
            scope = workspace,
            from = from,
            to = to,
            actorId = actor.id,
            lifecycle = PostLifecycle.PUBLISHED,
            cursor = cursor,
            limit = 25,
        )
    }

    private fun syncHandler(checkpointRepository: RecordingCheckpointRepository): SocialContentSyncHandler {
        val post = importedPost(workspace, actor, "post-1")
        return SocialContentSyncHandler(
            provider = SinglePageProvider(post),
            postRepository = FakeSocialContentPostRepository(),
            checkpointRepository = checkpointRepository,
            capabilityResolver = DefaultCapabilityResolver(SocialContentFeatureGates(importEnabled = true)),
            retention = RetentionRequirements(Duration.ofHours(48), Duration.ofHours(24)),
            gates = SocialContentFeatureGates(importEnabled = true),
            batchWriter = RecordingBatchWriter(checkpointRepository = checkpointRepository),
            backoff = { _, _ -> },
        )
    }

    private fun importedPost(scope: WorkspaceScope, actor: SocialContentActor, id: String): SocialPost =
        SocialPost.imported(
            scope = scope,
            actor = actor,
            externalPostId = ExternalPostId(id),
            publishedAt = now,
            now = now,
        )

    private class HandlerDependencyProvider(private val handlers: List<Any>) : DependencyProvider {
        override fun <T> getSingleInstanceOf(clazz: Class<T>): T = handlers.single { clazz.isInstance(it) } as T

        override fun <T> getSubTypesOf(clazz: Class<T>): Collection<Class<T>> =
            handlers.filter { clazz.isAssignableFrom(it.javaClass) }.map { it.javaClass as Class<T> }
    }

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
    }

    private class RecordingActorRepository(private val result: SocialContentActor?) : SocialContentActorRepository {
        override suspend fun findByWorkspaceAndId(scope: WorkspaceScope, actorId: String): SocialContentActor? =
            result?.takeIf { it.scope == scope && it.id == actorId }

        override suspend fun findByWorkspaceExternalId(
            scope: WorkspaceScope,
            provider: SocialProvider,
            externalActorId: ProviderActorId,
        ): SocialContentActor? = null

        override suspend fun upsert(actor: SocialContentActor): SocialContentActor = actor
    }

    private class RecordingReader(private val result: SocialPost?) : SocialContentReader {
        val queries = mutableListOf<SocialContentCalendarQuery>()

        override suspend fun findImportedPosts(query: SocialContentCalendarQuery): SocialContentPage<SocialPost> {
            queries += query
            return SocialContentPage(emptyList(), null)
        }

        override suspend fun findPost(scope: WorkspaceScope, externalPostId: ExternalPostId): SocialPost? = result
    }

    private class RecordingBatchWriter(private val checkpointRepository: RecordingCheckpointRepository? = null) :
        SocialContentBatchWriter {
        override suspend fun persist(
            posts: Collection<SocialPost>,
            tombstoneIds: Set<ExternalPostId>,
            checkpoint: SyncCheckpoint,
        ) {
            checkpointRepository?.save(checkpoint)
        }
    }

    private class RecordingCheckpointRepository : SocialContentCheckpointRepository {
        val saved = mutableListOf<SyncCheckpoint>()

        override suspend fun find(
            scope: WorkspaceScope,
            actorId: String,
            resource: SyncResource,
            postId: ExternalPostId?,
        ): SyncCheckpoint? = null

        override suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint {
            saved += checkpoint
            return checkpoint
        }
    }

    private class SinglePageProvider(private val post: SocialPost) : SocialContentProvider {
        override suspend fun discoverActors(scope: WorkspaceScope, connectionId: String) =
            emptyList<com.profiletailors.smp.publishing.domain.SocialContentActorCandidate>()

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = SocialContentPage(listOf(post), null)

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: PageCursor?,
            pageSize: Int,
        ) = SocialContentPage<com.profiletailors.smp.publishing.domain.SocialComment>(emptyList(), null)

        override suspend fun reply(
            actor: SocialContentActor,
            parent: com.profiletailors.smp.publishing.domain.SocialComment,
            body: String,
            idempotencyKey: com.profiletailors.smp.publishing.domain.IdempotencyKey,
        ) = parent
    }
}
