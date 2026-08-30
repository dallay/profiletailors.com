package com.profiletailors.smp.bdd

import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.application.SocialContentSyncCommandHandler
import com.profiletailors.smp.publishing.application.SocialContentSyncHandler
import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorRepository
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidence
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidenceRepository
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialContentReader
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialAccountRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.time.Instant

@TestConfiguration(proxyBeanMethods = false)
class SocialContentBddTestConfiguration {
    @Bean
    fun socialContentBddState(): SocialContentBddState = SocialContentBddState()

    @Bean
    @Primary
    fun socialContentBddActorRepository(state: SocialContentBddState): SocialContentActorRepository = state.actors

    @Bean("socialContentAccountRepository")
    fun socialContentBddSocialAccountRepository(state: SocialContentBddState): SocialAccountRepository =
        state.socialContentAccountRepository

    @Bean
    @Primary
    fun socialContentBddLegacySocialAccountRepository(
        repository: R2dbcSocialAccountRepository,
    ): SocialAccountRepository = repository

    @Bean("socialContentSyncCommandHandler")
    @Primary
    fun socialContentBddSyncCommandHandler(
        resourceContextProvider: ResourceContextProvider,
        actorRepository: SocialContentActorRepository,
        syncHandler: SocialContentSyncHandler,
        @Qualifier("socialContentFeatureGates") featureGates: SocialContentFeatureGates,
        @Qualifier("socialContentAccountRepository") socialAccountRepository: SocialAccountRepository,
    ): SocialContentSyncCommandHandler = SocialContentSyncCommandHandler(
        resourceContextProvider = resourceContextProvider,
        actorRepository = actorRepository,
        syncHandler = syncHandler,
        featureGates = featureGates,
        socialAccountRepository = socialAccountRepository,
    )

    @Bean
    @Primary
    fun socialContentBddApprovalEvidenceRepository(
        state: SocialContentBddState,
    ): SocialContentApprovalEvidenceRepository = state.approvalEvidence

    @Bean
    @Primary
    fun socialContentBddProvider(state: SocialContentBddState): SocialContentProvider = state.provider

    /**
     * Provides the social content reader configured for BDD tests.
     *
     * @param state Mutable BDD state containing the content store.
     * @param repositoryProvider Provider for optional production social content repositories.
     * @return The configured social content reader.
     */
    @Bean
    @Primary
    fun socialContentBddReader(state: SocialContentBddState): SocialContentReader = state.content

    /**
     * Provides the feature gates used by social-content BDD tests.
     *
     * @param state The shared BDD test state containing the feature gates.
     * @return The social-content feature gates.
     */
    @Bean("socialContentFeatureGates")
    @Primary
    fun socialContentBddFeatureGates(state: SocialContentBddState): SocialContentFeatureGates = state.gates

    @Bean
    @Primary
    fun socialContentBddCapabilityResolver(gates: SocialContentFeatureGates) = DefaultCapabilityResolver(gates)

    @Bean
    @Primary
    fun socialContentBddRetentionRequirements(): RetentionRequirements = RetentionRequirements(
        activityTtl = Duration.ofHours(48),
        commenterProfileTtl = Duration.ofHours(24),
    )

    @Bean
    @Primary
    fun socialContentBddSyncHandler(
        state: SocialContentBddState,
        capabilityResolver: DefaultCapabilityResolver,
        retention: RetentionRequirements,
        gates: SocialContentFeatureGates,
    ): SocialContentSyncHandler = SocialContentSyncHandler(
        provider = state.provider,
        postRepository = state.content,
        checkpointRepository = state.content,
        capabilityResolver = capabilityResolver,
        retention = retention,
        gates = gates,
        batchWriter = state.content,
    )
}

class SocialContentBddState {
    val actors = BddActorRepository()
    val approvalEvidence = BddApprovalEvidenceRepository()
    val socialAccounts = mutableMapOf<Pair<String, String>, SocialAccount>()
    val socialContentAccountRepository: SocialAccountRepository = object : SocialAccountRepository {
        override suspend fun upsert(account: SocialAccount): SocialAccount {
            socialAccounts[account.workspaceId to account.id] = account
            return account
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? =
            socialAccounts[workspaceId to accountId]

        override suspend fun findFirstActiveByWorkspace(workspaceId: String): SocialAccount? =
            socialAccounts.values.firstOrNull {
                it.workspaceId == workspaceId && it.status == SocialConnectionStatus.ACTIVE
            }
    }
    val provider = BddProvider()
    val content = BddContentStore()
    val gates = SocialContentFeatureGates()

    fun reset() {
        actors.clear()
        approvalEvidence.clear()
        socialAccounts.clear()
        provider.reset()
        content.clear()
        gates.discoveryEnabled = false
        gates.importEnabled = false
        gates.inboxEnabled = false
        gates.repliesEnabled = false
    }
}

class BddActorRepository : SocialContentActorRepository {
    private val records = mutableListOf<SocialContentActor>()

    override suspend fun findByWorkspaceAndId(scope: WorkspaceScope, actorId: String): SocialContentActor? =
        records.firstOrNull { it.scope == scope && it.id == actorId }

    override suspend fun findByWorkspaceExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        externalActorId: com.profiletailors.smp.publishing.domain.ProviderActorId,
    ): SocialContentActor? = records.firstOrNull {
        it.scope == scope && it.provider == provider && it.externalActorId == externalActorId
    }

    override suspend fun upsert(actor: SocialContentActor): SocialContentActor {
        records.removeIf { it.scope == actor.scope && it.id == actor.id }
        records += actor
        return actor
    }

    fun put(actor: SocialContentActor) {
        records.removeIf { it.scope == actor.scope && it.id == actor.id }
        records += actor
    }

    fun clear() = records.clear()
}

class BddApprovalEvidenceRepository : SocialContentApprovalEvidenceRepository {
    private val records = mutableMapOf<Pair<String, String>, SocialContentApprovalEvidence>()

    override suspend fun findByWorkspaceAndAccount(
        workspaceId: String,
        socialAccountId: String,
    ): SocialContentApprovalEvidence? = records[workspaceId to socialAccountId]

    fun put(evidence: SocialContentApprovalEvidence) {
        records[evidence.workspaceId to evidence.socialAccountId] = evidence
    }

    fun clear() = records.clear()
}

class BddProvider : SocialContentProvider {
    private var callCount = 0

    fun calls(): Int = callCount

    fun reset() {
        callCount = 0
    }

    override suspend fun fetchPosts(
        actor: SocialContentActor,
        cursor: PageCursor?,
        pageSize: Int,
    ): SocialContentPage<SocialPost> {
        callCount++
        return SocialContentPage(emptyList(), null)
    }

    override suspend fun fetchComments(
        actor: SocialContentActor,
        post: SocialPost,
        cursor: PageCursor?,
        pageSize: Int,
    ): SocialContentPage<SocialComment> = SocialContentPage(emptyList(), null)

    override suspend fun reply(
        actor: SocialContentActor,
        parent: SocialComment,
        body: String,
        idempotencyKey: com.profiletailors.smp.publishing.domain.IdempotencyKey,
    ): SocialComment = throw SocialContentProviderException(
        failure = SocialContentProviderFailure.PROVIDER_UNAVAILABLE,
    )
}

class BddContentStore :
    SocialContentPostRepository,
    SocialContentCheckpointRepository,
    SocialContentReader,
    SocialContentBatchWriter {
    private val posts = linkedMapOf<PostKey, SocialPost>()
    private val checkpoints = linkedMapOf<CheckpointKey, SyncCheckpoint>()
    private var readerCallCount = 0
    private var lastCursor: PageCursor? = null

    fun seed(post: SocialPost) {
        posts[PostKey(post)] = post
    }

    fun readerCalls(): Int = readerCallCount

    fun lastCursor(): PageCursor? = lastCursor

    /**
     * Clears stored posts and checkpoints and resets calendar query state.
     */
    fun clear() {
        posts.clear()
        checkpoints.clear()
        readerCallCount = 0
        lastCursor = null
    }

    /**
     * Stores a social post and returns it.
     *
     * @param post The post to store.
     * @return The stored post.
     */
    override suspend fun upsert(post: SocialPost): SocialPost {
        posts[PostKey(post)] = post
        return post
    }

    override suspend fun findByWorkspaceAndExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        externalPostId: ExternalPostId,
    ): SocialPost? = posts[PostKey(scope, provider, actorId, externalPostId)]

    override suspend fun tombstoneMissing(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        seenExternalIds: Set<ExternalPostId>,
    ) {
        posts.replaceAll { key, post ->
            if (
                key.scope == scope &&
                key.provider == provider &&
                key.actorId == actorId &&
                key.externalPostId !in seenExternalIds
            ) {
                post.tombstone(post.expiresAt)
            } else {
                post
            }
        }
    }

    override suspend fun find(
        scope: WorkspaceScope,
        actorId: String,
        resource: SyncResource,
        postId: ExternalPostId?,
    ): SyncCheckpoint? = checkpoints[CheckpointKey(scope, actorId, resource, postId)]

    override suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint {
        checkpoints[CheckpointKey(checkpoint.scope, checkpoint.actorId, checkpoint.resource, checkpoint.postId)] =
            checkpoint
        return checkpoint
    }

    /**
     * Finds imported posts matching the calendar query.
     *
     * @param query The scope, date range, optional actor and lifecycle filters, and page size.
     * @return A page containing matching posts and the latest publication timestamp as its marker.
     * @throws IllegalStateException If production-reader mode is enabled without an available reader.
     */
    override suspend fun findImportedPosts(
        query: com.profiletailors.smp.publishing.domain.SocialContentCalendarQuery,
    ): SocialContentPage<SocialPost> {
        readerCallCount++
        lastCursor = query.cursor
        val items = posts.values
            .filter { post ->
                post.scope == query.scope &&
                    post.publishedAt >= query.from &&
                    post.publishedAt < query.to &&
                    (query.actorId == null || post.actorId == query.actorId) &&
                    (query.lifecycle == null || post.lifecycle == query.lifecycle)
            }
            .sortedWith(compareBy<SocialPost> { it.publishedAt }.thenBy { it.externalPostId.value })
            .take(query.limit)
        return SocialContentPage(items, null, items.maxOfOrNull { it.publishedAt })
    }

    override suspend fun findPost(scope: WorkspaceScope, externalPostId: ExternalPostId): SocialPost? =
        posts.values.firstOrNull { it.scope == scope && it.externalPostId == externalPostId }

    override suspend fun persist(
        posts: Collection<SocialPost>,
        tombstoneIds: Set<ExternalPostId>,
        checkpoint: SyncCheckpoint,
    ) {
        posts.forEach { upsert(it) }
        if (tombstoneIds.isNotEmpty()) {
            tombstoneMissing(
                scope = checkpoint.scope,
                provider = checkpoint.provider,
                actorId = checkpoint.actorId,
                seenExternalIds = tombstoneIds,
            )
        }
        save(checkpoint)
    }

    private data class PostKey(
        val scope: WorkspaceScope,
        val provider: SocialProvider,
        val actorId: String,
        val externalPostId: ExternalPostId,
    ) {
        constructor(post: SocialPost) : this(post.scope, post.provider, post.actorId, post.externalPostId)
    }

    private data class CheckpointKey(
        val scope: WorkspaceScope,
        val actorId: String,
        val resource: SyncResource,
        val postId: ExternalPostId?,
    )
}

fun bddOrganizationPageActor(workspaceId: String, actorId: String = "page-1", socialAccountId: String = actorId) =
    SocialContentActor(
        id = actorId,
        socialAccountId = socialAccountId,
        scope = WorkspaceScope(workspaceId),
        connectionId = "connection-$workspaceId",
        provider = SocialProvider.LINKEDIN,
        externalActorId = com.profiletailors.smp.publishing.domain.ProviderActorId("urn:li:organization:$actorId"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors Page",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social", "r_organization_social_feed"),
    )

fun bddImportedPost(workspaceId: String, externalPostId: String = "post-1") = SocialPost(
    scope = WorkspaceScope(workspaceId),
    provider = SocialProvider.LINKEDIN,
    actorId = "page-1",
    externalPostId = ExternalPostId(externalPostId),
    publishedAt = Instant.parse("2026-08-02T12:00:00Z"),
    body = "Imported page content",
    origin = com.profiletailors.smp.publishing.domain.PostOrigin.EXTERNAL_OR_UNKNOWN,
    lifecycle = PostLifecycle.PUBLISHED,
    expiresAt = Instant.parse("2026-08-04T12:00:00Z"),
)
