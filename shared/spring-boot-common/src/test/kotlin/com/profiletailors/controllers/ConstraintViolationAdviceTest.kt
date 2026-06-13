package com.profiletailors.controllers

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.ElementKind
import jakarta.validation.Path
import jakarta.validation.Path.Node
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConstraintViolationAdviceTest {

    private val advice = ConstraintViolationAdvice()

    @Test
    fun `should build problem detail with field errors for each violation`() {
        val violations: Set<ConstraintViolation<*>> = setOf(
            FakeViolation("name", "must not be blank"),
            FakeViolation("age", "must be greater than or equal to 18"),
        )
        val ex = ConstraintViolationException("validation failed", violations)

        val pd = advice.handleConstraintViolation(ex)

        assertEquals(400, pd.status)
        assertEquals("validation failed", pd.title)
        assertEquals(
            "Request parameter validation failed. Please check the provided values.",
            pd.detail,
        )
        assertEquals(URI.create("https://profiletailors.com/errors/validation/constraint-violation"), pd.type)
        assertEquals("VALIDATION", pd.properties!![ERROR_CATEGORY])
        assertEquals("error.validation.failed", pd.properties!![MESSAGE_KEY])
        assertNotNull(pd.properties!![TIMESTAMP])

        @Suppress("UNCHECKED_CAST")
        val errors = pd.properties!!["errors"] as List<Map<String, Any?>>
        val fields = errors.map { it["field"] as String }.toSet()
        assertEquals(setOf("name", "age"), fields)
    }

    /** Minimal [ConstraintViolation] for test purposes. */
    private class FakeViolation(
        private val field: String,
        private val msg: String,
    ) : ConstraintViolation<Any> {
        override fun getMessage() = msg
        override fun getMessageTemplate() = msg
        override fun getRootBean() = this
        override fun getRootBeanClass() = Any::class.java
        override fun getLeafBean() = Unit
        override fun getPropertyPath(): Path = FakePath(field)
        override fun getInvalidValue() = ""
        override fun getConstraintDescriptor() = error("not used")
        override fun <T : Any?> unwrap(type: Class<T>?) = error("not used")
        override fun getExecutableParameters() = null
        override fun getExecutableReturnValue() = null
        override fun toString() = "$field: $msg"
    }

    /** Minimal [Path] that returns a single node for the given field name. */
    private class FakePath(private val field: String) : Path {
        override fun iterator() = listOf<Node>(object : Node {
            override fun getName() = field
            override fun isInIterable() = false
            override fun getIndex(): Int? = null
            override fun getKey(): Any? = null
            override fun getKind() = ElementKind.PROPERTY
            @Suppress("UNCHECKED_CAST")
            override fun <T : Node> `as`(type: Class<T>) = this as T
            override fun toString() = field
        }).toMutableList().iterator()

        override fun toString() = field
    }
}
