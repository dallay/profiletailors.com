package com.profiletailors.smp

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Architecture tests that guard the smp module's component-scan conventions.
 *
 * The rules in this file are the regression guard rail for
 * `fix-restrictive-component-scan`. They make the bug impossible to reintroduce silently by
 * failing `./gradlew :server:smp:test` whenever the smp application layer starts using Spring's
 * `@Component` or `@Repository` instead of the project marker
 * `com.profiletailors.common.domain.Service`, or whenever a config class under
 * `infrastructure.config.*` declares a nested `@ComponentScan` with a restrictive
 * `includeFilters` block.
 */
internal class ComponentScanArchTest {

    private lateinit var importedClasses: JavaClasses

    @BeforeEach
    fun setUp() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.profiletailors.smp")
    }

    /**
     * Spec scenario "Build fails on Spring `@Component` in the smp application layer":
     * no class under `com.profiletailors.smp.*.application.*` may be annotated with
     * Spring's `@Component`. Use-case handlers in the smp application layer must use the
     * project marker `com.profiletailors.common.domain.Service`.
     */
    @Test
    fun applicationLayerShouldNotUseSpringComponent() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .beAnnotatedWith(org.springframework.stereotype.Component::class.java)
            .because(
                "smp application-layer classes must use the project marker " +
                    "com.profiletailors.common.domain.Service instead of Spring's @Component",
            )
            .check(importedClasses)
    }

    /**
     * Spec scenario "Build fails on Spring `@Repository` in the smp application layer":
     * hard fail — persistence adapters belong in infrastructure and must never be introduced in
     * application classes.
     */
    @Test
    fun applicationLayerShouldNotUseSpringRepository() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .beAnnotatedWith(org.springframework.stereotype.Repository::class.java)
            .because(
                "smp application-layer classes must not carry Spring's @Repository; " +
                    "persistence adapters belong in the infrastructure layer",
            )
            .check(importedClasses)
    }

    /**
     * Spec scenario "Build fails on a nested `@ComponentScan` with `includeFilters` under
     * `infrastructure.config.*`": no class under
     * `com.profiletailors.*.infrastructure.config.*` may declare a nested
     * `@ComponentScan` annotation. The rule chains two `ArchRuleDefinition` calls; both
     * use `.allowEmptyShould(true)` because the desired steady state is for the codebase
     * to have zero `infrastructure.config.*` classes annotated with `@ComponentScan`.
     */
    @Test
    fun infrastructureConfigShouldNotDeclareNestedIncludeFilters() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..infrastructure.config..")
            .should()
            .beAnnotatedWith(org.springframework.context.annotation.ComponentScan::class.java)
            .because(
                "infrastructure.config classes must rely on Spring's default scan; " +
                    "a nested @ComponentScan is the bug we are guarding against",
            )
            .allowEmptyShould(true)
            .check(importedClasses)
    }
}
