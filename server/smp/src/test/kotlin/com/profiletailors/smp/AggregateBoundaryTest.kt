package com.profiletailors.smp

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.profiletailors.common.domain.DomainEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * DDD conformance tests — ADR-0015 (Aggregate Root as sole entry point).
 *
 * Verifies the invariant that no internal entity of an aggregate can be reached from outside its
 * bounded context. Internal references inside the same context (application ports, infrastructure
 * persistence adapters) are explicitly allowed because persistence adapters MUST materialize
 * entities, and ports expose the entity contract that handlers depend on.
 *
 * Companion tests covering the remaining rules live in:
 * - `IdentityOnlyAggregateCommunicationTest` (ADR-0016 — identity-only references between
 *   aggregates)
 * - `ValueObjectImmutabilityTest` (ADR-0017 — value object invariants)
 *
 * Tag `@Tag("ddd-conformance")` causes the test to run as part of `./gradlew :server:smp:test`
 * by default.
 */
@Tag("ddd-conformance")
internal class AggregateBoundaryTest {

    private val contextPackage = Regex("""com\.profiletailors\.smp\.([a-z]+)\.domain""")

    /**
     * Spec scenario "Cross-context import of internal entity fails the build":
     * a class annotated with `@DomainEntity` MUST NOT be imported from a file in a different
     * bounded context. This is the canonical DDD aggregate-boundary violation: one context
     * reaches into another's internal cluster of objects, bypassing the Aggregate Root and
     * breaching transactional consistency.
     *
     * The scan is intentionally restricted to the production source set: tests legitimately
     * reference entities to construct fixtures, and Konsist would otherwise flag every test that
     * materialises a `WorkspaceMembership` for an authorization scenario.
     */
    @Test
    fun internalEntitiesAreNotReferencedFromOtherBoundedContexts() {
        val scope = Konsist.scopeFromProduction()
        val offenders = sortedSetOf<String>()

        val domainPackages = scope.packages
            .map { it.name }
            .distinct()
            .filter { contextPackage.matches(it) }

        for (pkgName in domainPackages) {
            collectCrossContextImports(scope, pkgName, offenders)
        }

        assertThat(offenders)
            .withFailMessage(
                "ADR-0015 violated: @DomainEntity must not be imported from another bounded " +
                    "context. The Aggregate Root in the owning context is the only legal entry " +
                    "point. Offending imports:\n" + offenders.joinToString("\n"),
            )
            .isEmpty()
    }

    /**
     * Spec scenario "Public mutator on @DomainEntity fails the build":
     * a class annotated with `@DomainEntity` MUST NOT expose public functions whose name starts
     * with `set` or `update`. State changes inside the aggregate MUST go through the Aggregate
     * Root, which preserves invariants. Auto-generated `copy()`, `componentN()`, and
     * `equals/hashCode/toString` do not match these prefixes and are allowed.
     */
    @Test
    fun internalEntitiesDoNotExposePublicMutators() {
        val scope = Konsist.scopeFromProduction()
        val offenders = sortedSetOf<String>()

        val entities = scope.classes(includeNested = false, includeLocal = false)
            .filter { it.hasAnnotationOf(DomainEntity::class) }

        for (entity in entities) {
            collectPublicMutators(entity, offenders)
        }

        assertThat(offenders)
            .withFailMessage(
                "ADR-0015 violated: @DomainEntity must not expose public mutators (set*, " +
                    "update*). State changes must flow through the Aggregate Root. Offending " +
                    "mutators:\n" + offenders.joinToString("\n"),
            )
            .isEmpty()
    }

    private fun collectCrossContextImports(scope: KoScope, pkgName: String, offenders: MutableSet<String>) {
        val context = contextPackage.matchEntire(pkgName)?.groupValues?.get(1) ?: return

        val internals = scope.classes(includeNested = false, includeLocal = false)
            .filter { it.containingFile.packagee?.name == pkgName }
            .filter { it.hasAnnotationOf(DomainEntity::class) }
            .map { it.name }
            .toSet()

        if (internals.isEmpty()) return

        scope.files
            .filter { file ->
                val fileContext = file.packagee?.name
                    ?.substringAfter("com.profiletailors.smp.")
                    ?.substringBefore('.')
                    .orEmpty()
                fileContext.isNotBlank() && fileContext != context
            }
            .forEach { file ->
                file.imports
                    .filter { imp ->
                        internals.any { entity -> imp.name.endsWith(".$entity") }
                    }
                    .forEach { imp ->
                        offenders += "  - ${file.path} imports ${imp.name}"
                    }
            }
    }

    private fun collectPublicMutators(
        entity: com.lemonappdev.konsist.api.declaration.KoClassDeclaration,
        offenders: MutableSet<String>,
    ) {
        entity.functions(includeNested = false, includeLocal = false)
            .filter { fn -> fn.name.startsWith("set") || fn.name.startsWith("update") }
            .filter { fn -> fn.hasPublicOrDefaultModifier }
            .forEach { mutator ->
                offenders += "  - ${entity.name}.${mutator.name}()"
            }
    }
}
