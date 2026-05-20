package com.profiletailors.smp

import com.profiletailors.common.domain.bus.Mediator
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PlatformBootstrapContextTest(
    @Autowired private val mediator: Mediator,
) {

    @Test
    fun `registers mediator platform bean`() {
        assertNotNull(mediator)
    }
}
