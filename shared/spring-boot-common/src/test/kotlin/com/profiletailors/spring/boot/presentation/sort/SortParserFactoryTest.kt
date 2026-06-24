package com.profiletailors.spring.boot.presentation.sort

import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jsonMapper

internal class SortParserFactoryTest {

    private val factory = SortParserFactory(jsonMapper())

    @Test
    fun `should create parser for given class`() {
        val parser = factory.create(String::class)

        parser shouldNotBe null
    }

    @Test
    fun `should create parser using reified inline function`() {
        val parser = factory.create<String>()

        parser shouldNotBe null
    }
}
