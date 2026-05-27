import org.gradle.kotlin.dsl.support.listFilesOrdered

rootProject.name = "profiletailors-backend"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

buildCache {
    local {
        directory = rootDir.resolve(".gradle/build-cache")
    }
}

val defaultBuildGradleKts = "build.gradle.kts"

fun isGradleKtsProjectDirectory(directory: File) =
    directory.isDirectory &&
        (
            directory.resolve(defaultBuildGradleKts).exists() ||
                directory.resolve("${directory.name}.gradle.kts").exists()
        ) &&
        directory.relativeTo(rootDir).path.split(File.separator).none { it in excludedProjects }

fun includeGradleProjectsRecursively(directoryPath: String) {
    val baseDirectory = rootDir.resolve(directoryPath)
    if (!baseDirectory.exists()) return
    
    baseDirectory.walkTopDown()
        .maxDepth(3)
        .filter { it.isDirectory }
        .forEach { subDir ->
            includeProjectsInDirectory(subDir.path)
        }
}

fun includeProject(dir: File) {
    val projectName = calculateProjectName(dir)
    include(projectName)
    val prj = project(":$projectName")
    prj.projectDir = dir
    prj.buildFileName = if (dir.resolve("${dir.name}.gradle.kts").exists()) {
        "${dir.name}.gradle.kts"
    } else {
        defaultBuildGradleKts
    }
}

fun calculateProjectName(dir: File): String {
    val projectName = dir.relativeTo(rootDir).path.replace("/", ":")
    return if (projectName.startsWith(":")) projectName.substring(1) else projectName
}

fun includeProjectsInDirectory(directoryPath: String) {
    val baseDirectory = rootDir.resolve(directoryPath)
    if (!baseDirectory.exists()) return
    
    baseDirectory.listFilesOrdered()
        .filter { isGradleKtsProjectDirectory(it) }
        .forEach { projectDirectory ->
            includeProject(projectDirectory)
        }
}

val excludedProjects = listOf("build-logic", "wrapper")
val scanDirectories = listOf("server", "shared")

scanDirectories.forEach { includeGradleProjectsRecursively(it) }
