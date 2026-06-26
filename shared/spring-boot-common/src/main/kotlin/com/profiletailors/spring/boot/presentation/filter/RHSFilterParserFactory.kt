package com.profiletailors.spring.boot.presentation.filter

import com.profiletailors.common.domain.presentation.filter.RHSFilterParser
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import kotlin.reflect.KClass

@Component
class RHSFilterParserFactory(private val objectMapper: ObjectMapper) {
    fun <T : Any> create(clazz: KClass<T>): RHSFilterParser<T> = RHSFilterParser(clazz, objectMapper)
}

inline fun <reified T : Any> RHSFilterParserFactory.create(): RHSFilterParser<T> = this.create(T::class)
