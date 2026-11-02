package com.profiletailors.smp.config

import com.profiletailors.smp.identity.infrastructure.R2dbcPrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapCli
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapConsole
import liquibase.integration.spring.SpringLiquibase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor

class PlatformAdminBootstrapCommandTest {
    @Test
    fun `wires the identity lookup into an isolated bootstrap context`() {
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
        PlatformAdminBootstrapCommand.configure(
            context,
            object : BootstrapConsole {
                override fun readLine(prompt: String): String? = null
                override fun writeLine(value: String) = Unit
            },
        )
        context.refresh()

        assertTrue(context.getBeansOfType(R2dbcPrincipalIdentityLookup::class.java).isNotEmpty())
        assertTrue(context.getBeansOfType(BootstrapCli::class.java).isNotEmpty())
        assertFalse(context.beanDefinitionNames.any { it.contains("webServer", ignoreCase = true) })
        assertTrue(context.getBeansOfType(SpringLiquibase::class.java).isEmpty())
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).isEmpty())

        context.close()
    }
}
