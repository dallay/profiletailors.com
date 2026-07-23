package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.smp.media.application.AssetNotFoundException
import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.FileTooLargeException
import com.profiletailors.smp.media.application.InvalidCursorException
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.media.application.RateLimitExceededException
import com.profiletailors.smp.media.application.UnsupportedMediaTypeException
import com.profiletailors.smp.media.application.UploadConflictException
import com.profiletailors.smp.media.application.UploadInProgressException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

private val logger = LoggerFactory.getLogger(MediaProblemDetailsHandler::class.java)

@RestControllerAdvice
class MediaProblemDetailsHandler {

    companion object {
        private const val ASSET_NOT_FOUND_DETAIL = "Requested asset was not found."
        private const val UPLOAD_CONFLICT_DETAIL = "The asset cannot be uploaded in its current state."
        private const val UPLOAD_IN_PROGRESS_DETAIL = "An upload is already in progress for this asset."
        private const val ASSET_NOT_READY_DETAIL = "The asset is not ready for this operation."
        private const val RATE_LIMIT_EXCEEDED_DETAIL = "Rate limit exceeded. Please try again later."
        private const val INVALID_CURSOR_DETAIL = "Invalid pagination cursor."
        private const val MEDIA_SERVICE_UNAVAILABLE_DETAIL = "Media service is temporarily unavailable."
    }

    @ExceptionHandler(AssetNotFoundException::class)
    fun handle(exception: AssetNotFoundException): ProblemDetail {
        logger.debug("Asset not found: assetId={}", exception.assetId)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ASSET_NOT_FOUND_DETAIL,
        ).apply {
            title = "Asset not found"
            setProperty("errorCode", "ASSET_NOT_FOUND")
        }
    }

    @ExceptionHandler(UploadConflictException::class)
    fun handle(exception: UploadConflictException): ProblemDetail {
        logger.debug("Upload conflict: assetId={} currentStatus={}", exception.assetId, exception.currentStatus)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            UPLOAD_CONFLICT_DETAIL,
        ).apply {
            title = "Upload conflict"
            setProperty("errorCode", "ASSET_UPLOAD_CONFLICT")
        }
    }

    @ExceptionHandler(UploadInProgressException::class)
    fun handle(exception: UploadInProgressException): ProblemDetail {
        logger.debug("Upload in progress: assetId={}", exception.assetId)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            UPLOAD_IN_PROGRESS_DETAIL,
        ).apply {
            title = "Upload in progress"
            setProperty("errorCode", "ASSET_UPLOAD_IN_PROGRESS")
        }
    }

    @ExceptionHandler(AssetNotReadyException::class)
    fun handle(exception: AssetNotReadyException): ProblemDetail {
        logger.debug("Asset not ready: assetId={} reason={}", exception.assetId, exception.reason)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ASSET_NOT_READY_DETAIL,
        ).apply {
            title = "Asset not ready"
            setProperty("errorCode", "ASSET_NOT_READY")
        }
    }

    @ExceptionHandler(UnsupportedMediaTypeException::class)
    fun handle(exception: UnsupportedMediaTypeException): ProblemDetail {
        logger.debug("Unsupported media type: declared={} detected={}", exception.declaredType, exception.detectedType)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            exception.message ?: "Unsupported media type.",
        ).apply {
            title = "Unsupported media type"
            setProperty("errorCode", "UNSUPPORTED_MEDIA_TYPE")
            exception.detectedType?.let { setProperty("detectedType", it) }
            exception.declaredType?.let { setProperty("declaredType", it) }
        }
    }

    @ExceptionHandler(FileTooLargeException::class)
    fun handle(exception: FileTooLargeException): ProblemDetail {
        logger.debug("File too large: size={} maxAllowed={}", exception.actualSize, exception.maxAllowed)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "File size (${exception.actualSize} bytes) exceeds the 500 MB limit.",
        ).apply {
            title = "File too large"
            setProperty("errorCode", "FILE_TOO_LARGE")
            setProperty("actualSize", exception.actualSize)
            setProperty("maxAllowed", exception.maxAllowed)
        }
    }

    @ExceptionHandler(RateLimitExceededException::class)
    fun handle(exception: RateLimitExceededException): ResponseEntity<ProblemDetail> {
        logger.info(
            "media.ratelimit.exceeded workspaceId={} limitType={} currentValue={} limitValue={}",
            exception.workspaceId,
            exception.limitType,
            exception.currentValue,
            exception.limitValue,
        )
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            RATE_LIMIT_EXCEEDED_DETAIL,
        ).apply {
            title = "Rate limit exceeded"
            setProperty("errorCode", "RATE_LIMIT_EXCEEDED")
            setProperty("retryAfterSeconds", exception.retryAfterSeconds)
        }

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", exception.retryAfterSeconds.toString())
            .body(problemDetail)
    }

    @ExceptionHandler(InvalidCursorException::class)
    fun handle(exception: InvalidCursorException): ProblemDetail {
        logger.debug("Invalid cursor: {}", exception.message)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            INVALID_CURSOR_DETAIL,
        ).apply {
            title = "Invalid cursor"
            setProperty("errorCode", "INVALID_CURSOR")
        }
    }

    @ExceptionHandler(MediaServiceUnavailableException::class)
    fun handle(exception: MediaServiceUnavailableException): ProblemDetail {
        logger.error("Media service unavailable: {}", exception.message, exception)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            MEDIA_SERVICE_UNAVAILABLE_DETAIL,
        ).apply {
            title = "Media service unavailable"
            setProperty("errorCode", "MEDIA_SERVICE_UNAVAILABLE")
        }
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handle(exception: ResponseStatusException): ProblemDetail {
        if (exception.statusCode == HttpStatus.PAYLOAD_TOO_LARGE) {
            return ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                exception.reason ?: "File size exceeds the 500 MB limit.",
            ).apply {
                title = "File too large"
                setProperty("errorCode", "FILE_TOO_LARGE")
            }
        }
        return ProblemDetail.forStatusAndDetail(
            exception.statusCode,
            exception.reason ?: exception.statusCode.value().toString(),
        ).apply {
            title = exception.statusCode.value().toString()
        }
    }
}
