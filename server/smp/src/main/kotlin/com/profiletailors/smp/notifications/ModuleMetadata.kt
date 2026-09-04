package com.profiletailors.smp.notifications

import org.springframework.modulith.ApplicationModule

/**
 * Notifications bounded context — bridge module.
 *
 * ## Architecture note (hybrid context)
 *
 * Unlike most bounded contexts, **notifications' domain and application layers live in
 * `shared/notifications`** (under `com.profiletailors.notifications.*`), NOT under
 * `com.profiletailors.smp.notifications.*`.
 *
 * The `smp` module ONLY provides infrastructure adapters: the R2DBC repository, the
 * event bus adapter that publishes `WaitlistEntryJoined` on the bus, the
 * identity-email dispatcher, and the welcome email consumer. This separation exists
 * because:
 *
 * 1. The notifications domain is intended to be framework-agnostic and reusable across
 *    deployment targets (ADR-0011).
 * 2. The smp module acts as the Spring Boot integration shell.
 *
 * The architecture test `HexagonalArchTest.boundedContextsShouldExposeAllLayers()`
 * exempts `notifications` from the "all three layers in smp" assertion via
 * `HYBRID_CONTEXTS`.
 *
 * @see com.profiletailors.notifications
 */
@ApplicationModule(
    allowedDependencies = [
        "identity :: application",
        "identity :: infrastructure",
        "platformadmin",
        "platformadmin :: contracts",
        "platformadmin :: domain",
    ],
)
internal class ModuleMetadata
