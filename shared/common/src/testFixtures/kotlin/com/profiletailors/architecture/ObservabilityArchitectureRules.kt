package com.profiletailors.architecture

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition

object ObservabilityArchitectureRules {
    private val observabilityVendorPackages = arrayOf(
        "org.slf4j..",
        "ch.qos.logback..",
        "org.apache.logging.log4j..",
        "io.opentelemetry..",
        "io.micrometer..",
        "kotlinx.serialization.json..",
        "tools.jackson..",
        "com.fasterxml.jackson..",
    )

    fun domainMustNotDependOnObservabilityVendors(packageRoot: String): ArchRule =
        noClassesInLayer(packageRoot, "domain")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*observabilityVendorPackages)
            .because("domain must remain independent from observability implementations")

    fun applicationMustNotDependOnObservabilityVendors(packageRoot: String): ArchRule =
        noClassesInLayer(packageRoot, "application")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*observabilityVendorPackages)
            .because("application must remain independent from observability implementations")

    fun domainMustNotDependOnOperationalEventSink(packageRoot: String): ArchRule =
        noClassesInLayer(packageRoot, "domain")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.profiletailors.observability..")
            .because("domain must not depend on the operational observability port")

    private fun noClassesInLayer(packageRoot: String, layer: String) = ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("$packageRoot..$layer..")
}
