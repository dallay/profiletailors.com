package com.profiletailors.smp.bdd.glue

import org.springframework.test.web.reactive.server.EntityExchangeResult

class PlatformAdminScenarioState {
    var lastResponse: EntityExchangeResult<ByteArray>? = null
    var lastEntryId: String? = null
    var lastInvitationId: String? = null
    var invitationToken: String? = null
}
