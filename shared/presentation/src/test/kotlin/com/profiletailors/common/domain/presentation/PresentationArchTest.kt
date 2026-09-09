package com.profiletailors.common.domain.presentation

import com.profiletailors.architecture.ObservabilityArchitectureRules
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PresentationArchTest {

    private lateinit var importedClasses: JavaClasses

    @BeforeEach
    fun setUp() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "com.profiletailors.common.domain",
            )
    }

    @Test
    fun domainShouldNotDependOnObservabilityFrameworks() {
        ObservabilityArchitectureRules.domainMustNotDependOnObservabilityVendors("com.profiletailors.common")
            .check(importedClasses)

        ObservabilityArchitectureRules.domainMustNotDependOnOperationalEventSink("com.profiletailors.common")
            .check(importedClasses)
    }

    @Test
    fun applicationShouldNotDependOnObservabilityImplementations() {
        ObservabilityArchitectureRules.applicationMustNotDependOnObservabilityVendors("com.profiletailors.common")
            .allowEmptyShould(true)
            .check(importedClasses)
    }
}
