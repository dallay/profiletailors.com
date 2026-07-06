package com.profiletailors.common.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GlobalConstantsTest {
    @Test
    fun `Global constants should be accessible`() {
        assertEquals("system", SYSTEM_USER)
        assertEquals("00000000-0000-0000-0000-000000000000", SYSTEM_USER_UUID.toString())
    }
}
