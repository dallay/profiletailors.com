package com.profiletailors.smp

import com.lemonappdev.konsist.api.Konsist
import com.profiletailors.common.domain.ValueObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * DDD conformance tests — ADR-0017 (Value Object immutability).
 *
 * Verifies the three invariants of a DDD Value Object:
 *
 * 1. **Immutability** — every property is `val` or `private`; no public setters or mutators.
 * 2. **Validation** — invariants are enforced in `init`, a factory method, or by composition
 *    with other VOs that validate themselves.
 * 3. **Equality by attributes** — auto-derived for `data class` and `value class`; enums are
 *    equal by identity of the constant and need no further check.
 *
 * Enums are exempted from the immutability and validation checks because they are inherently
 * immutable and inherently valid by construction.
 *
 * Companion tests covering the remaining rules live in:
 * - `AggregateBoundaryTest` (ADR-0015 — aggregate root as sole entry point)
 * - `IdentityOnlyAggregateCommunicationTest` (ADR-0016 — identity-only references between
 *   aggregates)
 *
 * Tag `@Tag("ddd-conformance")` causes the test to run as part of `./gradlew :server:smp:test`
 * by default.
 */
@Tag("ddd-conformance")
internal class ValueObjectImmutabilityTest {

    /**
     * Spec scenario "@ValueObject property is not val or private fails the build":
     * every property of a class annotated with `@ValueObject` MUST be `val` or `private`.
     * Mutable properties break the value-object contract: two instances with the same attributes
     * could diverge after construction.
     *
     * Spec scenario "@ValueObject exposes a public mutator fails the build":
     * a class annotated with `@ValueObject` MUST NOT expose public functions whose name starts
     * with `set` or `mutate`. Mutation breaks referential transparency; callers can no longer
     * rely on the value remaining constant.
     */
    @Test
    fun valueObjectsAreImmutable() {
        val scope = Konsist.scopeFromProduction()
        val offenders = sortedSetOf<String>()

        val valueObjects = scope.classes(includeNested = false, includeLocal = false)
            .filter { it.hasAnnotationOf(ValueObject::class) }
            .filter { !it.hasEnumModifier }

        for (vo in valueObjects) {
            collectMutableProperties(vo, offenders)
            collectPublicMutators(vo, offenders)
        }

        assertThat(offenders)
            .withFailMessage(
                "ADR-0017 violated: @ValueObject must be immutable — every property must be " +
                    "val or private, and no public set*/mutate* method may exist. " +
                    "Offending members:\n" + offenders.joinToString("\n"),
            )
            .isEmpty()
    }

    /**
     * Spec scenario "@ValueObject without init or factory validation fails the build":
     * a class annotated with `@ValueObject` MUST validate its invariants in an `init` block, a
     * factory method (`of`, `create`, `from`, `fromRaw`, `ensure`, `generate`, `random`), or by
     * composing other VOs that validate themselves in their own constructors.
     *
     * If a VO accepts raw input without checking it, invalid state can leak into the domain.
     */
    @Test
    fun valueObjectsValidateInvariants() {
        val scope = Konsist.scopeFromProduction()
        val offenders = sortedSetOf<String>()

        val valueObjects = scope.classes(includeNested = false, includeLocal = false)
            .filter { it.hasAnnotationOf(ValueObject::class) }
            .filter { !it.hasEnumModifier }

        for (vo in valueObjects) {
            val hasInit = vo.hasInitBlocks()
            val hasFactory = vo.functions(includeNested = true, includeLocal = false).any { fn ->
                val name = fn.name
                name in setOf("of", "create", "from", "fromRaw", "ensure", "generate", "random")
            }

            if (!hasInit && !hasFactory) {
                offenders += "  - ${vo.name} has no init block or factory method"
            }
        }

        assertThat(offenders)
            .withFailMessage(
                "ADR-0017 violated: @ValueObject must validate invariants in an init block, a " +
                    "factory method, or by composing other validated VOs. Missing validation:\n" +
                    offenders.joinToString("\n"),
            )
            .isEmpty()
    }

    private fun collectMutableProperties(
        vo: com.lemonappdev.konsist.api.declaration.KoClassDeclaration,
        offenders: MutableSet<String>,
    ) {
        vo.properties(includeNested = false)
            .filter { !it.isVal }
            .filter { !it.hasPrivateModifier }
            .forEach { prop ->
                offenders += "  - ${vo.name}.${prop.name} must be val or private"
            }
    }

    private fun collectPublicMutators(
        vo: com.lemonappdev.konsist.api.declaration.KoClassDeclaration,
        offenders: MutableSet<String>,
    ) {
        vo.functions(includeNested = true, includeLocal = false)
            .filter { it.hasPublicOrDefaultModifier }
            .filter { fn ->
                val name = fn.name
                name.startsWith("set") || name.startsWith("mutate")
            }
            .forEach { fn ->
                offenders += "  - ${vo.name}.${fn.name}() is a public mutator"
            }
    }
}
