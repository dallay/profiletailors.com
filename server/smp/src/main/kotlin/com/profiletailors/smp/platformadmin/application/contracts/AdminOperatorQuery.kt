package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.application.model.AdminOperatorSummary

fun interface AdminOperatorQuery {
    suspend fun listAllActive(): List<AdminOperatorSummary>
}
