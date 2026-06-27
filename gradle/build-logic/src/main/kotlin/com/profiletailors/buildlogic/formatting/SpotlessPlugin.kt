package com.profiletailors.buildlogic.formatting

import com.diffplug.gradle.spotless.SpotlessExtension
import com.profiletailors.buildlogic.ConventionPlugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Spotless + ktlint convention plugin.
 *
 * Applies ktlint-based formatting to all Kotlin and Kotlin Gradle DSL sources.
 * This is the **auto-fix** layer — it handles everything mechanical:
 *   - line length
 *   - indentation
 *   - spacing (brackets, keywords, operators)
 *   - import ordering & wildcard imports
 *   - trailing whitespace
 *   - file endings
 *
 * **Rule:** this plugin owns ALL formatting. Detekt should have formatting
 * rules DISABLED to avoid overlap and conflicting expectations.
 */
class SpotlessPlugin : ConventionPlugin {

    override fun Project.configure() {
        pluginManager.apply("com.diffplug.spotless")

        extensions.configure<SpotlessExtension> {
            kotlin {
                ktlint("1.5.0")
                    .editorConfigOverride(
                        mapOf(
                            "max_line_length" to "120",
                            "indent_size" to "4",
                            "continuation_indent_size" to "4",
                            // no-wildcard-imports is NOT auto-fixable by ktlint,
                            // so it stays disabled here and is handled by detekt.
                            // max-line-length IS auto-fixable and ktlint handles it.
                            "ktlint_standard_no-wildcard-imports" to "disabled",
                        ),
                    )
                trimTrailingWhitespace()
                endWithNewline()
            }

            kotlinGradle {
                ktlint()
            }
        }
    }
}
