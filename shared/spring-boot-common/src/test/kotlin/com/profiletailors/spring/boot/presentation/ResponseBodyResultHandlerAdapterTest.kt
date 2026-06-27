package com.profiletailors.spring.boot.presentation

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerResult
import org.springframework.web.reactive.accept.RequestedContentTypeResolver
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class ResponseBodyResultHandlerAdapterTest {

    private val presenter = mockk<Presenter<TestEntity>>()
    private val resolver = mockk<RequestedContentTypeResolver>()
    private val writers = ServerCodecConfigurer.create().writers
    private val adapter = ResponseBodyResultHandlerAdapter(writers, resolver, presenter)

    // Stub methods to obtain real MethodParameters via reflection
    @Suppress("unused")
    private fun stubReturningTestEntity(): Mono<TestEntity> = Mono.empty()

    @Suppress("unused")
    private fun stubReturningString(): Mono<String> = Mono.empty()

    @Suppress("unused")
    private fun stubReturningAny(): Mono<Any> = Mono.empty()

    @Suppress("unused")
    private fun stubReturningVoid(): Mono<Unit> = Mono.empty()

    private val methodParamForTestEntity: MethodParameter by lazy {
        MethodParameter.forExecutable(
            this::class.java.getDeclaredMethod("stubReturningTestEntity"),
            -1,
        )
    }

    private val methodParamForString: MethodParameter by lazy {
        MethodParameter.forExecutable(
            this::class.java.getDeclaredMethod("stubReturningString"),
            -1,
        )
    }

    private val methodParamForAny: MethodParameter by lazy {
        MethodParameter.forExecutable(
            this::class.java.getDeclaredMethod("stubReturningAny"),
            -1,
        )
    }

    private val methodParamForVoid: MethodParameter by lazy {
        MethodParameter.forExecutable(
            this::class.java.getDeclaredMethod("stubReturningVoid"),
            -1,
        )
    }

    @Test
    fun `should support result type matching presenter type`() {
        // Given
        every { presenter.type } returns TestEntity::class
        val result = HandlerResult(Any(), Mono.just(TestEntity()), methodParamForTestEntity)

        // When/Then
        adapter.supports(result) shouldBe true
    }

    @Test
    fun `should not support different result type`() {
        // Given
        every { presenter.type } returns TestEntity::class
        val result = HandlerResult(Any(), Mono.just("string"), methodParamForString)

        // When/Then
        adapter.supports(result) shouldBe false
    }

    @Test
    fun `should handle result by delegating to presenter`() {
        // Given
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        val result = HandlerResult(Any(), Mono.just(TestEntity()), methodParamForVoid)

        coEvery { presenter.present(exchange, result) } returns Unit

        // When
        val handleResult = adapter.handleResult(exchange, result)

        // Then
        StepVerifier.create(handleResult)
            .verifyComplete()

        io.mockk.coVerify(exactly = 1) { presenter.present(exchange, result) }
    }

    @Test
    fun `should propagate presenter exception as reactor error`() {
        // Given
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        val result = HandlerResult(Any(), Mono.just(TestEntity()), methodParamForVoid)

        coEvery { presenter.present(exchange, result) } throws RuntimeException("Presenter failed")

        // When
        val handleResult = adapter.handleResult(exchange, result)

        // Then
        StepVerifier.create(handleResult)
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `should not support result when return type is supertype of presenter type`() {
        // Given
        every { presenter.type } returns TestEntity::class
        val result = HandlerResult(Any(), Mono.just(Any()), methodParamForAny)

        // When/Then
        adapter.supports(result) shouldBe false
    }

    class TestEntity
}
