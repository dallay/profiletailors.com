package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.query.Query

/**
 * Query to evaluate the release gate status for a given release.
 * Returns a [ReleaseGateResult] with the gate status and evaluation summary.
 */
data class ReleaseGateQuery(val release: String) : Query<ReleaseGateResult>

/**
 * Result of a release gate evaluation.
 */
data class ReleaseGateResult(
    val release: String,
    val gateStatus: String,
    val totalControls: Int,
    val passed: Int,
    val failed: Int,
    val waived: Int,
    val evaluatedAt: String,
)
