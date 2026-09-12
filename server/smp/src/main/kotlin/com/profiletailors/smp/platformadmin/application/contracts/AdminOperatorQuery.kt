package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.application.model.AdminOperatorSummary

interface AdminOperatorQuery {
    suspend fun listAllActive(): List<AdminOperatorSummary>
}
