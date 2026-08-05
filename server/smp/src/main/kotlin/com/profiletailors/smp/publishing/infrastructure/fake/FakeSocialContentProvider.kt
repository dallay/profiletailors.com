package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorCandidate
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Controlled failures the fake provider will inject before returning its next read. */
enum class FakeProviderFailure {
    UNAUTHORIZED,
    ROLE_FORBIDDEN,
    RATE_LIMITED,
}

/** Static fixture inputs handed to the fake provider at construction time. */
data class FakeSocialContentFixtures(
    val actorCandidates: List<SocialContentActorCandidate> = emptyList(),
    val posts: Map<String, List<SocialPost>> = emptyMap(),
    val comments: Map<String, List<SocialComment>> = emptyMap(),
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val failures: ArrayDeque<FakeProviderFailure> = ArrayDeque(),
) {
    init {
        require(pageSize in 1..MAX_PAGE_SIZE) { "Fake page size must be between 1 and $MAX_PAGE_SIZE." }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 100
        const val MAX_PAGE_SIZE = 100
    }
}

/** Discriminated record of operations sent to the fake provider during a test run. */
sealed interface FakeProviderCall {
    data class DiscoverActors(val scope: String, val connectionId: String) : FakeProviderCall
    data class FetchPosts(val actorId: String, val cursor: String?) : FakeProviderCall
    data class FetchComments(val actorId: String, val postId: String) : FakeProviderCall
    data class Reply(val actorId: String, val parentCommentId: String, val idempotencyKey: String) : FakeProviderCall
}

/** In-memory [SocialContentPostRepository] keyed by workspace + provider + actor + external post id. */
class FakeSocialContentPostRepository : SocialContentPostRepository {
    private val records = linkedMapOf<PostIdentity, SocialPost>()

    val all: List<SocialPost> get() = records.values.toList()

    /**
     * Inserts or replaces a stored social post.
     *
     * @param post The social post to store.
     * @return The stored social post.
     */
    override suspend fun upsert(post: SocialPost): SocialPost {
        records[PostIdentity(post.scope, post.provider, post.actorId, post.externalPostId)] = post
        return post
    }

    /**
     * Finds a stored social post by workspace, provider, actor, and external post identifier.
     *
     * @param scope The workspace scope.
     * @param provider The social provider.
     * @param actorId The provider's actor identifier.
     * @param externalPostId The provider's post identifier.
     * @return The matching social post, or `null` if no post is stored.
     */
    override suspend fun findByWorkspaceAndExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        externalPostId: com.profiletailors.smp.publishing.domain.ExternalPostId,
    ): SocialPost? = records[PostIdentity(scope, provider, actorId, externalPostId)]

    /**
     * Tombstones stored posts for an actor that are absent from the supplied external IDs.
     *
     * @param scope The workspace scope containing the posts.
     * @param provider The social provider associated with the posts.
     * @param actorId The external actor identifier.
     * @param seenExternalIds The external post IDs observed for the actor.
     */
    override suspend fun tombstoneMissing(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        seenExternalIds: Set<com.profiletailors.smp.publishing.domain.ExternalPostId>,
    ) {
        records.replaceAll { key, post ->
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

    private data class PostIdentity(
        val scope: WorkspaceScope,
        val provider: SocialProvider,
        val actorId: String,
        val externalPostId: com.profiletailors.smp.publishing.domain.ExternalPostId,
    )
}

/** In-memory [SocialContentProvider] used by tests; records every call for later assertions. */
class FakeSocialContentProvider(private val fixtures: FakeSocialContentFixtures) : SocialContentProvider {
    private val mutex = Mutex()
    private val recordedCalls = mutableListOf<FakeProviderCall>()
    private val recordedReplyCalls = mutableListOf<FakeProviderCall.Reply>()

    val calls: List<FakeProviderCall> get() = recordedCalls.toList()
    val replyCalls: List<FakeProviderCall.Reply> get() = recordedReplyCalls.toList()

    /**
     * Discovers the configured social content actor candidates.
     *
     * @param scope The workspace scope for the discovery request.
     * @param connectionId The provider connection identifier.
     * @return The configured actor candidates.
     */
    override suspend fun discoverActors(
        scope: WorkspaceScope,
        connectionId: String,
    ): List<SocialContentActorCandidate> = mutex.withLock {
        recordedCalls += FakeProviderCall.DiscoverActors(scope.value, connectionId)
        fixtures.actorCandidates.toList()
    }

    /**
     * Retrieves a paginated set of posts for an actor.
     *
     * @param actor The actor whose posts are retrieved.
     * @param cursor The cursor identifying the starting position, or `null` to start from the beginning.
     * @return A page of posts and a cursor for the next page when more posts are available.
     * @throws SocialContentProviderException If a configured provider failure occurs.
     */
    override suspend fun fetchPosts(actor: SocialContentActor, cursor: PageCursor?): SocialContentPage<SocialPost> =
        mutex.withLock {
            recordedCalls += FakeProviderCall.FetchPosts(actor.id, cursor?.value)
            fixtures.failures.removeFirstOrNull()?.let {
                throw SocialContentProviderException(
                    when (it) {
                        FakeProviderFailure.UNAUTHORIZED -> SocialContentProviderFailure.UNAUTHORIZED
                        FakeProviderFailure.ROLE_FORBIDDEN -> SocialContentProviderFailure.ROLE_FORBIDDEN
                        FakeProviderFailure.RATE_LIMITED -> SocialContentProviderFailure.RATE_LIMITED
                    },
                )
            }
            val offset = cursor?.value?.toIntOrNull() ?: 0
            val posts = fixtures.posts[actor.id].orEmpty()
            val page = posts.drop(offset).take(fixtures.pageSize)
            SocialContentPage(page, (offset + page.size).takeIf { it < posts.size }?.toString()?.let(::PageCursor))
        }

    /**
     * Retrieves the configured comments for a post.
     *
     * @param actor The actor associated with the post.
     * @param post The post whose comments are retrieved.
     * @return A page containing the configured comments and no subsequent cursor.
     */
    override suspend fun fetchComments(actor: SocialContentActor, post: SocialPost): SocialContentPage<SocialComment> =
        mutex.withLock {
            recordedCalls += FakeProviderCall.FetchComments(actor.id, post.externalPostId.value)
            SocialContentPage(fixtures.comments[post.externalPostId.value].orEmpty(), null)
        }

    /**
     * Creates a fake reply to a parent comment and records the provider call.
     *
     * @param actor The actor creating the reply.
     * @param parent The comment receiving the reply.
     * @param body The reply text.
     * @param idempotencyKey The key used to identify the reply.
     * @return A comment representing the created reply.
     */
    override suspend fun reply(
        actor: SocialContentActor,
        parent: SocialComment,
        body: String,
        idempotencyKey: IdempotencyKey,
    ): SocialComment = mutex.withLock {
        val call = FakeProviderCall.Reply(actor.id, parent.externalCommentId.value, idempotencyKey.value)
        recordedCalls += call
        recordedReplyCalls += call
        parent.copy(
            externalCommentId = ExternalCommentId("fake-reply-${idempotencyKey.value}"),
            parentExternalCommentId = parent.externalCommentId,
            actorExternalId = actor.externalActorId,
            body = body,
        )
    }
}
