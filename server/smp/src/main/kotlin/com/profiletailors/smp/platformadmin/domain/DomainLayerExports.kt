package com.profiletailors.smp.platformadmin.domain

import org.springframework.modulith.NamedInterface

/**
 * Marker class exposing the domain layer to the notifications module.
 *
 * Named `DomainLayerExports` so the HexagonalArchTest rules
 * [domainLayerShouldNotDependOnSpring] / [domainLayerShouldNotDependOnInfrastructureFrameworks]
 * exclude this class (rule filter: haveSimpleNameNotEndingWith("Exports")).
 *
 * Spring Modulith requires a [@NamedInterface("domain")] on a class residing in the
 * `platformadmin.domain` package for the `platformadmin :: domain` allowed-dependency
 * syntax to resolve correctly.
 */
@NamedInterface("domain")
internal class DomainLayerExports
