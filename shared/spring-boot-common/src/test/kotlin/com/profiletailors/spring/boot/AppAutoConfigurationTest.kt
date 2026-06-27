package com.profiletailors.spring.boot

import com.profiletailors.common.domain.bus.Mediator
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

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

    @Test
    fun `should use custom JsonMapper when provided`() {
        contextRunner
            .withUserConfiguration(CustomJsonMapperConfig::class.java)
            .run { context ->
                // Only one JsonMapper bean should exist (the custom one wins)
                context.getBeanNamesForType(JsonMapper::class.java).size shouldNotBe 0
            }
    }

    @Test
    fun `should register AppSpringBeanProvider as single bean`() {
        contextRunner.run { context ->
            val beanNames = context.getBeanNamesForType(AppSpringBeanProvider::class.java)
            beanNames.size shouldNotBe 0
        }
    }

    @Configuration
    open class CustomJsonMapperConfig {
        @Bean
        open fun jsonMapper(): JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()
    }
}
