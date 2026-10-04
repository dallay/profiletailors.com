package com.profiletailors.buildlogic.security

import com.profiletailors.buildlogic.extensions.catalogVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Declares the patched Jackson BOMs as platforms.
 *
 * Unlike [enforcePatchedJacksonVersions], which rewrites versions at
 * resolution time, platform declarations are visible to static readers
 * (Dependabot's updater requires updatable declarations in the snapshot and
 * errors with dependency_not_found otherwise). Both stay in sync through the
 * jackson/jackson2 version catalog entries.
 */
internal fun Project.addPatchedJacksonPlatforms() {
    val jackson3 = catalogVersion("jackson")
    val jackson2 = catalogVersion("jackson2")
    dependencies {
        add("implementation", platform("tools.jackson:jackson-bom:$jackson3"))
        add("implementation", platform("com.fasterxml.jackson:jackson-bom:$jackson2"))
    }
}

/**
 * Forces patched Jackson versions across every Spring module.
 *
 * Spring Boot BOMs lag behind patched releases (4.0.8 manages the vulnerable
 * tools.jackson 3.1.x line), and Dependabot evaluates declared dependency
 * metadata rather than each module's local resolution rules. Centralizing the
 * pins here keeps server/smp and all shared Spring modules on the same
 * patched lines. Resolved versions stay under test in SpringBootLibraryPluginTest
 * and server/smp's verifySecurityVersions task.
 */
internal fun Project.enforcePatchedJacksonVersions() {
    val jackson3 = catalogVersion("jackson")
    val jackson2 = catalogVersion("jackson2")
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group.startsWith("com.fasterxml.jackson")) {
                useVersion(if (requested.name == "jackson-annotations") "2.22" else jackson2)
            }
            if (requested.group.startsWith("tools.jackson")) {
                useVersion(jackson3)
            }
        }
    }
}
