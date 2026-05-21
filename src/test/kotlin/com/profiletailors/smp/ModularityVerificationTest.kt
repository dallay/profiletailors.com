package com.profiletailors.smp

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityVerificationTest {

    @Test
    fun verifiesApplicationModules() {
        ApplicationModules.of(SmpApplication::class.java).verify()
    }
}
