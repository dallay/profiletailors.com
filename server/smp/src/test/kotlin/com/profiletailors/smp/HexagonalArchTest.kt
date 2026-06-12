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
 */
internal class HexagonalArchTest {

    private lateinit var importedClasses: JavaClasses

    private val boundedContexts = listOf(
        "authorization",
        "credentials",
        "governance",
        "identity",
        "platform",
        "publishing",
        "tenancy",
        "audit",
        "observability",
    )

    @BeforeEach
    fun setUp() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.profiletailors.smp")
    }

    @Test
    fun domainLayerShouldNotDependOnSpring() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "org.springframework.r2dbc..")
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
        boundedContexts.forEach { context ->
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
