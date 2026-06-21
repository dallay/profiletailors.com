package com.profiletailors.spring.boot

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext

class AppSpringBeanProviderTest {

    private val applicationContext = mockk<ApplicationContext>()
    private val provider = AppSpringBeanProvider(applicationContext)

    @Test
    fun `should return single instance of type`() {
        // Given
        val bean = "test-bean"
        every { applicationContext.getBeanNamesForType(String::class.java) } returns arrayOf("stringBean")
        every { applicationContext.getBean("stringBean") } returns bean

        // When
        val result = provider.getSingleInstanceOf(String::class.java)

        // Then
        result shouldBe bean
    }

    @Test
    fun `should throw exception if no bean found`() {
        // Given
        every { applicationContext.getBeanNamesForType(String::class.java) } returns emptyArray()

        // When/Then
        shouldThrow<IllegalArgumentException> {
            provider.getSingleInstanceOf(String::class.java)
        }
    }

    @Test
    fun `should throw exception if multiple beans found`() {
        // Given
        every { applicationContext.getBeanNamesForType(String::class.java) } returns arrayOf("bean1", "bean2")

        // When/Then
        shouldThrow<IllegalArgumentException> {
            provider.getSingleInstanceOf(String::class.java)
        }
    }

    @Test
    fun `should return subtypes of type`() {
        // Given
        every { applicationContext.getBeanNamesForType(CharSequence::class.java) } returns arrayOf("bean1", "bean2")
        every { applicationContext.getType("bean1") } returns String::class.java
        every { applicationContext.getType("bean2") } returns StringBuilder::class.java

        // When
        val result = provider.getSubTypesOf(CharSequence::class.java)

        // Then
        result.size shouldBe 2
        result.contains(String::class.java) shouldBe true
        result.contains(StringBuilder::class.java) shouldBe true
    }
}
