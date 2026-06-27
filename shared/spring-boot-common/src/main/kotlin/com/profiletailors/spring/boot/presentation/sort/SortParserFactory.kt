package com.profiletailors.spring.boot.presentation.sort

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import kotlin.reflect.KClass

@Component
class SortParserFactory(private val objectMapper: ObjectMapper) {
    fun <T : Any> create(clazz: KClass<T>): SortParser<T> = SortParser(clazz, objectMapper)
}

inline fun <reified T : Any> SortParserFactory.create(): SortParser<T> = create(T::class)
