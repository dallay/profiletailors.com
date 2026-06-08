package com.profiletailors.buildlogic.springboot

import com.profiletailors.buildlogic.ConventionPlugin
import com.profiletailors.buildlogic.extensions.catalogPlugin
import com.profiletailors.buildlogic.extensions.catalogVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

class SpringBootLibraryPlugin : ConventionPlugin {
    override fun Project.configure() {
        // Apply our base Kotlin library plugin
        apply(plugin = "com.profiletailors.kotlin.library")

        // Apply Spring-specific plugins
        apply(plugin = catalogPlugin("kotlin-spring").get().pluginId)
        apply(plugin = catalogPlugin("spring-dependency-management").get().pluginId)

        val springBootVersion = catalogVersion("springBoot")

        dependencies {
            // Manage dependency versions using the Spring Boot BOM
            add("implementation", platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        }
    }
}
