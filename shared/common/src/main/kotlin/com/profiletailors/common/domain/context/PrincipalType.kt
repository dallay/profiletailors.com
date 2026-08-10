package com.profiletailors.common.domain.context

import com.profiletailors.common.domain.ValueObject

/**
 * Represents the type of principal (actor) performing an operation in the system.
 *
 * @see PrincipalContext for the full principal description including id and type.
 */
@ValueObject
enum class PrincipalType {
    /**
     * Human user — interactive principal authenticating via login.
     * Example: a person using the Profile Tailors web app.
     */
    USER,

    /**
     * Long-lived machine account used for automated workflows and integrations.
     * Example: a service account used by an external CRM sync bot.
     */
    SERVICE_ACCOUNT,

    /**
     * Token-scoped client identified by an API key.
     * Example: a third-party webhook consumer with a limited-scope API key.
     */
    API_KEY,

    /**
     * Internal system operation triggered by background jobs or infrastructure.
     * Example: a scheduled cleanup task running as the system principal.
     */
    SYSTEM,

    /**
     * External connected service with delegated permissions.
     * Example: a LinkedIn OAuth app that can post on behalf of a user.
     */
    INTEGRATION,

    /**
     * Autonomous agent operating without human interaction.
     * Example: an AI agent that schedules posts based on content strategy.
     */
    AGENT,
}
