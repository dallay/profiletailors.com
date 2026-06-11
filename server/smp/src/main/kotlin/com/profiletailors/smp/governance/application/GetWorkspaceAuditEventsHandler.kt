package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.governance.application.AuditEventCursorCodec
import com.profiletailors.smp.governance.application.AuditEventPage
import com.profiletailors.smp.governance.application.AuditEventReader
import com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsQuery
import com.profiletailors.smp.governance.application.WorkspaceAuditEventsResponse

private val AUDIT_READ_PERMISSION: PermissionKey = PermissionKey.of("workspace", "audit", "read")

@Service
internal class GetWorkspaceAuditEventsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val auditEventReader: AuditEventReader,
    private val workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
) : QueryHandler<GetWorkspaceAuditEventsQuery, WorkspaceAuditEventsResponse> {
    override suspend fun handle(query: GetWorkspaceAuditEventsQuery): WorkspaceAuditEventsResponse {
        val decision = workspaceAuthorizationDecider.decideDetailed(requiredPermission = AUDIT_READ_PERMISSION)
        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(
                decision = decision,
                requiredPermission = AUDIT_READ_PERMISSION,
            )
        }

        val resourceContext = resourceContextProvider.require()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        val normalizedCursor = query.cursor?.let(AuditEventCursorCodec::decode)
        val normalizedLimit = query.limit.coerceIn(1, 200)
        val items = auditEventReader.readWorkspaceEvents(
            workspaceId = workspaceId,
            targetType = query.targetType,
            action = query.action,
            eventType = query.eventType,
            actorPrincipalId = query.actorPrincipalId,
            createdAfter = query.createdAfter,
            createdBefore = query.createdBefore,
            cursor = normalizedCursor,
            limit = normalizedLimit + 1,
        )
        val visibleItems = items.take(normalizedLimit)
        val nextCursor = if (items.size > normalizedLimit && visibleItems.isNotEmpty()) {
            val lastVisibleItem = visibleItems.last()
            AuditEventCursorCodec.encode(
                com.profiletailors.smp.governance.application.AuditEventCursor(
                    createdAt = lastVisibleItem.createdAt,
                    id = lastVisibleItem.id,
                ),
            )
        } else {
            null
        }
        return WorkspaceAuditEventsResponse(
            workspaceId = workspaceId,
            items = visibleItems,
            page = AuditEventPage(
                cursor = query.cursor,
                limit = normalizedLimit,
                returned = visibleItems.size,
                hasMore = items.size > normalizedLimit,
                nextCursor = nextCursor,
            ),
        )
    }
}
