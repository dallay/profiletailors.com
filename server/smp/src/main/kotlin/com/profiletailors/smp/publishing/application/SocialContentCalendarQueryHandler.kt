package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.PostOrigin
import com.profiletailors.smp.publishing.domain.SocialContentCalendarQuery
import com.profiletailors.smp.publishing.domain.SocialContentReader
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import java.time.Instant

data class SocialContentCalendarItem(
    val externalPostId: ExternalPostId,
    val scope: WorkspaceScope,
    val provider: SocialProvider,
    val actorId: String,
    val scheduledAt: Instant?,
    val publishedAt: Instant,
    val origin: PostOrigin,
    val lifecycle: PostLifecycle,
    val mutationAllowed: Boolean,
)

data class SocialContentCalendarResponse(val items: List<SocialContentCalendarItem>, val nextCursor: PageCursor?)

class SocialContentCalendarIsolationException :
    IllegalStateException("Social content calendar result crossed workspace boundary.")

@Service
class SocialContentCalendarQueryHandler(private val reader: SocialContentReader) {
    suspend fun handle(query: SocialContentCalendarQuery): SocialContentCalendarResponse {
        val page = reader.findImportedPosts(query)
        if (page.items.any { it.scope != query.scope }) {
            throw SocialContentCalendarIsolationException()
        }
        return SocialContentCalendarResponse(
            items = page.items.map { it.toCalendarItem() },
            nextCursor = page.nextCursor,
        )
    }

    private fun SocialPost.toCalendarItem() = SocialContentCalendarItem(
        externalPostId = externalPostId,
        scope = scope,
        provider = provider,
        actorId = actorId,
        scheduledAt = null,
        publishedAt = publishedAt,
        origin = origin,
        lifecycle = lifecycle,
        mutationAllowed = mutationAllowed,
    )
}
