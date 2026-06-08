package com.profiletailors.buildlogic.extensions

import com.profiletailors.buildlogic.AppConfiguration
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val Project.libs get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun Project.catalogVersion(alias: String) = libs.findVersion(alias).get().toString()
fun Project.catalogLib(alias: String) = libs.findLibrary(alias).get()
fun Project.catalogPlugin(alias: String) = libs.findPlugin(alias).get()

fun ExtensionContainer.commonExtensions() {
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(AppConfiguration.jvmTargetStr.toInt()))
        }
    }
}

fun TaskContainer.commonTasks() {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = AppConfiguration.jvmTargetStr
        targetCompatibility = AppConfiguration.jvmTargetStr
    }
    withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(AppConfiguration.jvmTarget)
        compilerOptions.freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}
