package com.profiletailors.buildlogic.security

import com.profiletailors.buildlogic.AppConfiguration.APP_NAME
import com.profiletailors.buildlogic.ConventionPlugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension
import org.owasp.dependencycheck.reporting.ReportGenerator

private const val FAIL_BUILDS_ON_CVSS: Float = 7.0F // Fail build on High or Critical vulnerabilities
private const val AUTO_UPDATE: Boolean = true
private const val PURGE_DATABASE: Boolean = true
private const val DEFAULT_DELAY = 1000

@Suppress("unused")
internal class OwaspPlugin : ConventionPlugin {
    override fun Project.configure() {
        apply(plugin = "org.owasp.dependencycheck")

        // Register a task to purge the dependency check database in case of local corruption
        tasks.register<Delete>("purgeDependencyCheckDatabase") {
            description = "Purges the local dependency check database to resolve corruption issues"
            group = "security"

            doFirst {
                println("Purging dependency check database...")
            }

            delete(
                fileTree(layout.buildDirectory.dir("dependency-check-data").get().asFile) {
                    include("*.h2.db")
                    include("*.mv.db")
                    include("*.trace.db")
                    include("*.lock.db")
                },
            )

            doLast {
                println("Dependency check database purged successfully.")
            }
        }

        // Configure OWASP analyze tasks to bypass configuration cache validation
        tasks.withType(org.owasp.dependencycheck.gradle.tasks.Analyze::class.java).configureEach {
            notCompatibleWithConfigurationCache("OWASP Dependency Check plugin accesses Task.project at execution time")
        }

        // Disable aggregate task to encourage per-project scanning and avoid unsafe configuration resolution errors
        tasks.withType(org.owasp.dependencycheck.gradle.tasks.Aggregate::class.java).configureEach {
            enabled = false
        }

        if (PURGE_DATABASE) {
            tasks.named("dependencyCheckAnalyze").configure {
                dependsOn("purgeDependencyCheckDatabase")
            }
        }

        with(extensions) {
            configure<DependencyCheckExtension> {
                failBuildOnCVSS.set(FAIL_BUILDS_ON_CVSS)
                formats.set(
                    listOf(
                        ReportGenerator.Format.HTML.toString(),
                        ReportGenerator.Format.XML.toString(),
                        ReportGenerator.Format.SARIF.toString(),
                    ),
                )
                
                // Set suppression file path
                val suppressionFileLocation = rootProject.rootDir.resolve("config/owasp/owasp-suppression.xml")
                if (suppressionFileLocation.exists()) {
                    suppressionFile.set(suppressionFileLocation.absolutePath)
                }

                setEnvironmentVariables()

                // Configure data directory
                data {
                    directory.set(layout.buildDirectory.dir("dependency-check-data").get().asFile.absolutePath)
                }

                autoUpdate.set(AUTO_UPDATE)

                // Scan only production/compile classpaths, exclude test, check, coverage tools, etc.
                val validConfigurations = listOf("compileClasspath", "runtimeClasspath")
                val excludedPatterns = listOf("kover", "test", "jacoco", "detekt", "testFixtures")
                scanConfigurations.set(
                    configurations.names
                        .filter { configName ->
                            validConfigurations.any { valid -> configName.contains(valid, ignoreCase = true) } &&
                                excludedPatterns.none { excluded -> configName.contains(excluded, ignoreCase = true) }
                        }
                        .toList(),
                )

                outputDirectory.set(layout.buildDirectory.dir("reports/owasp").get())
            }
        }
    }

    private fun DependencyCheckExtension.setEnvironmentVariables() {
        val apiKeyValue = System.getenv("NVD_API_KEY") ?: run {
            println("⚠️ NVD_API_KEY not found in the environment. NVD queries may be heavily rate-limited.")
            println("   Create an API key at: https://nvd.nist.gov/vuln/data-feeds#apikey")
            null
        }
        if (apiKeyValue != null) {
            nvd {
                apiKey.set(apiKeyValue)
            }
            println("✅ NVD_API_KEY loaded from environment.")
        }
        val delayValue = System.getenv("NVD_API_DELAY")
        if (delayValue != null) {
            val delayInt = delayValue.toIntOrNull()
            if (delayInt != null) {
                if (delayInt <= 0) {
                    nvd {
                        delay.set(DEFAULT_DELAY)
                    }
                } else {
                    nvd {
                        delay.set(delayInt)
                    }
                }
            } else {
                nvd {
                    delay.set(DEFAULT_DELAY)
                }
            }
        } else {
            nvd {
                delay.set(DEFAULT_DELAY)
            }
        }
    }
}
