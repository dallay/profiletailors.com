package com.profiletailors.smp.platformadmin.application.query

import java.time.Instant
import java.util.UUID

data class ListAdminWaitlistEntriesQuery(
    val page: Int = 0,
    val size: Int = 25,
    val sortField: String = "joinedAt",
    val sortDirection: String = "desc",
    val status: String? = null,
    val waitlistId: String? = null,
    val waitlistKey: String? = null,
    val email: String? = null,
    val joinedFrom: Instant? = null,
    val joinedTo: Instant? = null,
    val invitedFrom: Instant? = null,
    val invitedTo: Instant? = null,
)

data class ListAdminUsersQuery(
    val page: Int = 0,
    val size: Int = 25,
    val sortField: String = "createdAt",
    val sortDirection: String = "desc",
    val status: String? = null,
    val email: String? = null,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null,
)

data class ListAdminAuditEventsQuery(
    val page: Int = 0,
    val size: Int = 25,
    val operatorPrincipalId: UUID? = null,
    val action: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val result: String? = null,
    val occurredFrom: Instant? = null,
    val occurredTo: Instant? = null,
    val correlationId: String? = null,
)

data class GetAdminDashboardQuery(val periodDays: Int = 30)
