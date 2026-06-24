package com.profiletailors.common.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GlobalSystemConstantsTest {

    @Test
    fun `should define SYSTEM_USER as non-blank`() {
        assertThat(SYSTEM_USER).isNotBlank()
    }

    @Test
    fun `should define SYSTEM_USER as system`() {
        assertThat(SYSTEM_USER).isEqualTo("system")
    }

    @Test
    fun `should define SYSTEM_USER_UUID as nil UUID`() {
        assertThat(SYSTEM_USER_UUID).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000"))
    }
}
