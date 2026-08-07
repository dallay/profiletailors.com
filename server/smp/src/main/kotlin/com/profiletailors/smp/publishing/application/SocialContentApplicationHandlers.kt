package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialContentAccessDenial
import com.profiletailors.smp.publishing.domain.SocialContentAccessDeniedException
import com.profiletailors.smp.publishing.domain.SocialContentActorRepository
import com.profiletailors.smp.publishing.domain.SocialContentCalendarQuery
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialContentReader
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock
import java.time.Instant

/** Starts a bounded import for one workspace-owned social-content actor. */
data class SocialContentSyncCommand(val actorId: String) : CommandWithResult<SocialContentSyncResult>

enum class SocialContentSyncStatus { COMPLETED }

data class SocialContentSyncResult(
    val actorId: String,
    val importedCount: Int,
    val highWaterMark: Instant?,
    val status: SocialContentSyncStatus,
)

class SocialContentActorNotFoundException(val actorId: String) :
    RuntimeException("Social content actor '$actorId' was not found in the active workspace.")

@Service
class SocialContentSyncCommandHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val actorRepository: SocialContentActorRepository? = null,
    private val syncHandler: SocialContentSyncHandler? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val featureGates: SocialContentFeatureGates = SocialContentFeatureGates(),
    private val socialAccountRepository: SocialAccountRepository? = null,
) : CommandWithResultHandler<SocialContentSyncCommand, SocialContentSyncResult> {
    override suspend fun handle(command: SocialContentSyncCommand): SocialContentSyncResult {
        val scope = currentWorkspaceScope(resourceContextProvider)
        if (!featureGates.importEnabled) {
            throw SocialContentAccessDeniedException(SocialContentAccessDenial.OPERATION_DISABLED)
        }
        val (actorRepository, syncHandler) = requireSyncDependencies()
        val actor = actorRepository.findByWorkspaceAndId(scope, command.actorId)
            ?: actorNotFound(scope, command.actorId)
        val imported = syncHandler.importPosts(actor, clock.instant())
        return SocialContentSyncResult(
            actorId = actor.id,
            importedCount = imported.items.size,
            highWaterMark = imported.highWaterMark,
            status = SocialContentSyncStatus.COMPLETED,
        )
    }

    private fun requireSyncDependencies(): Pair<SocialContentActorRepository, SocialContentSyncHandler> = Pair(
        actorRepository ?: deniedEvidenceMissing(),
        syncHandler ?: deniedEvidenceMissing(),
    )

    private suspend fun actorNotFound(scope: WorkspaceScope, actorId: String): Nothing {
        val account = socialAccountRepository?.findByWorkspaceAndId(scope.value, actorId)
        if (account?.kind == SocialAccountKind.PERSONAL_PROFILE) {
            throw SocialContentAccessDeniedException(SocialContentAccessDenial.ORGANIZATION_PAGE_REQUIRED)
        }
        throw SocialContentActorNotFoundException(actorId)
    }

    private fun deniedEvidenceMissing(): Nothing =
        throw SocialContentAccessDeniedException(SocialContentAccessDenial.EVIDENCE_MISSING)
}

data class SocialContentPostQuery(val externalPostId: String) : Query<SocialPost>

class SocialContentPostNotFoundException(val externalPostId: String) :
    RuntimeException("Social content post '$externalPostId' was not found in the active workspace.")

class SocialContentPostIsolationException :
    IllegalStateException("Social content post result crossed workspace boundary.")

@Service
class SocialContentPostQueryHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val reader: SocialContentReader,
) : QueryHandler<SocialContentPostQuery, SocialPost> {
    override suspend fun handle(query: SocialContentPostQuery): SocialPost {
        val scope = currentWorkspaceScope(resourceContextProvider)
        val post = reader.findPost(scope, ExternalPostId(query.externalPostId))
            ?: throw SocialContentPostNotFoundException(query.externalPostId)
        if (post.scope != scope) {
            throw SocialContentPostIsolationException()
        }
        return post
    }
}

data class WorkspaceSocialContentCalendarQuery(
    val from: Instant,
    val to: Instant,
    val actorId: String? = null,
    val lifecycle: PostLifecycle? = null,
    val cursor: PageCursor? = null,
    val limit: Int = 50,
) : Query<SocialContentCalendarResponse>

@Service
class WorkspaceSocialContentCalendarQueryHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val calendarQueryHandler: SocialContentCalendarQueryHandler,
) : QueryHandler<WorkspaceSocialContentCalendarQuery, SocialContentCalendarResponse> {
    override suspend fun handle(query: WorkspaceSocialContentCalendarQuery): SocialContentCalendarResponse =
        calendarQueryHandler.handle(
            SocialContentCalendarQuery(
                scope = currentWorkspaceScope(resourceContextProvider),
                from = query.from,
                to = query.to,
                actorId = query.actorId,
                lifecycle = query.lifecycle,
                cursor = query.cursor,
                limit = query.limit,
            ),
        )
}

typealias SocialContentCalendarRequest = WorkspaceSocialContentCalendarQuery

private fun currentWorkspaceScope(resourceContextProvider: ResourceContextProvider): WorkspaceScope =
    WorkspaceScope(requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId))
