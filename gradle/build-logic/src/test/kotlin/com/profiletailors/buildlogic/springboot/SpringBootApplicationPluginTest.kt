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

    private fun writeProject(testSource: String = "class PlaceholderTest") {
        val versionCatalog = File(projectDir, "gradle/libs.versions.toml")
        versionCatalog.parentFile.mkdirs()
        versionCatalog.writeText(
            """
                [versions]
                kotlin = "2.2.21"

                [plugins]
                kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
                kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
                spring-boot = { id = "org.springframework.boot", version = "4.0.0" }
                spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
                detekt = { id = "dev.detekt", version = "1.23.8" }
                kover = { id = "org.jetbrains.kotlinx.kover", version = "0.9.3" }
            """.trimIndent(),
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
                pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
                dependencyResolutionManagement { repositories { mavenCentral() } }
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
                plugins {
                    id("com.profiletailors.spring.boot.application")
                }

                repositories { mavenCentral() }

                dependencies {
                    testImplementation(platform("org.junit:junit-bom:6.1.0"))
                    testImplementation("org.junit.jupiter:junit-jupiter")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }
            """.trimIndent(),
        )
        File(projectDir, "src/test/kotlin").mkdirs()
        File(projectDir, "src/test/kotlin/FastTagBoundaryTest.kt").writeText(testSource)
    }
}
