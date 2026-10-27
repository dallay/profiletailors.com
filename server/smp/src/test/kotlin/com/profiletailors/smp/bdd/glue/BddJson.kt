package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory

private val mapType = TypeFactory.defaultInstance().constructMapType(
    Map::class.java,
    String::class.java,
    Any::class.java,
)

fun ObjectMapper.readMap(content: String): Map<String, Any?> = readValue(content, mapType)

fun ObjectMapper.readMap(content: ByteArray): Map<String, Any?> = readValue(content, mapType)
