package com.profiletailors.smp.platformadmin.application.ports

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry

/**
 * Read/write port for waitlist entry mutations required by platform administration.
 * Implemented in infrastructure using R2DBC, keeping domain ownership in lead-capture.
 */
interface WaitlistEntryAdminPort {
    suspend fun findById(id: String): WaitlistEntry?
    suspend fun save(entry: WaitlistEntry): WaitlistEntry
}
