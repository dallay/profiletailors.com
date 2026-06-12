package com.profiletailors.smp

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Architecture tests that enforce hexagonal layer boundaries in the smp module.
 *
 * Rules:
 * - domain: pure Kotlin, no Spring, no upward dependencies
 * - application: depends on domain only (within smp packages)
 * - infrastructure: may depend on domain and application
 * - every bounded context exposes domain, application, and infrastructure packages
 *
 * Bounded contexts are auto-discovered from the package tree so that
 * adding a new context automatically includes it in the validation.
 */
internal class HexagonalArchTest {

    private lateinit var importedClasses: JavaClasses

    private companion object {
        private val THREE_LAYER_PACKAGE = Regex(
            "com\\.profiletailors\\.smp\\.[a-z]+\\.(domain|application|infrastructure)",
        )
    }

    @BeforeEach
    fun setUp() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.profiletailors.smp")
    }

    private fun discoverBoundedContexts(): List<String> =
        importedClasses.map { it.packageName }
            .filterNot { it.isBlank() }
            .mapNotNull { THREE_LAYER_PACKAGE.matchEntire(it) }
            .map { it.value.removePrefix("com.profiletailors.smp.").substringBefore('.') }
            .distinct()
            .sorted()

    @Test
    fun domainLayerShouldNotDependOnSpring() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .because("domain layer must be pure Kotlin with no Spring dependencies")
            .check(importedClasses)
    }

    @Test
    fun domainLayerShouldNotDependOnApplicationOrInfrastructure() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..")
            .because("domain layer must not depend on application or infrastructure layers")
            .check(importedClasses)
    }

    @Test
    fun applicationLayerShouldNotDependOnInfrastructure() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because("application layer must not depend on infrastructure layer")
            .check(importedClasses)
    }

    @Test
    fun boundedContextsShouldExposeAllLayers() {
        val contexts = discoverBoundedContexts()
        assertTrue(
            contexts.isNotEmpty(),
            "No bounded contexts discovered — package tree may be empty or misconfigured",
        )
        contexts.forEach { context ->
            val domainClasses = importedClasses.filter {
                it.packageName.startsWith("com.profiletailors.smp.$context.domain")
            }
            assertTrue(
                domainClasses.isNotEmpty(),
                "bounded context '$context' must have a domain layer",
            )

            val applicationClasses = importedClasses.filter {
                it.packageName.startsWith("com.profiletailors.smp.$context.application")
            }
            assertTrue(
                applicationClasses.isNotEmpty(),
                "bounded context '$context' must have an application layer",
            )

            val infrastructureClasses = importedClasses.filter {
                it.packageName.startsWith("com.profiletailors.smp.$context.infrastructure")
            }
            assertTrue(
                infrastructureClasses.isNotEmpty(),
                "bounded context '$context' must have an infrastructure layer",
            )
        }
    }
}
