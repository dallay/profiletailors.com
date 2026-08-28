package com.profiletailors.buildlogic.legal

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LicenceReportPluginTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `configures licence reporting without deprecated Gradle APIs`() {
        val source = locatePluginSource().readText()

        assertFalse(source.contains("\$buildDir"))
        assertTrue(source.contains("arrayOf<ReportRenderer>"))

        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText(
            """
                plugins {
                    id("com.profiletailors.legal.licence-report")
                }

                repositories {
                    mavenCentral()
                }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("help", "--warning-mode=fail", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":help")?.outcome)
    }

    private fun locatePluginSource(): File {
        val workingDirectory = File(System.getProperty("user.dir"))
        return generateSequence(workingDirectory) { it.parentFile }
            .map {
                File(
                    it,
                    "gradle/build-logic/src/main/kotlin/com/profiletailors/buildlogic/legal/LicenceReportPlugin.kt",
                )
            }
            .firstOrNull(File::isFile)
            ?: error("LicenceReportPlugin.kt source file was not found")
    }
}
