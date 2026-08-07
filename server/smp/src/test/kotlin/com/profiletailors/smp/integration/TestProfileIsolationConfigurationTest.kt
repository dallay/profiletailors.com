package com.profiletailors.smp.integration

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.bdd.SocialContentBddTestConfiguration
import com.profiletailors.smp.bdd.fast.CucumberSpringConfiguration
import com.profiletailors.smp.bdd.postgres.CucumberPostgresSpringConfiguration
import com.profiletailors.smp.publishing.application.SocialContentSyncCommandHandler
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialAccountRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoUniqueBeanDefinitionException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestContextAnnotationUtils
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class LegacyPublishingHandler(val socialAccountRepository: SocialAccountRepository)

private class SocialContentPublishingHandler(val socialAccountRepository: SocialAccountRepository)

private fun SocialContentSyncCommandHandler.socialAccountRepositoryForTest(): SocialAccountRepository =
    javaClass.getDeclaredField("socialAccountRepository").apply { isAccessible = true }
        .get(this) as SocialAccountRepository

@TestConfiguration(proxyBeanMethods = false)
private class AmbiguousSocialAccountRepositoryTestConfiguration {
    @Bean("r2dbcSocialAccountRepository")
    fun r2dbcSocialAccountRepository(): SocialAccountRepository = mockk(relaxed = true)

    @Bean("socialContentAccountRepository")
    fun socialContentAccountRepository(): SocialAccountRepository = mockk(relaxed = true)

    @Bean
    fun unqualifiedLegacyHandler(repository: SocialAccountRepository): LegacyPublishingHandler =
        LegacyPublishingHandler(repository)
}

@TestConfiguration(proxyBeanMethods = false)
private class SocialAccountRepositoryWiringTestConfiguration {
    @Bean
    fun resourceContextProvider(): ResourceContextProvider = object : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
    }

    @Bean
    fun r2dbcSocialAccountRepository(): R2dbcSocialAccountRepository = R2dbcSocialAccountRepository(
        databaseClient = mockk<DatabaseClient>(relaxed = true),
        meterRegistry = SimpleMeterRegistry(),
    )

    @Bean
    fun legacyPublishingHandler(
        @Qualifier("r2dbcSocialAccountRepository") repository: SocialAccountRepository,
    ): LegacyPublishingHandler = LegacyPublishingHandler(repository)

    @Bean
    fun socialContentPublishingHandler(
        @Qualifier("socialContentAccountRepository") repository: SocialAccountRepository,
    ): SocialContentPublishingHandler = SocialContentPublishingHandler(repository)
}

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

    @Test
    fun `both cucumber variants import social content state configuration`() {
        listOf(
            CucumberSpringConfiguration::class.java,
            CucumberPostgresSpringConfiguration::class.java,
        ).forEach { testClass ->
            val imports = AnnotationUtils.findAnnotation(testClass, Import::class.java)

            assertTrue(
                imports?.value?.contains(SocialContentBddTestConfiguration::class) == true,
                "${testClass.simpleName} must import SocialContentBddTestConfiguration",
            )
        }
    }

    @Test
    fun `unqualified social account repository wiring exposes the current ambiguity`() {
        ApplicationContextRunner()
            .withUserConfiguration(AmbiguousSocialAccountRepositoryTestConfiguration::class.java)
            .run { context ->
                val startupFailure = context.startupFailure

                assertTrue(startupFailure != null)
                assertTrue(
                    generateSequence(startupFailure) { it.cause }
                        .any { it is NoUniqueBeanDefinitionException },
                )
            }
    }

    @Test
    fun `legacy handlers use r2dbc while social content handlers use the bdd fake`() {
        ApplicationContextRunner()
            .withUserConfiguration(
                SocialAccountRepositoryWiringTestConfiguration::class.java,
                SocialContentBddTestConfiguration::class.java,
            )
            .run { context ->
                assertNull(context.startupFailure)

                val legacyRepository = context.getBean(LegacyPublishingHandler::class.java).socialAccountRepository
                val socialContentRepository =
                    context.getBean(SocialContentPublishingHandler::class.java).socialAccountRepository
                val socialContentSyncHandler = context.getBean(SocialContentSyncCommandHandler::class.java)

                assertIs<R2dbcSocialAccountRepository>(legacyRepository)
                assertSame(
                    context.getBean("r2dbcSocialAccountRepository"),
                    legacyRepository,
                    "legacy handlers must use the R2DBC repository bean",
                )
                assertSame(
                    context.getBean("socialContentAccountRepository"),
                    socialContentRepository,
                    "social-content handlers must use the BDD fake bean",
                )
                assertSame(
                    context.getBean("socialContentAccountRepository"),
                    socialContentSyncHandler.socialAccountRepositoryForTest(),
                    "the real social-content sync handler must use the BDD fake bean",
                )
                assertTrue(legacyRepository !== socialContentRepository)
            }
    }
}
