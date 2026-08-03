package com.profiletailors.smp.ideas.infrastructure.http

import com.profiletailors.smp.ideas.application.IdeaNotFoundException
import com.profiletailors.smp.ideas.application.InvalidIdeaColumnsException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class IdeasProblemDetailsHandler {
    @ExceptionHandler(IdeaNotFoundException::class)
    fun handle(exception: IdeaNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Idea not found.").apply {
            title = "Idea not found"
            setProperty("ideaId", exception.ideaId)
        }

    @ExceptionHandler(InvalidIdeaColumnsException::class)
    fun handle(exception: InvalidIdeaColumnsException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid columns.").apply {
            title = "Invalid idea columns"
        }
}
