package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext

data class EvaluateComplianceQuery(val context: ComplianceEvaluationContext) : Query<ComplianceEvaluation>
