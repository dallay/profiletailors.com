package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotResendableException
import com.profiletailors.smp.platformadmin.domain.InvitationNotRevocableException
import com.profiletailors.smp.platformadmin.domain.InvitationRateLimitExceededException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.UserNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyCancelledException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyConvertedException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotInvitableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import java.net.URI

@RestControllerAdvice
class AdminProblemDetailsHandler {

    private val logger = LoggerFactory.getLogger(AdminProblemDetailsHandler::class.java)

    @ExceptionHandler(PlatformAccessDeniedException::class)
    fun handle(ex: PlatformAccessDeniedException, exchange: ServerWebExchange? = null): ProblemDetail {
        logger.warn(
            "admin.access.denied path={} permission={} correlationId={}",
            exchange?.request?.path?.value() ?: "unknown",
            ex.permission.key,
            requestCorrelationId(exchange),
        )
        return problem(HttpStatus.FORBIDDEN, "PLATFORM_ACCESS_DENIED", ex.message)
    }

    @ExceptionHandler(WaitlistEntryNotFoundException::class)
    fun handle(ex: WaitlistEntryNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "WAITLIST_ENTRY_NOT_FOUND", ex.message)

    @ExceptionHandler(WaitlistEntryNotInvitableException::class)
    fun handle(ex: WaitlistEntryNotInvitableException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "WAITLIST_ENTRY_NOT_INVITABLE", ex.message)

    @ExceptionHandler(WaitlistEntryAlreadyConvertedException::class)
    fun handle(ex: WaitlistEntryAlreadyConvertedException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "WAITLIST_ENTRY_ALREADY_CONVERTED", ex.message)

    @ExceptionHandler(WaitlistEntryAlreadyCancelledException::class)
    fun handle(ex: WaitlistEntryAlreadyCancelledException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "WAITLIST_ENTRY_ALREADY_CANCELLED", ex.message)

    @ExceptionHandler(InvitationNotFoundException::class)
    fun handle(ex: InvitationNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", ex.message)

    @ExceptionHandler(InvitationNotAcceptableException::class)
    fun handle(ex: InvitationNotAcceptableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "INVITATION_NOT_ACCEPTABLE", ex.message ?: "Invitation is unavailable.")

    @ExceptionHandler(InvitationAlreadyActiveException::class)
    fun handle(ex: InvitationAlreadyActiveException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "INVITATION_ALREADY_ACTIVE", ex.message)

    @ExceptionHandler(InvitationNotResendableException::class)
    fun handle(ex: InvitationNotResendableException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "INVITATION_NOT_RESENDABLE", ex.message)

    @ExceptionHandler(InvitationNotRevocableException::class)
    fun handle(ex: InvitationNotRevocableException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "INVITATION_NOT_REVOCABLE", ex.message)

    @ExceptionHandler(InvitationRateLimitExceededException::class)
    fun handle(ex: InvitationRateLimitExceededException): ProblemDetail =
        problem(HttpStatus.TOO_MANY_REQUESTS, "INVITATION_RATE_LIMIT_EXCEEDED", ex.message)

    @ExceptionHandler(UserNotFoundException::class)
    fun handle(ex: UserNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.message)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handle(ex: IllegalArgumentException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.message)

    private fun requestCorrelationId(exchange: ServerWebExchange?): String {
        val candidates = listOf(
            exchange?.request?.headers?.getFirst("X-Correlation-Id"),
            exchange?.request?.headers?.getFirst("X-Request-Id"),
            exchange?.request?.headers?.getFirst("X-Trace-Id"),
            exchange?.request?.headers?.getFirst("Correlation-Id"),
        )
        return candidates.firstNotNullOfOrNull { candidate ->
            sanitizeCorrelationId(candidate).takeIf { it != UNKNOWN_CORRELATION_ID }
        } ?: UNKNOWN_CORRELATION_ID
    }

    private fun sanitizeCorrelationId(raw: String?): String {
        val value = raw?.trim() ?: return UNKNOWN_CORRELATION_ID
        return if (value.length <= MAX_CORRELATION_ID_LENGTH && value.matches(VALID_CORRELATION_ID)) {
            value
        } else {
            UNKNOWN_CORRELATION_ID
        }
    }

    private fun problem(status: HttpStatus, code: String, detail: String?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase).apply {
            type = URI.create("urn:profiletailors:error:$code")
            properties = mapOf("code" to code)
        }

    companion object {
        private const val MAX_CORRELATION_ID_LENGTH = 64
        private const val UNKNOWN_CORRELATION_ID = "unknown"
        private val VALID_CORRELATION_ID = Regex("^[A-Za-z0-9._:-]{1,$MAX_CORRELATION_ID_LENGTH}$")
    }
}
