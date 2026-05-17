package com.profiletailors.spring.boot

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.MediatorBuilder
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.kotlinModule

@AutoConfiguration
class AppAutoConfiguration {
    @Bean
    fun appSpringBeanProvider(applicationContext: ApplicationContext): AppSpringBeanProvider =
        AppSpringBeanProvider(applicationContext)

    @Bean
    @ConditionalOnMissingBean(name = ["mediator"], value = [Mediator::class])
    fun mediator(appSpringBeanProvider: AppSpringBeanProvider): Mediator =
        MediatorBuilder(appSpringBeanProvider).build()

    /**
     * Provides a default JsonMapper (Jackson 3) for shared modules.
     * This can be overridden by the main application if needed.
     */
    @Bean
    @ConditionalOnMissingBean(JsonMapper::class)
    fun jsonMapper(): JsonMapper =
        JsonMapper.builder()
            .addModule(
                kotlinModule {
                    enable(KotlinFeature.NullToEmptyCollection)
                    enable(KotlinFeature.NullToEmptyMap)
                    enable(KotlinFeature.StrictNullChecks)
                },
            )
            .build()
}
