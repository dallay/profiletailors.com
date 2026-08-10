package com.profiletailors.smp

import com.lemonappdev.konsist.api.Konsist
import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.DomainEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * DDD conformance tests — ADR-0016 (Aggregates Communicate by Identity Only).
 *
 * Verifies the invariant that an aggregate holds identity references (`WorkspaceId`,
 * `PrincipalId`, ...) to aggregates in OTHER bounded contexts — never direct object references
 * (`workspace: Workspace`, `principal: Principal`). Direct references would couple two aggregates
 * into a single transactional boundary and break referential transparency.
 *
 * The test scans the production source set only (test fixtures legitimately materialise full
 * aggregates to set up scenarios). It restricts the scan to `@AggregateRoot` and `@DomainEntity`
 * classes — the rule applies to domain entities, not to value objects, ports, repositories, or
 * pure-logic classes that are free to hold whatever references they need.
 *
 * Companion tests covering the remaining rules live in:
 * - `AggregateBoundaryTest` (ADR-0015 — aggregate root as sole entry point)
 * - `ValueObjectImmutabilityTest` (ADR-0017 — value object immutability)
 *
 * Tag `@Tag("ddd-conformance")` causes the test to run as part of `./gradlew :server:smp:test`
 * by default.
 */
@Tag("ddd-conformance")
internal class IdentityOnlyAggregateCommunicationTest {

    private val pkgPrefix = "com.profiletailors.smp."

    /**
     * Spec scenario "Direct cross-context reference on @AggregateRoot/@DomainEntity fails the build":
     * a property declared on a `@AggregateRoot` or `@DomainEntity` class whose declared type lives in
     * a different bounded context MUST be an identity type — its source name MUST end with `Id`,
     * `Ids`, or `Identifier` (with optional `?` for nullable). Examples:
     *
     * - ✅ `val workspaceId: String` (cross-context identity, even though typed as `String`)
     * - ✅ `val ownerPrincipalId: String` (cross-context identity)
     * - ❌ `val workspace: Workspace` (cross-context direct object reference)
     * - ❌ `val principal: Principal` (cross-context direct object reference)
     *
     * Same-context references are allowed: within a bounded context, multiple aggregates may
     * hold direct references to each other for tight transactional coupling. The rule targets
     * cross-context coupling only.
     */
    @Test
    fun aggregatesCommunicateByIdentityAcrossContexts() {
        val scope = Konsist.scopeFromProduction()
        val offenders = sortedSetOf<String>()

        val guardedClasses = scope.classes(includeNested = false, includeLocal = false)
            .filter { it.hasAnnotationOf(AggregateRoot::class) || it.hasAnnotationOf(DomainEntity::class) }

        for (source in guardedClasses) {
            collectCrossContextReferences(source, offenders)
        }

        assertThat(offenders)
            .withFailMessage(
                "ADR-0016 violated: an @AggregateRoot or @DomainEntity class must reference " +
                    "other aggregates by identity (Id, Ids, Identifier), never by direct object " +
                    "reference, when the referenced type lives in another bounded context. " +
                    "Offending references:\n" + offenders.joinToString("\n"),
            )
            .isEmpty()
    }

    private fun collectCrossContextReferences(
        source: com.lemonappdev.konsist.api.declaration.KoClassDeclaration,
        offenders: MutableSet<String>,
    ) {
        val sourceContext = source.containingFile.packagee?.name
            ?.substringAfter("com.profiletailors.smp.")
            ?.substringBefore('.')
            .orEmpty()

        if (sourceContext.isBlank()) return

        for (prop in source.properties(includeNested = false)) {
            checkPropertyForCrossContextViolation(source, prop, sourceContext, offenders)
        }
    }

    private fun checkPropertyForCrossContextViolation(
        source: com.lemonappdev.konsist.api.declaration.KoClassDeclaration,
        prop: com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration,
        sourceContext: String,
        offenders: MutableSet<String>,
    ) {
        val targetType = prop.type?.sourceType ?: return

        if (!targetType.startsWith(pkgPrefix)) return

        val targetContext = targetType
            .substringAfter("com.profiletailors.smp.")
            .substringBefore('.')

        if (targetContext == sourceContext) return

        val isIdentity = targetType.endsWith("Id") ||
            targetType.endsWith("Identifier") ||
            targetType.endsWith("Id?") ||
            targetType.endsWith("Ids")

        if (!isIdentity) {
            offenders += "  - ${source.name}.${prop.name}: $targetType"
        }
    }
}
