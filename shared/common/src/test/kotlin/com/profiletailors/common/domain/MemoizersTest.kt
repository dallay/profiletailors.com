package com.profiletailors.common.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

internal class MemoizersTest {

    @Test
    fun `should execute supplier only once`() {
        val counter = AtomicInteger(0)
        val memoized = Memoizers.of<Int> { counter.incrementAndGet() }

        val first = memoized()
        val second = memoized()
        val third = memoized()

        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(1)
        assertThat(third).isEqualTo(1)
        assertThat(counter).hasValue(1)
    }

    @Test
    fun `should memoize by input key`() {
        val counter = AtomicInteger(0)
        val memoized = Memoizers.of<String, String> { input ->
            counter.incrementAndGet()
            "result-$input"
        }

        val resultA = memoized("A")
        val resultB = memoized("B")
        val resultC = memoized("C")

        assertThat(resultA).isEqualTo("result-A")
        assertThat(resultB).isEqualTo("result-B")
        assertThat(resultC).isEqualTo("result-C")
        assertThat(counter).hasValue(3)
    }

    @Test
    fun `should return same result for same input`() {
        val counter = AtomicInteger(0)
        val memoized = Memoizers.of<String, String> { input ->
            counter.incrementAndGet()
            "hello-$input"
        }

        val first = memoized("world")
        val second = memoized("world")
        val third = memoized("world")

        assertThat(first).isEqualTo("hello-world")
        assertThat(second).isEqualTo("hello-world")
        assertThat(third).isEqualTo("hello-world")
        assertThat(counter).hasValue(1)
    }

    @Test
    fun `should handle null input`() {
        val counter = AtomicInteger(0)
        val memoized = Memoizers.of<String?, String> { input ->
            counter.incrementAndGet()
            "processed-${input ?: "null"}"
        }

        val first = memoized(null)
        val second = memoized(null)

        assertThat(first).isEqualTo("processed-null")
        assertThat(second).isEqualTo("processed-null")
        assertThat(counter).hasValue(1)
    }
}
