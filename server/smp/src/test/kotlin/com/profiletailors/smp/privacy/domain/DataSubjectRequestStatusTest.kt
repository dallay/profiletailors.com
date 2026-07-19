package com.profiletailors.smp.privacy.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DataSubjectRequestStatusTest {

    @Test
    fun `PENDING can transition to COMPLETED`() {
        assertTrue(DataSubjectRequestStatus.PENDING.canTransitionTo(DataSubjectRequestStatus.COMPLETED))
    }

    @Test
    fun `PENDING can transition to REJECTED`() {
        assertTrue(DataSubjectRequestStatus.PENDING.canTransitionTo(DataSubjectRequestStatus.REJECTED))
    }

    @Test
    fun `PENDING can transition to FAILED`() {
        assertTrue(DataSubjectRequestStatus.PENDING.canTransitionTo(DataSubjectRequestStatus.FAILED))
    }

    @Test
    fun `PENDING cannot transition to PENDING`() {
        assertFalse(DataSubjectRequestStatus.PENDING.canTransitionTo(DataSubjectRequestStatus.PENDING))
    }

    @Test
    fun `COMPLETED is terminal and rejects any transition`() {
        DataSubjectRequestStatus.entries.forEach { target ->
            assertFalse(
                DataSubjectRequestStatus.COMPLETED.canTransitionTo(target),
                "COMPLETED should not transition to $target",
            )
        }
    }

    @Test
    fun `REJECTED is terminal and rejects any transition`() {
        DataSubjectRequestStatus.entries.forEach { target ->
            assertFalse(
                DataSubjectRequestStatus.REJECTED.canTransitionTo(target),
                "REJECTED should not transition to $target",
            )
        }
    }

    @Test
    fun `FAILED is terminal and rejects any transition`() {
        DataSubjectRequestStatus.entries.forEach { target ->
            assertFalse(
                DataSubjectRequestStatus.FAILED.canTransitionTo(target),
                "FAILED should not transition to $target",
            )
        }
    }

    @Test
    fun `transitionTo throws IllegalStateException for invalid transition`() {
        assertThrows<IllegalStateException> {
            DataSubjectRequestStatus.PENDING.transitionTo(
                DataSubjectRequestStatus.PENDING,
            )
        }
    }

    @Test
    fun `transitionTo succeeds for valid transition`() {
        val result = DataSubjectRequestStatus.PENDING.transitionTo(DataSubjectRequestStatus.COMPLETED)
        assertTrue(result)
    }
}
