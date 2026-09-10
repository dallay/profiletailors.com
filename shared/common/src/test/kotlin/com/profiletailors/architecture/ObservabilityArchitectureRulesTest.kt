package com.profiletailors.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObservabilityArchitectureRulesTest {

    private lateinit var importedClasses: JavaClasses

    @BeforeEach
    fun setUp() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.profiletailors.common")
    }

    @Test
    fun `should enforce observability boundaries for common layers`() {
        ObservabilityArchitectureRules.domainMustNotDependOnObservabilityVendors("com.profiletailors.common")
            .check(importedClasses)
        ObservabilityArchitectureRules.applicationMustNotDependOnObservabilityVendors("com.profiletailors.common")
            .allowEmptyShould(true)
            .check(importedClasses)
        ObservabilityArchitectureRules.domainMustNotDependOnOperationalEventSink("com.profiletailors.common")
            .check(importedClasses)
    }
}
