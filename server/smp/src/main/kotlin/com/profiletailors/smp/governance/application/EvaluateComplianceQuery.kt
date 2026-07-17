package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext

/**
 * Query to evaluate compliance for a given context (release, market, etc.).
 * Returns a complete [ComplianceEvaluation] with per-control results.
 */
data class EvaluateComplianceQuery(val context: ComplianceEvaluationContext) : Query<ComplianceEvaluation>
