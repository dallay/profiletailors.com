package com.profiletailors.leadcapture.waitlist

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import org.junit.jupiter.api.Test

internal class LeadCaptureArchTest {

    @Test
    fun `lead-capture modules must not depend on Spring`() {
        val rule: ArchRule = ArchRuleDefinition.noClasses()
            .that()
            .resideInAnyPackage("..leadcapture..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .because("lead-capture shared modules must be framework-free (ADR-0011)")
            .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `lead-capture modules must not depend on R2DBC`() {
        val rule: ArchRule = ArchRuleDefinition.noClasses()
            .that()
            .resideInAnyPackage("..leadcapture..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.r2dbc..")
            .because("lead-capture shared modules must be framework-free (ADR-0011)")
            .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `lead-capture modules must not depend on server`() {
        val rule: ArchRule = ArchRuleDefinition.noClasses()
            .that()
            .resideInAnyPackage("..leadcapture..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.profiletailors.smp..")
            .because("shared modules must not depend on server (ADR-0011, dependency is one-way)")
            .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    companion object {
        private val importedClasses: JavaClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.profiletailors.leadcapture")
    }
}
