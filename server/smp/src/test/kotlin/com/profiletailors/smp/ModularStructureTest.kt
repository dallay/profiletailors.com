package com.profiletailors.smp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * Spring Modulith verification test.
 *
 * This test ensures that:
 * - All modules are properly defined
 * - Module boundaries are respected
 * - No unwanted dependencies exist between modules
 * - Only explicitly exposed packages (via @NamedInterface) are accessible
 * - Newly added bounded contexts such as publishing stay within verified Modulith seams
 */
@Tag("modularity")
class ModularStructureTest {

    private val modules = ApplicationModules.of(SmpApplication::class.java)

    private val expectedLayerInterfaces = mapOf(
        "audit" to setOf("application", "domain", "infrastructure"),
        "authorization" to setOf("application", "domain", "infrastructure"),
        "credentials" to setOf("application", "domain", "infrastructure"),
        "governance" to setOf("application", "domain", "infrastructure"),
        "identity" to setOf("application", "domain", "infrastructure"),
        "media" to setOf("application", "domain", "infrastructure"),
        "observability" to setOf("application", "domain", "infrastructure"),
        "platform" to setOf("application", "domain", "infrastructure"),
        "publishing" to setOf("application", "domain", "infrastructure"),
        "tenancy" to setOf("application", "domain", "infrastructure"),
    )

    @Test
    fun `verifies modular structure`() {
        // This will fail if there are any violations of module boundaries
        modules.verify()
    }

    @Test
    fun `preserves layer named interfaces`() {
        val actualLayerInterfaces = modules
            .filter { it.identifier.toString() in expectedLayerInterfaces }
            .associate { module ->
                module.identifier.toString() to module.namedInterfaces
                    .filter { it.isNamed }
                    .map { it.name }
                    .toSet()
            }

        assertThat(actualLayerInterfaces).isEqualTo(expectedLayerInterfaces)
    }

    @Test
    @Disabled("Manual test - prints module structure to console")
    fun `prints module structure`() {
        // Useful for understanding the detected modules and their relationships
        println("\n=== Spring Modulith Module Structure ===\n")
        modules.forEach { module ->
            println(module)
            println("---")
        }
    }

    @Test
    @Disabled("Manual test - generates documentation files")
    fun `generates module documentation`() {
        // Generates PlantUML diagrams and AsciiDoc documentation
        // Output will be in build/spring-modulith-docs/
        Documenter(modules)
            .writeDocumentation()
            .writeIndividualModulesAsPlantUml()
    }
}
