package com.profiletailors.common.domain.bus.notification

/**
 * Marker interface for notifications — fire-and-forget messages that can have
 * multiple handlers.
 *
 * Unlike [Command][com.profiletailors.common.domain.bus.command.Command] (exactly one handler) and
 * [Query][com.profiletailors.common.domain.bus.query.Query] (exactly one handler),
 * notifications are broadcast to ALL registered [NotificationHandler] instances.
 *
 * @see NotificationHandler
 * @see com.profiletailors.common.domain.bus.Mediator.publish
 */
interface Notification
