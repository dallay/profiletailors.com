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

    private fun discoverBoundedContexts(): List<String> = importedClasses.map { it.packageName }
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
            .and()
            .haveSimpleNameNotEndingWith("ModuleMetadata")
            .and()
            .haveSimpleNameNotEndingWith("package-info")
            .and()
            .haveSimpleNameNotContaining("InvitationIssued")
            .and()
            .haveSimpleNameNotContaining("DomainLayerExports")
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
    fun applicationLayerShouldNotDependOnSpringConfigurationAnnotations() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.beans.factory.annotation..",
                "org.springframework.context.annotation..",
                "org.springframework.scheduling.annotation..",
                "org.springframework.stereotype..",
            )
            .because("application layer must not depend on Spring configuration or stereotype annotations")
            .check(importedClasses)
    }

    @Test
    fun applicationLayerShouldNotUseSpringValueAnnotation() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .beAnnotatedWith(org.springframework.beans.factory.annotation.Value::class.java)
            .because("application layer configuration values must be wired in infrastructure, not via Spring @Value")
            .check(importedClasses)
    }

    /**
     * Guards against the real violation: application layer using Spring R2DBC, HTTP, or Security
     * imports directly — bypassing the domain port/abstraction layer.
     */
    @Test
    fun applicationLayerShouldNotDependOnSpringR2dbcHttpOrSecurity() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.r2dbc..",
                "io.r2dbc..",
                "org.springframework.http..",
                "org.springframework.security..",
            )
            .because(
                "application layer must remain framework-agnostic; " +
                    "persistence uses domain repository ports, HTTP transport belongs in infrastructure",
            )
            .check(importedClasses)
    }

    /**
     * Guards against reactive/infrastructure imports leaking into application via coroutine adapters.
     */
    @Test
    fun applicationLayerShouldNotDependOnReactorOrCoroutinesReactor() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "reactor..",
                "kotlinx.coroutines.reactor..",
            )
            .because(
                "application layer must not depend on reactive/infrastructure transports; " +
                    "use domain ports instead",
            )
            .check(importedClasses)
    }

    /**
     * Guards against Spring Security base classes leaking into application exceptions.
     */
    @Test
    fun applicationLayerShouldNotExtendSpringSecurityClasses() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.security.authentication..")
            .because(
                "application business exceptions must extend domain exceptions, " +
                    "not Spring Security types like BadCredentialsException",
            )
            .check(importedClasses)
    }

    /**
     * Guards domain from any infrastructure framework — even narrower than the existing Spring check.
     */
    @Test
    fun domainLayerShouldNotDependOnInfrastructureFrameworks() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..domain..")
            .and()
            .haveSimpleNameNotEndingWith("ModuleMetadata")
            .and()
            .haveSimpleNameNotEndingWith("package-info")
            .and()
            .haveSimpleNameNotContaining("InvitationIssued")
            .and()
            .haveSimpleNameNotContaining("DomainLayerExports")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "io.r2dbc..",
                "reactor..",
                "kotlinx.coroutines.reactor..",
                "jakarta.persistence..",
                "javax.persistence..",
            )
            .because("domain layer must stay pure Kotlin")
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
