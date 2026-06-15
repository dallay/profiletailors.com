package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.tenancy.application.MissingActiveWorkspaceException
import com.profiletailors.smp.tenancy.application.OwnerTargetMustBeActiveMemberException
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipNotFoundException
import com.profiletailors.smp.tenancy.application.WorkspaceOwnerAccessDeniedException
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
class TenancyProblemDetailsHandler {

    @ExceptionHandler(MissingActiveWorkspaceException::class)
    fun handle(exception: MissingActiveWorkspaceException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: BAD_REQUEST_DETAIL).apply {
            title = "Active workspace missing"
        }

    @ExceptionHandler(WorkspaceOwnershipOperationRequiresWorkspaceContextException::class)
    fun handle(exception: WorkspaceOwnershipOperationRequiresWorkspaceContextException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: BAD_REQUEST_DETAIL).apply {
            title = "Workspace context required"
        }

    @ExceptionHandler(OwnerTargetMustBeActiveMemberException::class)
    fun handle(exception: OwnerTargetMustBeActiveMemberException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: BAD_REQUEST_DETAIL).apply {
            title = "Owner target must be active member"
        }

    @ExceptionHandler(WorkspaceOwnerNotFoundException::class)
    fun handle(exception: WorkspaceOwnerNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: NOT_FOUND_DETAIL).apply {
            title = "Workspace owner not found"
        }

    @ExceptionHandler(WorkspaceMembershipNotFoundException::class)
    fun handle(exception: WorkspaceMembershipNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: NOT_FOUND_DETAIL).apply {
            title = "Workspace membership not found"
        }

    @ExceptionHandler(WorkspaceOwnerAccessDeniedException::class)
    fun handle(exception: WorkspaceOwnerAccessDeniedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.message ?: FORBIDDEN_DETAIL).apply {
            title = "Workspace owner access denied"
        }

    @ExceptionHandler(
        WorkspaceMustHaveAtLeastOneOwnerException::class,
        LastOwnerRemovalRequiresReplacementException::class,
        OwnerMustRemainActiveMemberException::class,
    )
    fun handle(exception: Exception): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.message ?: CONFLICT_DETAIL).apply {
            title = "Workspace ownership conflict"
        }

    companion object {
        private const val BAD_REQUEST_DETAIL = "Bad request"
        private const val NOT_FOUND_DETAIL = "Not found"
        private const val FORBIDDEN_DETAIL = "Forbidden"
        private const val CONFLICT_DETAIL = "Conflict"
    }
}
