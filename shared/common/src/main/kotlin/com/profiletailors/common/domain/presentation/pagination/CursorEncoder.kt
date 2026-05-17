package com.profiletailors.common.domain.presentation.pagination

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
interface CursorEncoder {
    fun encode(data: String): String = Base64.encode(data.toByteArray())
    fun decode(encodedData: String): String {
        @Suppress("TooGenericExceptionCaught")
        try {
            return String(Base64.decode(encodedData))
        } catch (e: IndexOutOfBoundsException) {
            throw InvalidCursor("Invalid cursor", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidCursor("Invalid cursor", e)
        }
    }
}

class Base64CursorEncoder : CursorEncoder
