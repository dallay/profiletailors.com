package com.profiletailors.smp.platformadmin.infrastructure.cli

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcPlatformBootstrapInvitationRepository
import liquibase.integration.spring.SpringLiquibase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor

class PlatformAdminBootstrapCliConfigurationTest {
    @Test
    fun `explicit bootstrap context excludes server migrations events and schedulers`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "bootstrap-test",
                mapOf(
                    "SMP_R2DBC_URL" to "r2dbc:postgresql://localhost:5432/bootstrap_test",
                    "SMP_DB_USERNAME" to "bootstrap-test",
                    "SMP_DB_PASSWORD" to "bootstrap-test",
                    "SMP_ADMIN_PUBLIC_ORIGIN" to "https://admin.example.test",
                ),
            ),
        )
        context.register(PlatformAdminBootstrapCliConfiguration::class.java)
        context.refresh()

        assertTrue(context.getBeansOfType(R2dbcPlatformBootstrapInvitationRepository::class.java).isNotEmpty())
        assertFalse(context.beanDefinitionNames.any { it.contains("webServer", ignoreCase = true) })
        assertTrue(context.getBeansOfType(SpringLiquibase::class.java).isEmpty())
        assertTrue(context.getBeansOfType(EventConsumer::class.java).isEmpty())
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).isEmpty())
        assertFalse(context.beanDefinitionNames.any { it.contains("eventConfiguration", ignoreCase = true) })

        context.close()
    }
}
