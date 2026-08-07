package com.profiletailors.smp

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.test.TestStorageConfiguration
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
    ],
)
@Import(TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformBootstrapContextTest(
    @Autowired private val mediator: Mediator,
    @Autowired @Qualifier("socialContentBatchWriter") private val socialContentBatchWriter: SocialContentBatchWriter,
) {

    @Test
    fun `registers mediator platform bean`() {
        assertNotNull(mediator)
    }

    @Test
    fun `registers the schema supported social content batch writer`() {
        assertNotNull(socialContentBatchWriter)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("platform_test")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
