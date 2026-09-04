package com.profiletailors.smp.platformadmin.domain

import org.springframework.modulith.NamedInterface

/**
 * Marker class exposing the domain layer to the notifications module.
 *
 * Excluded from HexagonalArchTest rules
 * [domainLayerShouldNotDependOnSpring] / [domainLayerShouldNotDependOnInfrastructureFrameworks]
 * via explicit class-name predicate: haveSimpleName != "DomainLayerExports".
 *
 * Spring Modulith requires a [@NamedInterface("domain")] on a class residing in the
 * `platformadmin.domain` package for the `platformadmin :: domain` allowed-dependency
 * syntax to resolve correctly.
 */
@NamedInterface("domain")
internal class DomainLayerExports
