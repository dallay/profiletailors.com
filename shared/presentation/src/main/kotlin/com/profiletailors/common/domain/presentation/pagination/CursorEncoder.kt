package com.profiletailors.common.domain.presentation.pagination

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Encodes and decodes opaque pagination cursors.
 *
 * Default implementation uses Base64 encoding to produce cursors that
 * are safe for URLs and HTTP headers.
 *
 * @see InvalidCursor
 */
@OptIn(ExperimentalEncodingApi::class)
interface CursorEncoder {
    /** Encode a string into an opaque cursor. */
    fun encode(data: String): String = Base64.encode(data.toByteArray())

    /** Decode an opaque cursor back to its original string. Throws [InvalidCursor] on malformed input. */
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

/** Default [CursorEncoder] using Base64. */
class Base64CursorEncoder : CursorEncoder
