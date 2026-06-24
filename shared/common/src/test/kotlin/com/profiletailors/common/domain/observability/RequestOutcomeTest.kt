package com.profiletailors.common.domain.observability

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RequestOutcomeTest {

    @Test
    fun `should have exactly 2 outcomes`() {
        assertThat(RequestOutcome.entries).hasSize(2)
    }

    @Test
    fun `should include SUCCESS and FAILURE`() {
        assertThat(RequestOutcome.entries).containsExactly(
            RequestOutcome.SUCCESS,
            RequestOutcome.FAILURE,
        )
    }

    @Test
    fun `should resolve by name`() {
        assertThat(RequestOutcome.valueOf("SUCCESS")).isEqualTo(RequestOutcome.SUCCESS)
        assertThat(RequestOutcome.valueOf("FAILURE")).isEqualTo(RequestOutcome.FAILURE)
    }
}
