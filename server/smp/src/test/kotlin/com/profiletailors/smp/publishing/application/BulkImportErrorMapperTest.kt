package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.BulkJobStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BulkImportErrorMapperTest {
    @Test
    fun `maps validation exception to invalid media`() {
        val error = BulkImportErrorMapper.map(PublicationValidationException("bad media"))
        assertEquals("INVALID_MEDIA", error.code)
    }

    @Test
    fun `maps capability illegal argument to capability violation`() {
        val error = BulkImportErrorMapper.map(IllegalArgumentException("CAPABILITY boom"))
        assertEquals("CAPABILITY_VIOLATION", error.code)
    }

    @Test
    fun `maps plain illegal argument to invalid date`() {
        val error = BulkImportErrorMapper.map(IllegalArgumentException("bad date"))
        assertEquals("INVALID_DATE", error.code)
    }

    @Test
    fun `maps unknown to unknown`() {
        val error = BulkImportErrorMapper.map(IllegalStateException("boom"))
        assertEquals("UNKNOWN", error.code)
    }

    @Test
    fun `final status scheduled when all pass`() {
        assertEquals(BulkJobStatus.SCHEDULED, BulkImportFinalStatus.compute(2, 2, 0))
    }

    @Test
    fun `final status failed when all fail`() {
        assertEquals(BulkJobStatus.FAILED, BulkImportFinalStatus.compute(2, 0, 2))
    }

    @Test
    fun `final status partial otherwise`() {
        assertEquals(BulkJobStatus.PARTIAL, BulkImportFinalStatus.compute(2, 1, 1))
    }
}
