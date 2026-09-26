package com.profiletailors.buildlogic.testing

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MutationTestingPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `registers verifyMutationClean task`() {
        writeProject()
        val output =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build().output
        assertTrue(output.contains("verifyMutationClean"))
    }

    @Test
    fun `sets explicit narrow baseline targets`() {
        writeProject()
        File(projectDir, "build.gradle.kts").appendText(
            """
            val mutflow = extensions.getByType<io.github.anschnapp.mutflow.gradle.MutflowExtension>()
            check(mutflow.targets.get().size == 2)
            check(mutflow.targets.get().any { it.contains("BulkValidationPipeline") })
            check(mutflow.targets.get().any { it.contains("PublicationLifecyclePolicy") })
            """.trimIndent(),
        )
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("help", "--stacktrace")
            .build()
    }

    @Test
    fun `mutatedMain carries no resources to avoid duplicate classpath entries`() {
        writeProject()
        File(projectDir, "build.gradle.kts").appendText(
            "\n" +
                """
                afterEvaluate {
                    val sourceSets = extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()
                    val mutated = sourceSets.getByName("mutatedMain")
                    check(mutated.resources.srcDirs.isEmpty())
                }
                """.trimIndent(),
        )
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("help", "--stacktrace")
            .build()
    }

    @Test
    fun `test task still passes without mutation annotations`() {        writeProject()
        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("test", "--stacktrace")
                .build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
    }

    private fun writeProject() {
        val versionCatalog = File(projectDir, "gradle/libs.versions.toml")
        versionCatalog.parentFile.mkdirs()
        versionCatalog.writeText(
            """
            [versions]
            kotlin = "2.4.10"
            mutflow = "1.5.0"

            [plugins]
            kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
            mutflow = { id = "io.github.anschnapp.mutflow", version.ref = "mutflow" }
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
                id("org.jetbrains.kotlin.jvm") version "2.4.10"
                id("com.profiletailors.mutation.testing")
            }

            repositories { mavenCentral() }

            tasks.withType<Test>().configureEach { useJUnitPlatform() }

            dependencies {
                testImplementation(platform("org.junit:junit-bom:6.1.3"))
                testImplementation("org.junit.jupiter:junit-jupiter")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }
            """.trimIndent(),
        )
        val testSourceDir = File(projectDir, "src/test/kotlin")
        testSourceDir.mkdirs()
        File(testSourceDir, "BaselineBoundaryTest.kt").writeText(
            """
            import org.junit.jupiter.api.Test

            class BaselineBoundaryTest {
                @Test
                fun placeholderPasses() = Unit
            }
            """.trimIndent(),
        )
        val mainSourceDir = File(projectDir, "src/main/kotlin")
        mainSourceDir.mkdirs()
        File(mainSourceDir, "BaselineSubject.kt").writeText(
            """
            class BaselineSubject {
                fun isPositive(value: Int): Boolean = value > 0
            }
            """.trimIndent(),
        )
    }
}
