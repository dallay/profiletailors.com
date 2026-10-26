package com.profiletailors.smp.governance.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

sealed interface ConsentScope {
    val id: UUID

    @ValueObject
    data class Workspace(override val id: UUID) : ConsentScope

    @ValueObject
    data class Waitlist(override val id: UUID) : ConsentScope
}
