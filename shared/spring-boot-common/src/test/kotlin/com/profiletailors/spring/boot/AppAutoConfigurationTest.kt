package com.profiletailors.spring.boot

import com.profiletailors.common.domain.bus.Mediator
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper

class AppAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AppAutoConfiguration::class.java))

    @Test
    fun `should provide default beans`() {
        contextRunner.run { context ->
            context shouldNotBe null
            context.getBean(AppSpringBeanProvider::class.java) shouldNotBe null
            context.getBean(Mediator::class.java) shouldNotBe null
            context.getBean(JsonMapper::class.java) shouldNotBe null
        }
    }
}
