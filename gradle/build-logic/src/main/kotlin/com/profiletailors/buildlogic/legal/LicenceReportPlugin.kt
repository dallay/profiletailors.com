package com.profiletailors.buildlogic.legal

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.TextReportRenderer
import com.profiletailors.buildlogic.ConventionPlugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

// Licences whose presence should cause the build to fail (incompatible with AGPL-3.0).
private val BLOCKED_LICENCES = listOf(
    "GPL-2.0-only",
    "GPL-2.0",
    "GNU General Public License, version 2",
    "GNU General Public License v2.0 only",
    // Add BSL-1.1, SSPL-1.0 here if any dependency adopts them.
)

@Suppress("unused")
internal class LicenceReportPlugin : ConventionPlugin {
    override fun Project.configure() {
        apply(plugin = "com.github.jk1.dependency-license-report")

        val reportDirectory = layout.buildDirectory.dir("reports/dependency-licence")
        val jsonReport = reportDirectory.map { it.file("dependency-licence.json") }
        val textReport = reportDirectory.map { it.file("dependency-licence.txt") }

        configure<LicenseReportExtension> {
            outputDir = reportDirectory.get().asFile.absolutePath
            renderers = arrayOf<ReportRenderer>(
                JsonReportRenderer("dependency-licence.json"),
                TextReportRenderer("dependency-licence.txt"),
            )
            filters = arrayOf(LicenseBundleNormalizer())
            // Include all runtime + compile configurations; exclude test-only deps.
            configurations = arrayOf("runtimeClasspath", "compileClasspath")
            excludeOwnGroup = true
            allowedLicensesFile = null
        }

        // Fail the build if a blocked licence is found in the generated JSON report.
        tasks.named("generateLicenseReport").configure {
            doLast {
                val report = jsonReport.get().asFile
                if (!report.exists()) return@doLast
                val content = report.readText()
                val violations = BLOCKED_LICENCES.filter { blocked -> content.contains(blocked) }
                if (violations.isNotEmpty()) {
                    error(
                        "Dependency licence check FAILED. The following blocked licences were " +
                            "found in the dependency graph: $violations\n" +
                            "Review ${textReport.get().asFile} " +
                            "and replace the offending dependency or obtain a legal exception.",
                    )
                }
            }
        }
    }
}
