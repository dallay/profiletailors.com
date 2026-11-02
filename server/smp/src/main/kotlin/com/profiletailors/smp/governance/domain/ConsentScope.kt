package com.profiletailors.smp.governance.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

sealed interface ConsentScope {
    @ValueObject
    data class Workspace(val id: UUID) : ConsentScope

    @ValueObject
    data class Waitlist(val id: UUID) : ConsentScope

    data object Platform : ConsentScope
}
