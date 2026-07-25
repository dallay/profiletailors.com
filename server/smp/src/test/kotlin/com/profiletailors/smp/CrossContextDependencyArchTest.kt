package com.profiletailors.smp

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * Guards cross-context dependencies from the application layer.
 *
 * Policy:
 * - application -> other-context infrastructure is forbidden.
 * - application -> other-context application/domain imports must be explicit and allowlisted.
 *
 * This freezes the current architecture surface so we can tighten it incrementally
 * without accidental new couplings.
 */
internal class CrossContextDependencyArchTest {

    private lateinit var importedClasses: JavaClasses

    private data class AllowlistedCrossContextImport(val file: String, val to: String, val layer: String)

    private companion object {
        private val APPLICATION_PACKAGE = Regex(
            "com\\.profiletailors\\.smp\\.([a-z]+)\\.application(?:\\..*)?",
        )

        private val TARGET_PACKAGE = Regex(
            "com\\.profiletailors\\.smp\\.([a-z]+)\\.(domain|application|infrastructure)(?:\\..*)?",
        )

        private val SOURCE_PACKAGE = Regex("^package\\s+com\\.profiletailors\\.smp\\.([a-z]+)\\.application(?:\\..*)?\\s*$")
        private val IMPORT_LINE = Regex("^import\\s+com\\.profiletailors\\.smp\\.([a-z]+)\\.(application|domain|infrastructure)\\..*")

        private val ALLOWED_FILE_IMPORTS = emptySet<AllowlistedCrossContextImport>()
    }

    @BeforeEach
    fun setUp() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.profiletailors.smp")
    }

    @Test
    fun applicationLayerShouldNotDependOnOtherContextsInfrastructure() {
        val violations = mutableSetOf<String>()

        importedClasses.forEach { sourceClass ->
            val sourceMatch = APPLICATION_PACKAGE.matchEntire(sourceClass.packageName) ?: return@forEach
            val fromContext = sourceMatch.groupValues[1]

            sourceClass.directDependenciesFromSelf.forEach { dependency ->
                val targetMatch = TARGET_PACKAGE.matchEntire(dependency.targetClass.packageName) ?: return@forEach
                val targetContext = targetMatch.groupValues[1]
                val targetLayer = targetMatch.groupValues[2]

                if (fromContext != targetContext && targetLayer == "infrastructure") {
                    violations += "${sourceClass.fullName} -> ${dependency.targetClass.fullName}"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Cross-context application->infrastructure dependencies are forbidden:\n${violations.sorted().joinToString("\n")}",
        )
    }

    @Test
    fun applicationLayerCrossContextImportsShouldBeFileAllowlisted() {
        val appSourcesRoot = Path.of("server", "smp", "src", "main", "kotlin", "com", "profiletailors", "smp")
        val observed = Files.walk(appSourcesRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .map { path -> path.normalize() }
                .flatMap { path ->
                    val lines = Files.readAllLines(path)
                    val packageLine = lines.firstOrNull { it.startsWith("package ") } ?: return@flatMap emptyList<AllowlistedCrossContextImport>().stream()
                    val sourceMatch = SOURCE_PACKAGE.matchEntire(packageLine.trim())
                        ?: return@flatMap emptyList<AllowlistedCrossContextImport>().stream()
                    val fromContext = sourceMatch.groupValues[1]
                    val relativeFile = path.toString().replace('\\\\', '/')

                    lines.asSequence()
                        .map { it.trim() }
                        .mapNotNull { importLine ->
                            val targetMatch = IMPORT_LINE.matchEntire(importLine) ?: return@mapNotNull null
                            val targetContext = targetMatch.groupValues[1]
                            val targetLayer = targetMatch.groupValues[2]
                            if (targetContext == fromContext || targetLayer == "infrastructure") {
                                null
                            } else {
                                AllowlistedCrossContextImport(
                                    file = relativeFile,
                                    to = targetContext,
                                    layer = targetLayer,
                                )
                            }
                        }
                        .distinct()
                        .asIterable()
                        .stream()
                }
                .collect(Collectors.toSet())
        }

        val unexpected = observed - ALLOWED_FILE_IMPORTS
        val missing = ALLOWED_FILE_IMPORTS - observed

        assertTrue(
            unexpected.isEmpty() && missing.isEmpty(),
            buildString {
                append("Cross-context file import allowlist drift detected.")
                if (unexpected.isNotEmpty()) {
                    append("\nUnexpected imports:\n")
                    append(
                        unexpected
                            .sortedBy { "${it.file}:${it.to}:${it.layer}" }
                            .joinToString("\n") { "${it.file} -> ${it.to} :: ${it.layer}" },
                    )
                }
                if (missing.isNotEmpty()) {
                    append("\nMissing allowlisted imports (allowlist cleanup needed):\n")
                    append(
                        missing
                            .sortedBy { "${it.file}:${it.to}:${it.layer}" }
                            .joinToString("\n") { "${it.file} -> ${it.to} :: ${it.layer}" },
                    )
                }
            },
        )
    }
}