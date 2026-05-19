package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.governance.application.InvalidAuditEventCursorException
import com.profiletailors.smp.platform.application.MissingPrincipalContextException
import com.profiletailors.smp.platform.application.MissingResourceContextException
import com.profiletailors.smp.tenancy.application.MissingActiveWorkspaceException
import com.profiletailors.smp.tenancy.application.OwnerTargetMustBeActiveMemberException
import com.profiletailors.smp.tenancy.application.WorkspaceOwnerAccessDeniedException
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipNotFoundException
import com.profiletailors.smp.tenancy.application.WorkspaceOwnerNotFoundException
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipOperationRequiresWorkspaceContextException
import com.profiletailors.smp.tenancy.domain.LastOwnerRemovalRequiresReplacementException
import com.profiletailors.smp.tenancy.domain.OwnerMustRemainActiveMemberException
import com.profiletailors.smp.tenancy.domain.WorkspaceMustHaveAtLeastOneOwnerException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PlatformProblemDetailsHandler {

    @ExceptionHandler(MissingPrincipalContextException::class)
    fun handle(exception: MissingPrincipalContextException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.message ?: "Unauthorized").apply {
            title = "Principal context missing"
        }

    @ExceptionHandler(ApiKeyCredentialNotActiveException::class)
    fun handle(exception: ApiKeyCredentialNotActiveException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.message ?: "Unauthorized").apply {
            title = "API key credential invalid"
        }

    @ExceptionHandler(MissingResourceContextException::class)
    fun handle(exception: MissingResourceContextException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Resource context missing"
        }

    @ExceptionHandler(MissingActiveWorkspaceException::class)
    fun handle(exception: MissingActiveWorkspaceException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Active workspace missing"
        }

    @ExceptionHandler(WorkspaceOwnershipOperationRequiresWorkspaceContextException::class)
    fun handle(exception: WorkspaceOwnershipOperationRequiresWorkspaceContextException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Workspace context required"
        }

    @ExceptionHandler(OwnerTargetMustBeActiveMemberException::class)
    fun handle(exception: OwnerTargetMustBeActiveMemberException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Owner target must be active member"
        }

    @ExceptionHandler(WorkspaceOwnerNotFoundException::class)
    fun handle(exception: WorkspaceOwnerNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Not found").apply {
            title = "Workspace owner not found"
        }

    @ExceptionHandler(WorkspaceMembershipNotFoundException::class)
    fun handle(exception: WorkspaceMembershipNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Not found").apply {
            title = "Workspace membership not found"
        }

    @ExceptionHandler(WorkspaceOwnerAccessDeniedException::class)
    fun handle(exception: WorkspaceOwnerAccessDeniedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.message ?: "Forbidden").apply {
            title = "Workspace owner access denied"
        }

    @ExceptionHandler(InvalidAuditEventCursorException::class)
    fun handle(exception: InvalidAuditEventCursorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Invalid audit cursor"
        }

    @ExceptionHandler(
        WorkspaceMustHaveAtLeastOneOwnerException::class,
        LastOwnerRemovalRequiresReplacementException::class,
        OwnerMustRemainActiveMemberException::class,
    )
    fun handle(exception: Exception): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.message ?: "Conflict").apply {
            title = "Workspace ownership conflict"
        }
}
