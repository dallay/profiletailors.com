package com.profiletailors.spring.boot.presentation

import kotlinx.coroutines.reactor.mono
import org.springframework.http.codec.HttpMessageWriter
import org.springframework.web.reactive.HandlerResult
import org.springframework.web.reactive.accept.RequestedContentTypeResolver
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

open class ResponseBodyResultHandler<T : Any>(
    writers: List<HttpMessageWriter<*>>,
    resolver: RequestedContentTypeResolver,
    private val presenter: Presenter<T>,
) : ResponseBodyResultHandler(writers, resolver) {
    override fun supports(result: HandlerResult): Boolean =
        result.returnType.getGeneric(0).rawClass == presenter.type.java

    override fun handleResult(exchange: ServerWebExchange, result: HandlerResult): Mono<Void> = mono {
        presenter.present(exchange, result)
    }
        .flatMap { Mono.empty() }
}
