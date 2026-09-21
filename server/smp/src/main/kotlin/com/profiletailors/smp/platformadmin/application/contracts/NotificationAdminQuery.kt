package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.smp.platformadmin.application.model.NotificationSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.NotificationFilters

interface NotificationAdminQuery {
    suspend fun list(filters: NotificationFilters, page: Int, size: Int): PagedResult<NotificationSummary>

    suspend fun findById(notificationId: NotificationId): NotificationSummary?
}
