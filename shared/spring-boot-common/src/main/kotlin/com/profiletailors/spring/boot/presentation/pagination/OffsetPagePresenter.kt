package com.profiletailors.spring.boot.presentation.pagination

import com.profiletailors.common.domain.presentation.pagination.OffsetPageResponse
import com.profiletailors.spring.boot.presentation.Presenter
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.HandlerResult
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Suppress("UNCHECKED_CAST")
@Component
class OffsetPagePresenter(private val objectMapper: ObjectMapper) : Presenter<OffsetPageResponse<*>> {
    override val type = OffsetPageResponse::class

    override suspend fun present(exchange: ServerWebExchange, result: HandlerResult) {
        val mono = result.returnValue as? Mono<OffsetPageResponse<*>>
            ?: return
        val returnValue = mono.awaitSingleOrNull() ?: return
        val headers = exchange.response.headers
        val additional = mutableListOf<String>()

        if (returnValue.total != null) {
            headers["Total-Count"] = returnValue.total.toString()
            additional.add("Total-Count")
        }
        if (returnValue.page != null) {
            headers["Page"] = returnValue.page.toString()
            additional.add("Page")
        }
        headers["Per-Page"] = returnValue.perPage.toString()
        additional.add("Per-Page")

        val existing = headers.getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)
        val merged = (
            existing
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty() + additional
            )
            .distinct()
            .joinToString(", ")
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, merged)
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

        val response = exchange.response
        val dataBuffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(returnValue.data))
        response.writeWith(Mono.just(dataBuffer)).awaitSingleOrNull()
    }
}
