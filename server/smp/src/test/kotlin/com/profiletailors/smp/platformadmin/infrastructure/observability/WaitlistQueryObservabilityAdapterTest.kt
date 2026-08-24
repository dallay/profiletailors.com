package com.profiletailors.smp.platformadmin.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaitlistQueryObservabilityAdapterTest {

    @Test
    fun `recordListQuery increments counter tagged with status filter applied and email search`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilterApplied = true, emailSearch = true)

        val counter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "true")
            .tag("email.search", "true")
            .counter()
        assertEquals(1.0, requireNotNull(counter).count())
    }

    @Test
    fun `recordListQuery tags absent status filter as false`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilterApplied = false, emailSearch = false)

        val counter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "false")
            .tag("email.search", "false")
            .counter()
        assertEquals(1.0, requireNotNull(counter).count())
    }

    @Test
    fun `recordListQuery accumulates count for repeated calls with the same tags`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilterApplied = true, emailSearch = false)
        adapter.recordListQuery(statusFilterApplied = true, emailSearch = false)
        adapter.recordListQuery(statusFilterApplied = true, emailSearch = false)

        val counter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "true")
            .tag("email.search", "false")
            .counter()
        assertEquals(3.0, requireNotNull(counter).count())
    }

    @Test
    fun `recordListQuery with different tag combinations registers separate counters`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilterApplied = true, emailSearch = false)
        adapter.recordListQuery(statusFilterApplied = false, emailSearch = true)

        assertEquals(
            2,
            meterRegistry.meters.count { it.id.name == METRIC_NAME },
        )
        val filteredCounter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "true")
            .tag("email.search", "false")
            .counter()
        val unfilteredCounter = meterRegistry.find(METRIC_NAME)
            .tag("status.filter", "false")
            .tag("email.search", "true")
            .counter()
        assertEquals(1.0, requireNotNull(filteredCounter).count())
        assertEquals(1.0, requireNotNull(unfilteredCounter).count())
    }

    @Test
    fun `recordListQuery does not leak email content into low-cardinality tags`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = WaitlistQueryObservabilityAdapter(meterRegistry)

        adapter.recordListQuery(statusFilterApplied = true, emailSearch = true)

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
