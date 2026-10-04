package com.profiletailors.smp.shortlinks.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DomainIdTest {
    @Test
    fun `hostname identity is stable regardless of casing or whitespace`() {
        assertEquals(DomainId.fromHost("go.profiletailors.com"), DomainId.fromHost(" GO.PROFILETAILORS.COM "))
    }
}
