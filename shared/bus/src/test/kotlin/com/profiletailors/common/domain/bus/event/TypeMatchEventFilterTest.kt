package com.profiletailors.common.domain.bus.event

import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class TypeMatchEventFilterTest {

    @Test
    fun `should accept event of matching type`() = runTest {
        val filter = TypeMatchEventFilter<ConcreteDomainEvent>(ConcreteDomainEvent::class)
        val event = ConcreteDomainEvent()

        val result = filter.filter(event)

        assertThat(result).isTrue
    }

    @Test
    fun `should reject event of non-matching type`() = runTest {
        @Suppress("UNCHECKED_CAST")
        val filter = TypeMatchEventFilter<DomainEvent>(ConcreteDomainEvent::class as KClass<DomainEvent>)
        val event = OtherDomainEvent()

        val result = filter.filter(event)

        assertThat(result).isFalse
    }

    private open class ConcreteDomainEvent : DomainEvent {
        override fun eventVersion(): Int = 1
        override fun occurredOn(): LocalDateTime? = LocalDateTime.now()
    }

    private class OtherDomainEvent : DomainEvent {
        override fun eventVersion(): Int = 1
        override fun occurredOn(): LocalDateTime? = LocalDateTime.now()
    }
}
