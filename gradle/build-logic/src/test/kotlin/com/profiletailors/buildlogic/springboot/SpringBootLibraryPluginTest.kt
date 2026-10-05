package com.profiletailors.buildlogic.springboot

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SpringBootLibraryPluginTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `forces patched Jackson versions over vulnerable declarations`() {
        writeProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dependencies", "--configuration", "runtimeClasspath", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dependencies")?.outcome)
        assertTrue(
            result.output.contains("jackson-databind:2.22.1 -> 2.22.3"),
            "com.fasterxml.jackson databind should be forced from 2.22.1 to 2.22.3",
        )
        assertTrue(
            result.output.contains("jackson-databind:3.1.5 -> 3.2.3"),
            "tools.jackson databind should be forced from 3.1.5 to 3.2.3",
        )
    }

    @Test
    fun `declares patched Jackson BOM platforms for Dependabot visibility`() {
        writeProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dependencies", "--configuration", "runtimeClasspath", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dependencies")?.outcome)
        val rootPlatforms =
            result.output
                .lineSequence()
                .filter { it.startsWith("+--- ") || it.startsWith("\\--- ") }
                .toList()
        assertTrue(
            rootPlatforms.any { it.contains("com.fasterxml.jackson:jackson-bom:2.22.3") },
            "jackson-bom 2.x platform should be declared at the patched version",
        )
        assertTrue(
            rootPlatforms.any { it.contains("tools.jackson:jackson-bom:3.2.3") },
            "jackson-bom 3.x platform should be declared at the patched version",
        )
    }

    private fun writeProject() {
        val versionCatalog = File(projectDir, "gradle/libs.versions.toml")
        versionCatalog.parentFile.mkdirs()
        versionCatalog.writeText(
            """
                [versions]
                kotlin = "2.4.10"
                springBoot = "4.0.0"
                jackson = "3.2.3"
                jackson2 = "2.22.3"

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
                    id("com.profiletailors.spring.boot.library")
                }

                repositories { mavenCentral() }

                dependencies {
                    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
                    implementation("tools.jackson.core:jackson-databind:3.1.5")
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
    }
}
