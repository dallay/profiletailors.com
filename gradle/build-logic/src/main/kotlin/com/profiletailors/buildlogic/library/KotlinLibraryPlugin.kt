package com.profiletailors.buildlogic.library

import com.profiletailors.buildlogic.ConventionPlugin
import com.profiletailors.buildlogic.extensions.catalogPlugin
import com.profiletailors.buildlogic.extensions.commonExtensions
import com.profiletailors.buildlogic.extensions.commonTasks
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

class KotlinLibraryPlugin : ConventionPlugin {
    override fun Project.configure() {
        apply(plugin = catalogPlugin("kotlin-jvm").get().pluginId)
        apply(plugin = catalogPlugin("detekt").get().pluginId)
        apply(plugin = "com.profiletailors.security.owasp")
        apply(plugin = catalogPlugin("kover").get().pluginId)

        repositories.mavenCentral()

        with(extensions) {
            commonExtensions()
        }

        tasks.commonTasks()

        extensions.configure<DetektExtension> {
            config.setFrom(files(rootDir.resolve("detekt.yml")))
            buildUponDefaultConfig.set(false)
            allRules.set(false)
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

    }
}
