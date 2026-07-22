package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.query.Query

data class ReleaseGateQuery(val release: String) : Query<ReleaseGateResult>

data class ReleaseGateResult(
    val release: String,
    val gateStatus: String,
    val totalControls: Int,
    val passed: Int,
    val failed: Int,
    val waived: Int,
    val evaluatedAt: String,
)
