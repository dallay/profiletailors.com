package com.profiletailors.common.domain.regexp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.ParseException

internal class TokenizerTest {

    private lateinit var tokenizer: Tokenizer

    @BeforeEach
    fun setUp() {
        tokenizer = Tokenizer()
            .add("^(%)") { WildcardToken(it) }
            .add("^(_)") { WildcharToken(it) }
            .add("^([^%_]+)") { StringToken(it) }
    }

    @Test
    fun `should tokenize plain string`() {
        val tokens = tokenizer.tokenize("hello")

        assertThat(tokens).hasSize(1)
        assertThat(tokens.first().value).isEqualTo("hello")
    }

    @Test
    fun `should tokenize wildcard characters`() {
        val tokens = tokenizer.tokenize("%")

        assertThat(tokens).hasSize(1)
        assertThat(tokens.first().value).isEqualTo("%")
    }

    @Test
    fun `should tokenize wildchar characters`() {
        val tokens = tokenizer.tokenize("_")

        assertThat(tokens).hasSize(1)
        assertThat(tokens.first().value).isEqualTo("_")
    }

    @Test
    fun `should tokenize mixed pattern correctly`() {
        val tokens = tokenizer.tokenize("hello%world_!").toList()

        // hello -> StringToken, % -> WildcardToken, world -> StringToken,
        // _ -> WildcharToken, ! -> StringToken
        assertThat(tokens).hasSize(5)
        assertThat(tokens[0].value).isEqualTo("hello")
        assertThat(tokens[1].value).isEqualTo("%")
        assertThat(tokens[2].value).isEqualTo("world")
        assertThat(tokens[3].value).isEqualTo("_")
        assertThat(tokens[4].value).isEqualTo("!")
    }

    @Test
    fun `should tokenize multiple wildcards`() {
        val tokens = tokenizer.tokenize("%%__").toList()

        assertThat(tokens).hasSize(4)
        assertThat(tokens[0].value).isEqualTo("%")
        assertThat(tokens[1].value).isEqualTo("%")
        assertThat(tokens[2].value).isEqualTo("_")
        assertThat(tokens[3].value).isEqualTo("_")
    }

    @Test
    fun `should throw ParseException on character that cannot be tokenized`() {
        // Register a pattern that doesn't match the given token
        val strictTokenizer = Tokenizer()
            .add("^([a-z]+)") { StringToken(it) }

        assertThatThrownBy { strictTokenizer.tokenize("123") }
            .isInstanceOf(ParseException::class.java)
    }

    @Test
    fun `should return empty list for empty string`() {
        val tokens = tokenizer.tokenize("")

        assertThat(tokens).isEmpty()
    }
}
