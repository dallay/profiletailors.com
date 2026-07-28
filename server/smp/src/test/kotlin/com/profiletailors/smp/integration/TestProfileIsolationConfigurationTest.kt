package com.profiletailors.smp.integration

import com.profiletailors.smp.bdd.fast.CucumberSpringConfiguration
import com.profiletailors.smp.bdd.postgres.CucumberPostgresSpringConfiguration
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestContextAnnotationUtils
import kotlin.test.assertContentEquals

class TestProfileIsolationConfigurationTest {

    @Test
    fun `spring integration contexts explicitly isolate themselves from ambient profiles`() {
        listOf(
            CucumberSpringConfiguration::class.java,
            CucumberPostgresSpringConfiguration::class.java,
            ActuatorEndpointsIntegrationTest::class.java,
        ).forEach { testClass ->
            val activeProfiles = TestContextAnnotationUtils.findMergedAnnotation(
                testClass,
                ActiveProfiles::class.java,
            )

            assertContentEquals(
                expected = arrayOf("test"),
                actual = activeProfiles?.profiles,
                message = "${testClass.simpleName} must pin the test profile",
            )
        }
    }
}
