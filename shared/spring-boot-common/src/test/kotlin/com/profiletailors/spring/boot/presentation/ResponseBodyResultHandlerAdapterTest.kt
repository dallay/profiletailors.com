package com.profiletailors.spring.boot.presentation

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.web.reactive.HandlerResult
import org.springframework.web.reactive.accept.RequestedContentTypeResolver
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.reflect.KClass

class ResponseBodyResultHandlerAdapterTest {

    private val presenter = mockk<Presenter<TestEntity>>()
    private val resolver = mockk<RequestedContentTypeResolver>()
    private val adapter = ResponseBodyResultHandlerAdapter(emptyList(), resolver, presenter)

    @Test
    fun `should support result type matching presenter type`() {
        // Given
        val methodParameter = mockk<MethodParameter>()
        coEvery { methodParameter.nestedParameterType } returns TestEntity::class.java
        coEvery { presenter.type } returns TestEntity::class
        val result = HandlerResult(Any(), Mono.just(TestEntity()), methodParameter)

        // When/Then
        adapter.supports(result) shouldBe true
    }

    @Test
    fun `should not support different result type`() {
        // Given
        val methodParameter = mockk<MethodParameter>()
        coEvery { methodParameter.nestedParameterType } returns String::class.java
        coEvery { presenter.type } returns TestEntity::class
        val result = HandlerResult(Any(), Mono.just("string"), methodParameter)

        // When/Then
        adapter.supports(result) shouldBe false
    }

    @Test
    fun `should handle result by delegating to presenter`() {
        // Given
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        val result = HandlerResult(Any(), Mono.just(TestEntity()), mockk())

        coEvery { presenter.present(exchange, result) } returns Unit

        // When
        val handleResult = adapter.handleResult(exchange, result)

        // Then
        StepVerifier.create(handleResult)
            .verifyComplete()

        coEvery { presenter.present(exchange, result) }
    }

    class TestEntity
}
