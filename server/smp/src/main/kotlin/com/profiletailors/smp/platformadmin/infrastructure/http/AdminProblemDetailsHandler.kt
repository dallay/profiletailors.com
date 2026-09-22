package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.identity.application.InvalidPrincipalStatusTransitionException
import com.profiletailors.smp.identity.application.PrincipalNotFoundException
import com.profiletailors.smp.identity.application.PrincipalVersionConflictException
import com.profiletailors.smp.platformadmin.application.OptimisticLockException
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyConflictException
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyInProgressException
import com.profiletailors.smp.platformadmin.application.handler.UserControlStateConflictException
import com.profiletailors.smp.platformadmin.domain.InvitationAcceptanceFailureCode
import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotResendableException
import com.profiletailors.smp.platformadmin.domain.InvitationNotRevocableException
import com.profiletailors.smp.platformadmin.domain.InvitationRateLimitExceededException
import com.profiletailors.smp.platformadmin.domain.InvitationVersionConflictException
import com.profiletailors.smp.platformadmin.domain.NotificationNotFoundForRetryException
import com.profiletailors.smp.platformadmin.domain.NotificationNotRetryableException
import com.profiletailors.smp.platformadmin.domain.NotificationRetryConflictException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.UserNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyCancelledException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyConvertedException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotInvitableException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryVersionConflictException
import com.profiletailors.smp.platformadmin.domain.WorkspaceNotFoundException
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

    @ExceptionHandler(WaitlistEntryVersionConflictException::class)
    fun handle(ex: WaitlistEntryVersionConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "WAITLIST_ENTRY_VERSION_CONFLICT", ex.message)

    @ExceptionHandler(PrincipalVersionConflictException::class)
    fun handle(ex: PrincipalVersionConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "USER_ACCOUNT_VERSION_CONFLICT", ex.message)

    @ExceptionHandler(PrincipalNotFoundException::class)
    fun handle(ex: PrincipalNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "USER_PRINCIPAL_NOT_FOUND", ex.message)

    @ExceptionHandler(InvalidPrincipalStatusTransitionException::class)
    fun handle(ex: InvalidPrincipalStatusTransitionException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "PRINCIPAL_STATUS_TRANSITION_REJECTED", ex.message)

    @ExceptionHandler(InvitationNotFoundException::class)
    fun handle(ex: InvitationNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", ex.message)

    @ExceptionHandler(WorkspaceNotFoundException::class)
    fun handle(ex: WorkspaceNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND", ex.message)

    @ExceptionHandler(InvitationNotAcceptableException::class)
    fun handle(ex: InvitationNotAcceptableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(invitationStatus(ex.failureCode), INVITATION_UNAVAILABLE_DETAIL).apply {
            title = "Invitation unavailable"
            type = URI("/problems/invitation-unavailable")
            setProperty("code", ex.failureCode.publicCode)
        }

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

    @ExceptionHandler(InvitationVersionConflictException::class)
    fun handle(ex: InvitationVersionConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "INVITATION_VERSION_CONFLICT", ex.message)

    @ExceptionHandler(NotificationNotFoundForRetryException::class)
    fun handle(ex: NotificationNotFoundForRetryException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", ex.message)

    @ExceptionHandler(NotificationNotRetryableException::class)
    fun handle(ex: NotificationNotRetryableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "NOTIFICATION_NOT_RETRYABLE", ex.message)

    @ExceptionHandler(NotificationRetryConflictException::class)
    fun handle(ex: NotificationRetryConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", ex.message)

    @ExceptionHandler(OptimisticLockException::class)
    fun handle(ex: OptimisticLockException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", ex.message)

    @ExceptionHandler(UserNotFoundException::class)
    fun handle(ex: UserNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.message)

    @ExceptionHandler(UserControlStateConflictException::class)
    fun handle(ex: UserControlStateConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "USER_STATE_CONFLICT", ex.message)

    @ExceptionHandler(UserControlIdempotencyConflictException::class)
    fun handle(ex: UserControlIdempotencyConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", ex.message)

    @ExceptionHandler(UserControlIdempotencyInProgressException::class)
    fun handle(ex: UserControlIdempotencyInProgressException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_IN_PROGRESS", ex.message)

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

    private fun invitationStatus(code: InvitationAcceptanceFailureCode): HttpStatus = when (code) {
        InvitationAcceptanceFailureCode.INVALID,
        InvitationAcceptanceFailureCode.WORKSPACE_OVERRIDE_NOT_ALLOWED,
        -> HttpStatus.BAD_REQUEST

        InvitationAcceptanceFailureCode.EXPIRED,
        InvitationAcceptanceFailureCode.REVOKED,
        -> HttpStatus.GONE

        InvitationAcceptanceFailureCode.ALREADY_CONSUMED,
        InvitationAcceptanceFailureCode.REPLAYED,
        -> HttpStatus.CONFLICT

        InvitationAcceptanceFailureCode.EMAIL_MISMATCH -> HttpStatus.FORBIDDEN
    }

    companion object {
        private const val MAX_CORRELATION_ID_LENGTH = 64
        private const val UNKNOWN_CORRELATION_ID = "unknown"
        private const val INVITATION_UNAVAILABLE_DETAIL = "Invitation is unavailable."
        private val VALID_CORRELATION_ID = Regex("^[A-Za-z0-9._:-]{1,$MAX_CORRELATION_ID_LENGTH}$")
    }
}
