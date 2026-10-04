package com.profiletailors.smp.shortlinks.infrastructure.http

import com.profiletailors.smp.shortlinks.application.LinkNotFoundApplicationException
import com.profiletailors.smp.shortlinks.domain.AliasAlreadyExistsException
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.IdempotencyKeyConflictException
import com.profiletailors.smp.shortlinks.domain.IdempotencyRequestInProgressException
import com.profiletailors.smp.shortlinks.domain.InvalidDestinationUrlException
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkNotFoundException
import com.profiletailors.smp.shortlinks.domain.LinkStateTransitionException
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.LinkVersionConflictException
import com.profiletailors.smp.shortlinks.domain.ReservedAliasException
import com.profiletailors.smp.shortlinks.domain.ShortCodeCollisionExhaustedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

internal class ShortLinksProblemDetailsHandlerTest {
    private val handler = ShortLinksProblemDetailsHandler()
    private val linkId = LinkId(UUID.fromString("0199b1ca-0000-7000-8000-000000000001"))

    @Test
    fun `maps application not found to 404`() {
        val detail = handler.handle(LinkNotFoundApplicationException("AbC123"))

        assertEquals(HttpStatus.NOT_FOUND.value(), detail.status)
        assertEquals("LINK_NOT_FOUND", detail.properties?.get("code"))
    }

    @Test
    fun `maps domain not found to 404`() {
        val detail = handler.handle(LinkNotFoundException(linkId))

        assertEquals(HttpStatus.NOT_FOUND.value(), detail.status)
        assertEquals("LINK_NOT_FOUND", detail.properties?.get("code"))
    }

    @Test
    fun `maps invalid destination to 422`() {
        val detail = handler.handle(InvalidDestinationUrlException("ftp://x", "bad scheme", null))

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), detail.status)
        assertEquals("INVALID_DESTINATION_URL", detail.properties?.get("code"))
    }

    @Test
    fun `maps taken alias to 409`() {
        val detail = handler.handle(AliasAlreadyExistsException("taken", DomainId.fromHost("short.example")))

        assertEquals(HttpStatus.CONFLICT.value(), detail.status)
        assertEquals("ALIAS_ALREADY_EXISTS", detail.properties?.get("code"))
    }

    @Test
    fun `maps reserved alias to 409`() {
        val detail = handler.handle(ReservedAliasException("admin"))

        assertEquals(HttpStatus.CONFLICT.value(), detail.status)
        assertEquals("RESERVED_ALIAS", detail.properties?.get("code"))
    }

    @Test
    fun `maps version conflict to 412`() {
        val detail = handler.handle(LinkVersionConflictException(linkId, 1, 2))

        assertEquals(HttpStatus.PRECONDITION_FAILED.value(), detail.status)
        assertEquals("VERSION_CONFLICT", detail.properties?.get("code"))
    }

    @Test
    fun `maps invalid transition to 409`() {
        val detail = handler.handle(
            LinkStateTransitionException(linkId, LinkStatus.ACTIVE, LinkStatus.DISABLED),
        )

        assertEquals(HttpStatus.CONFLICT.value(), detail.status)
        assertEquals("INVALID_STATE_TRANSITION", detail.properties?.get("code"))
    }

    @Test
    fun `maps exhausted generation to 503`() {
        val detail = handler.handle(ShortCodeCollisionExhaustedException(5))

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), detail.status)
        assertEquals("CODE_GENERATION_EXHAUSTED", detail.properties?.get("code"))
    }

    @Test
    fun `maps idempotency conflict to 409`() {
        val detail = handler.handle(IdempotencyKeyConflictException("key-1"))

        assertEquals(HttpStatus.CONFLICT.value(), detail.status)
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", detail.properties?.get("code"))
    }

    @Test
    fun `maps in-progress idempotency to 409`() {
        val detail = handler.handle(IdempotencyRequestInProgressException("key-1"))

        assertEquals(HttpStatus.CONFLICT.value(), detail.status)
        assertEquals("IDEMPOTENCY_REQUEST_IN_PROGRESS", detail.properties?.get("code"))
    }
}
