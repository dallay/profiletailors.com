package com.profiletailors.smp

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

@Tag("modularity")
class ModularityVerificationTest {

    @Test
    fun verifiesApplicationModules() {
        try {
            ApplicationModules.of(SmpApplication::class.java).verify()
        } catch (exception: RuntimeException) {
            println("MODULITH_VERIFICATION_FAILURE: ${exception.message}")
            throw exception
        }
    }
}
