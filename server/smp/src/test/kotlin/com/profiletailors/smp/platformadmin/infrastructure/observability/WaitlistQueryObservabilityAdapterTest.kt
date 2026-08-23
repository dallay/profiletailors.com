package com.profiletailors.smp.platformadmin.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaitlistQueryObservabilityAdapterTest {

    @Test
    fun `recordListQuery increments counter tagged with status filter and email search`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilter = "PENDING", emailSearch = true)

        val counter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "PENDING")
            .tag("email.search", "true")
            .counter()
        assertEquals(1.0, requireNotNull(counter).count())
    }

    @Test
    fun `recordListQuery tags absent status filter as none`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilter = null, emailSearch = false)

        val counter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "none")
            .tag("email.search", "false")
            .counter()
        assertEquals(1.0, requireNotNull(counter).count())
    }

    @Test
    fun `recordListQuery accumulates count for repeated calls with the same tags`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilter = "INVITED", emailSearch = false)
        adapter.recordListQuery(statusFilter = "INVITED", emailSearch = false)
        adapter.recordListQuery(statusFilter = "INVITED", emailSearch = false)

        val counter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "INVITED")
            .tag("email.search", "false")
            .counter()
        assertEquals(3.0, requireNotNull(counter).count())
    }

    @Test
    fun `recordListQuery with different tag combinations registers separate counters`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilter = "PENDING", emailSearch = false)
        adapter.recordListQuery(statusFilter = "CANCELLED", emailSearch = true)

        assertEquals(
            2,
            meterRegistry.meters.count { it.id.name == METRIC_NAME },
        )
        val pendingCounter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "PENDING")
            .tag("email.search", "false")
            .counter()
        val cancelledCounter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "CANCELLED")
            .tag("email.search", "true")
            .counter()
        assertEquals(1.0, requireNotNull(pendingCounter).count())
        assertEquals(1.0, requireNotNull(cancelledCounter).count())
    }

    @Test
    fun `recordListQuery does not leak email content into low-cardinality tags`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilter = "PENDING", emailSearch = true)

        val tagValues = meterRegistry.meters
            .filter { it.id.name == METRIC_NAME }
            .flatMap { it.id.tags }
            .map { it.value }
        assertTrue(tagValues.none { it.contains("@") })
    }

    private companion object {
        const val METRIC_NAME = "platform.admin.waitlist.queries"
    }
}