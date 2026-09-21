package com.profiletailors.smp.platformadmin.application.command

import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import java.util.UUID

data class RetryNotificationCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val notificationId: NotificationId,
    val idempotencyKey: String? = null,
)
