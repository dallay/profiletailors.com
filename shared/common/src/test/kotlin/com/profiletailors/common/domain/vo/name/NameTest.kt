package com.profiletailors.common.domain.vo.name

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.stream.Stream

internal class NameTest {

    @Test
    fun `should create a valid name and lastname`() {
        val names = mapOf(
            "Yuniel" to "Acosta Pérez",
            "Neil" to "O'Neil",
            "Ramón" to "González Pérez",
            "Ñico" to "López",
        )

        names.forEach { (firstname, lastname) ->
            val name = Name.of(firstname, lastname)
            assertEquals(firstname, name.firstName.value)
            assertEquals(lastname, name.lastName?.value)
            assertEquals(name.fullName(), "$firstname $lastname")
        }
    }

    @Test
    fun `should not build without firstname`() {
        assertThrows(IllegalArgumentException::class.java) {
            Name.of("", "Acosta Pérez")
        }
    }

    @Test
    fun `should not build without lastname`() {
        assertThrows(IllegalArgumentException::class.java) {
            Name.of("Yuniel", "")
        }
    }

    @Test
    fun `should not build with firstname greater than 150 characters`() {
        val firstname = (1..256).joinToString("") { "a" }
        assertThrows(IllegalArgumentException::class.java) {
            Name.of(firstname, "Acosta Pérez")
        }
    }

    @Test
    fun `should not build with lastname greater than 150 characters`() {
        val lastname = (1..256).joinToString("") { "a" }
        assertThrows(IllegalArgumentException::class.java) {
            Name.of("Yuniel", lastname)
        }
    }

    @Test
    fun `should not build with firstname and lastname greater than 150 characters`() {
        val firstname = (1..256).joinToString("") { "a" }
        val lastname = (1..256).joinToString("") { "a" }
        assertThrows(IllegalArgumentException::class.java) {
            Name.of(firstname, lastname)
        }
    }

    @Test
    fun `should get fullname`() {
        val name = Name.of("Yuniel", "Acosta Pérez")
        assertEquals("Yuniel Acosta Pérez", name.fullName())
    }

    @Test
    fun shouldSortNames() {
        val names: List<Name> = Stream
            .of(Name.of("paul", "Dupond"), Name.of("jean", "Dupont"), Name.of("jean", "Dupond"))
            .sorted()
            .toList()
        assertThat(names).containsExactly(
            Name.of("jean", "Dupond"),
            Name.of("jean", "Dupont"),
            Name.of("paul", "Dupond"),
        )
    }
}
