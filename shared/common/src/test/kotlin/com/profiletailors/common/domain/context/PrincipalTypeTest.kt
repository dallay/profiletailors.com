package com.profiletailors.common.domain.context

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PrincipalTypeTest {

    @Test
    fun `should have exactly 6 principal types`() {
        val values = PrincipalType.entries

        assertThat(values).hasSize(6)
    }

    @Test
    fun `should include all expected types`() {
        assertThat(PrincipalType.entries).containsExactly(
            PrincipalType.USER,
            PrincipalType.SERVICE_ACCOUNT,
            PrincipalType.API_KEY,
            PrincipalType.SYSTEM,
            PrincipalType.INTEGRATION,
            PrincipalType.AGENT,
        )
    }

    @Test
    fun `should resolve each value by name`() {
        assertThat(PrincipalType.valueOf("USER")).isEqualTo(PrincipalType.USER)
        assertThat(PrincipalType.valueOf("SERVICE_ACCOUNT")).isEqualTo(PrincipalType.SERVICE_ACCOUNT)
        assertThat(PrincipalType.valueOf("API_KEY")).isEqualTo(PrincipalType.API_KEY)
        assertThat(PrincipalType.valueOf("SYSTEM")).isEqualTo(PrincipalType.SYSTEM)
        assertThat(PrincipalType.valueOf("INTEGRATION")).isEqualTo(PrincipalType.INTEGRATION)
        assertThat(PrincipalType.valueOf("AGENT")).isEqualTo(PrincipalType.AGENT)
    }

    @Test
    fun `should maintain consistent ordinal order`() {
        assertThat(PrincipalType.USER.ordinal).isEqualTo(0)
        assertThat(PrincipalType.SYSTEM.ordinal).isEqualTo(3)
        assertThat(PrincipalType.AGENT.ordinal).isEqualTo(5)
    }
}
