package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ConsentRecord

/** Result of atomically recording consent. */
data class RecordConsentOutcome(val created: Boolean, val record: ConsentRecord)
