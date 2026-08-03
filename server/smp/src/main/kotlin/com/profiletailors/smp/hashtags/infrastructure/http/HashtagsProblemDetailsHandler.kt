package com.profiletailors.smp.hashtags.infrastructure.http

import com.profiletailors.smp.hashtags.application.HashtagSavedSetNotFoundException
import com.profiletailors.smp.hashtags.application.HashtagSetEmptyException
import com.profiletailors.smp.hashtags.application.HashtagSetNameBlankException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class HashtagsProblemDetailsHandler {

    @ExceptionHandler(HashtagSavedSetNotFoundException::class)
    fun handleSavedSetNotFound(exception: HashtagSavedSetNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Hashtag set not found.").apply {
            title = "Hashtag set not found"
            setProperty("setId", exception.setId)
        }

    @ExceptionHandler(HashtagSetNameBlankException::class)
    fun handleBlankName(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Hashtag set name must not be blank.").apply {
            title = "Invalid hashtag set"
        }

    @ExceptionHandler(HashtagSetEmptyException::class)
    fun handleEmptySet(): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Hashtag set must contain at least one hashtag.",
    ).apply {
        title = "Invalid hashtag set"
    }
}
