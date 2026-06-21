package com.profiletailors.buildlogic.springboot

import com.profiletailors.buildlogic.ConventionPlugin
import com.profiletailors.buildlogic.extensions.catalogPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

import dev.detekt.gradle.extensions.DetektExtension

class SpringBootApplicationPlugin : ConventionPlugin {
    override fun Project.configure() {
        // Apply base Kotlin library plugin (configs toolchains, detekt, basic testing)
        apply(plugin = "com.profiletailors.kotlin.library")

        // Apply Spring-specific plugins
        apply(plugin = catalogPlugin("kotlin-spring").get().pluginId)
        apply(plugin = catalogPlugin("spring-boot").get().pluginId)
        apply(plugin = catalogPlugin("spring-dependency-management").get().pluginId)

        // Apply Jacoco
        apply(plugin = "jacoco")

        // Configure Detekt for the application module
        extensions.configure(DetektExtension::class.java) {
            buildUponDefaultConfig.set(true)
        }

        // Ensure check task runs detekt
        tasks.named("check") {
            dependsOn("detekt")
        }

        // Configure test tasks to support tags inclusion/exclusion
        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform {
                val includeTags = providers.gradleProperty("includeTags").orNull
                if (!includeTags.isNullOrBlank()) {
                    includeTags(*includeTags.split(",").map { it.trim() }.toTypedArray())
                }
                val excludeTags = providers.gradleProperty("excludeTags").orNull
                if (!excludeTags.isNullOrBlank()) {
                    excludeTags(*excludeTags.split(",").map { it.trim() }.toTypedArray())
                }
            }
        }

        // Configure standard test task
        val testTask = tasks.named("test", Test::class.java) {
            exclude("**/CucumberFastIntegrationTest.class", "**/CucumberPostgresIntegrationTest.class")
        }

        // Register BDD test tasks after project evaluation to ensure Java plugin is fully configured
        afterEvaluate {
            val javaExtension = extensions.getByType<JavaPluginExtension>()
            val testSourceSet = javaExtension.sourceSets.getByName("test")

            // Register BDD Fast Test task
            val bddFastTestTask = tasks.register("bddFastTest", Test::class.java) {
                group = "verification"
                description = "Runs fast BDD suite with H2"
                testClassesDirs = testSourceSet.output.classesDirs
                classpath = testSourceSet.runtimeClasspath
                useJUnitPlatform()
                include("**/CucumberFastIntegrationTest.class")
                shouldRunAfter(testTask)
            }

            // Register BDD Postgres Test task
            tasks.register("bddPostgresTest", Test::class.java) {
                group = "verification"
                description = "Runs Postgres BDD suite with Testcontainers"
                testClassesDirs = testSourceSet.output.classesDirs
                classpath = testSourceSet.runtimeClasspath
                useJUnitPlatform()
                include("**/CucumberPostgresIntegrationTest.class")
                shouldRunAfter(bddFastTestTask)
            }
        }

        // Configure Jacoco Coverage Verification (report generation is handled by Kover for all modules)
        tasks.named("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java) {
            violationRules {
                rule {
                    limit {
                        minimum = "0.80".toBigDecimal()
                    }
                }
            }
        }
    }
}
