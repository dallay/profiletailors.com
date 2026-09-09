package com.profiletailors.ratelimit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RatelimitArchTest {

    private lateinit var importedClasses: JavaClasses

    @BeforeEach
    fun setUp() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.profiletailors.ratelimit")
    }

    @Test
    fun domainShouldNotDependOnApplicationOrInfrastructure() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..")
            .because("ratelimit domain must not depend on application or infrastructure")
            .check(importedClasses)
    }

    @Test
    fun applicationShouldNotDependOnInfrastructure() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because("ratelimit application must not depend on infrastructure")
            .check(importedClasses)
    }

    @Test
    fun domainShouldNotDependOnObservabilityFrameworks() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.slf4j..",
                "ch.qos.logback..",
                "org.apache.logging.log4j..",
                "io.opentelemetry..",
                "io.micrometer..",
                "tools.jackson..",
                "com.fasterxml.jackson..",
                "com.profiletailors.observability..",
            )
            .because("ratelimit domain must stay independent from observability frameworks")
            .check(importedClasses)
    }

    @Test
    fun applicationShouldNotDependOnObservabilityImplementations() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.slf4j..",
                "ch.qos.logback..",
                "org.apache.logging.log4j..",
                "io.opentelemetry..",
                "io.micrometer..",
                "tools.jackson..",
                "com.fasterxml.jackson..",
            )
            .because("ratelimit application must use the observability sink port instead of implementations")
            .check(importedClasses)
    }
}
