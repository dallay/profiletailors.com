package com.profiletailors.common.domain.regexp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SqlLikeTranspilerTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "hello   | ^\\Qhello\\E$",
            "test123 | ^\\Qtest123\\E$",
            "hello%  | ^\\Qhello\\E.*$",
            "%world  | ^.*\\Qworld\\E$",
            "%oo%    | ^.*\\Qoo\\E.*$",
            "h%ld    | ^\\Qh\\E.*\\Qld\\E$",
            "h_llo   | ^\\Qh\\E.\\Qllo\\E$",
            "_ello   | ^.\\Qello\\E$",
            "____    | ^....$",
        ],
    )
    fun `should transpile like pattern to regex`(pattern: String, expectedRegex: String) {
        val regex = SqlLikeTranspiler.toRegEx(pattern)

        assertThat(regex).isEqualTo(expectedRegex)
    }

    @Test
    fun `should produce regex that matches expected strings`() {
        val regex = Regex(SqlLikeTranspiler.toRegEx("Hello%World"))

        assertThat(regex.matches("HelloWorld")).isTrue
        assertThat(regex.matches("Hello Cruel World")).isTrue
        assertThat(regex.matches("HelloWorld!")).isFalse
    }

    @Test
    fun `should handle wildcard character`() {
        val regex = Regex(SqlLikeTranspiler.toRegEx("H_llo"))

        assertThat(regex.matches("Hallo")).isTrue
        assertThat(regex.matches("Hello")).isTrue
        assertThat(regex.matches("Hllo")).isFalse
        assertThat(regex.matches("Heello")).isFalse
    }

    @Test
    fun `should escape special regex characters`() {
        val regex = Regex(SqlLikeTranspiler.toRegEx("test.value"))

        assertThat(regex.matches("testXvalue")).isFalse
        assertThat(regex.matches("test.value")).isTrue
    }

    @Test
    fun `should handle empty pattern`() {
        val regex = SqlLikeTranspiler.toRegEx("")

        assertThat(regex).isEqualTo("^$")
    }

    @Test
    fun `should escape square brackets with escape syntax`() {
        val regex = Regex(SqlLikeTranspiler.toRegEx("h[%]llo"))

        assertThat(regex.matches("h%llo")).isTrue
        assertThat(regex.matches("hello")).isFalse
        assertThat(regex.matches("hXllo")).isFalse
    }
}
