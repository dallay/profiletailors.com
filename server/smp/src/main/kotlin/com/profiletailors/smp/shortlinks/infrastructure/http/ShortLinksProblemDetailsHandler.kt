package com.profiletailors.smp.shortlinks.infrastructure.http

import com.profiletailors.smp.shortlinks.application.LinkNotFoundApplicationException
import com.profiletailors.smp.shortlinks.domain.AliasAlreadyExistsException
import com.profiletailors.smp.shortlinks.domain.IdempotencyKeyConflictException
import com.profiletailors.smp.shortlinks.domain.IdempotencyRequestInProgressException
import com.profiletailors.smp.shortlinks.domain.InvalidDestinationUrlException
import com.profiletailors.smp.shortlinks.domain.LinkNotFoundException
import com.profiletailors.smp.shortlinks.domain.LinkStateTransitionException
import com.profiletailors.smp.shortlinks.domain.LinkVersionConflictException
import com.profiletailors.smp.shortlinks.domain.ReservedAliasException
import com.profiletailors.smp.shortlinks.domain.ShortCodeCollisionExhaustedException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackageClasses = [LinkManagementController::class])
class ShortLinksProblemDetailsHandler {

    @ExceptionHandler(LinkNotFoundApplicationException::class)
    fun handle(ex: LinkNotFoundApplicationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Link not found.").apply {
            title = "Link not found"
            setProperty("code", "LINK_NOT_FOUND")
        }

    @ExceptionHandler(LinkNotFoundException::class)
    fun handle(ex: LinkNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Link not found.").apply {
            title = "Link not found"
            setProperty("code", "LINK_NOT_FOUND")
        }

    @ExceptionHandler(InvalidDestinationUrlException::class)
    fun handle(ex: InvalidDestinationUrlException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Invalid URL.").apply {
            title = "Invalid destination URL"
            setProperty("code", "INVALID_DESTINATION_URL")
        }

    @ExceptionHandler(AliasAlreadyExistsException::class)
    fun handle(ex: AliasAlreadyExistsException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Alias already exists.").apply {
            title = "Alias already exists"
            setProperty("code", "ALIAS_ALREADY_EXISTS")
        }

    @ExceptionHandler(ReservedAliasException::class)
    fun handle(ex: ReservedAliasException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Alias is reserved.").apply {
            title = "Reserved alias"
            setProperty("code", "RESERVED_ALIAS")
        }

    @ExceptionHandler(LinkVersionConflictException::class)
    fun handle(ex: LinkVersionConflictException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, ex.message ?: "Version conflict.").apply {
            title = "Version conflict"
            setProperty("code", "VERSION_CONFLICT")
        }

    @ExceptionHandler(LinkStateTransitionException::class)
    fun handle(ex: LinkStateTransitionException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Invalid state transition.").apply {
            title = "Invalid state transition"
            setProperty("code", "INVALID_STATE_TRANSITION")
        }

    @ExceptionHandler(ShortCodeCollisionExhaustedException::class)
    fun handle(ex: ShortCodeCollisionExhaustedException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        ex.message ?: "Code generation failed.",
    ).apply {
        title = "Short code generation failed"
        setProperty("code", "CODE_GENERATION_EXHAUSTED")
    }

    @ExceptionHandler(IdempotencyKeyConflictException::class)
    fun handle(ex: IdempotencyKeyConflictException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Idempotency conflict.").apply {
            title = "Idempotency key conflict"
            setProperty("code", "IDEMPOTENCY_KEY_CONFLICT")
        }

    @ExceptionHandler(IdempotencyRequestInProgressException::class)
    fun handle(ex: IdempotencyRequestInProgressException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Request is in progress.").apply {
            title = "Idempotency request in progress"
            setProperty("code", "IDEMPOTENCY_REQUEST_IN_PROGRESS")
        }
}
