package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthorizationProblemDetailsHandler {

    /**
     * Creates a generic forbidden response for an authorization failure.
     *
     * @return A problem detail that does not expose details of the denied operation.
     */
    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDenied(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, AUTHORIZATION_DENIED_DETAIL).apply {
            title = "Authorization denied"
        }

    companion object {
        private const val AUTHORIZATION_DENIED_DETAIL = "You do not have permission to perform this action."
    }
}
