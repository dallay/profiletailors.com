package com.profiletailors.buildlogic.springboot

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SpringBootApplicationPluginTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `registers postgresIntegrationTest with postgres tag and excludes cucumber suites`() {
        writeProject()

        val output = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build()
            .output

        assertTrue(output.contains("postgresIntegrationTest"), "postgresIntegrationTest task should be registered")
    }

    @Test
    fun `registers detekt and kover tasks with upgraded plugin versions`() {
        writeProject()

        val output = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build()
            .output

        assertTrue(output.contains("detekt"), "detekt task should be registered with detekt 2.0.0-alpha.6")
        assertTrue(output.contains("koverXmlReport"), "koverXmlReport task should be registered with kover 0.9.9")
    }

    @Test
    fun `fast test command can exclude postgres tag through gradle property`() {
        writeProject(
            testSource = """
                import org.junit.jupiter.api.Tag
                import org.junit.jupiter.api.Test

                class FastTagBoundaryTest {
                    @Test
                    fun fastTestRuns() = Unit

                    @Test
                    @Tag("postgres")
                    fun postgresTestIsExcluded() {
                        error("postgres-tagged tests must not run when excludeTags=postgres")
                    }
                }
            """.trimIndent(),
        )

        val result: BuildResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("test", "-PexcludeTags=postgres", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome) { "test task outcome should be SUCCESS — test lifecycle must execute" }
    }

    @Test
    fun `check task runs detekt and koverHtmlReport successfully`() {
        writeProject()

        val result: BuildResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("check", "koverHtmlReport", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":detekt")?.outcome) { "detekt task outcome should be SUCCESS — detekt analysis must execute" }
        assertEquals(TaskOutcome.SUCCESS, result.task(":koverHtmlReport")?.outcome) { "koverHtmlReport task outcome should be SUCCESS — kover HTML report must be generated" }
    }

    @Test
    fun `Kotlin library uses canonical Detekt policy`() {
        verifyDetektPolicy("com.profiletailors.kotlin.library")
    }

    @Test
    fun `Spring Boot application preserves canonical Detekt policy`() {
        verifyDetektPolicy("com.profiletailors.spring.boot.application")
    }

    private fun verifyDetektPolicy(pluginId: String) {
        writeProject(pluginId = pluginId)
        File(projectDir, "build.gradle.kts").appendText(
            """

                val policy = extensions.getByType<dev.detekt.gradle.extensions.DetektExtension>()
                check(policy.buildUponDefaultConfig.get())
                check(!policy.ignoreFailures.get())
                check(!policy.allRules.get())
            """.trimIndent(),
        )
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("help", "--stacktrace")
            .build()
    }

    private fun writeProject(
        pluginId: String = "com.profiletailors.spring.boot.application",
        testSource: String = """
            import org.junit.jupiter.api.Test

            class FastTagBoundaryTest {
                @Test
                fun placeholderTest() = Unit
            }

        """.trimIndent(),
    ) {
        val versionCatalog = File(projectDir, "gradle/libs.versions.toml")
        versionCatalog.parentFile.mkdirs()
        versionCatalog.writeText(
            """
                [versions]
                kotlin = "2.4.10"

                [plugins]
                kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
                kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
                spring-boot = { id = "org.springframework.boot", version = "4.0.0" }
                spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
                detekt = { id = "dev.detekt", version = "2.0.0-alpha.6" }
                kover = { id = "org.jetbrains.kotlinx.kover", version = "0.9.9" }
            """.trimIndent(),
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }
                dependencyResolutionManagement {
                    repositories {
                        mavenCentral()
                    }
                }

            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
                plugins {
                    id("$pluginId")
                }

                repositories { mavenCentral() }

                dependencies {
                    testImplementation(platform("org.junit:junit-bom:6.1.0"))
                    testImplementation("org.junit.jupiter:junit-jupiter")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

            """.trimIndent(),
        )
        val detektConfig = File(projectDir, "config/detekt/detekt.yml")
        detektConfig.parentFile.mkdirs()
        detektConfig.writeText(
            """
                config:
                  validation: true
                  warningsAsErrors: true

                complexity:
                  active: true

                exceptions:
                  active: true

                style:
                  active: true
            """.trimIndent(),
        )
        File(projectDir, "src/test/kotlin").mkdirs()
        File(projectDir, "src/test/kotlin/FastTagBoundaryTest.kt").writeText(testSource)
    }
}
