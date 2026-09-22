package com.profiletailors.smp.platformadmin.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminAuditActionTest {

    @Test
    fun `registry includes takedown approved and rejected`() {
        val names = AdminAuditAction.entries.map { it.name }.toSet()
        assertTrue("TAKEDOWN_APPROVED" in names)
        assertTrue("TAKEDOWN_REJECTED" in names)
        assertFalse("MEDIA_TAKEDOWN_APPROVED" in names)
        assertFalse("MEDIA_TAKEDOWN_REJECTED" in names)
    }
}
