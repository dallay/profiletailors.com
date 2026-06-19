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
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

private val logger = LoggerFactory.getLogger(MediaProblemDetailsHandler::class.java)

@RestControllerAdvice
class MediaProblemDetailsHandler {

    @ExceptionHandler(AssetNotFoundException::class)
    fun handle(exception: AssetNotFoundException): ProblemDetail {
        logger.debug("Asset not found: assetId={}", exception.assetId)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Asset ${exception.assetId} not found.",
        ).apply {
            title = "Asset not found"
            setProperty("errorCode", "ASSET_NOT_FOUND")
            setProperty("assetId", exception.assetId)
        }
    }

    @ExceptionHandler(UploadConflictException::class)
    fun handle(exception: UploadConflictException): ProblemDetail {
        logger.debug("Upload conflict: assetId={} currentStatus={}", exception.assetId, exception.currentStatus)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Asset ${exception.assetId} has already completed upload and cannot be re-uploaded.",
        ).apply {
            title = "Upload conflict"
            setProperty("errorCode", "ASSET_UPLOAD_CONFLICT")
            setProperty("assetId", exception.assetId)
            setProperty("currentStatus", exception.currentStatus)
        }
    }

    @ExceptionHandler(UploadInProgressException::class)
    fun handle(exception: UploadInProgressException): ProblemDetail {
        logger.debug("Upload in progress: assetId={}", exception.assetId)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Asset ${exception.assetId} already has an upload in progress.",
        ).apply {
            title = "Upload in progress"
            setProperty("errorCode", "ASSET_UPLOAD_IN_PROGRESS")
            setProperty("assetId", exception.assetId)
            setProperty("currentStatus", exception.currentStatus)
        }
    }

    @ExceptionHandler(AssetNotReadyException::class)
    fun handle(exception: AssetNotReadyException): ProblemDetail {
        logger.debug("Asset not ready: assetId={} reason={}", exception.assetId, exception.reason)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Asset ${exception.assetId} is not ready: ${exception.reason}",
        ).apply {
            title = "Asset not ready"
            setProperty("errorCode", "ASSET_NOT_READY")
            setProperty("assetId", exception.assetId)
            setProperty("reason", exception.reason)
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
    fun handle(exception: RateLimitExceededException): ProblemDetail {
        logger.info(
            "media.ratelimit.exceeded workspaceId={} limitType={} currentValue={} limitValue={}",
            exception.workspaceId,
            exception.limitType,
            exception.currentValue,
            exception.limitValue,
        )
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded for ${exception.limitType}.",
        ).apply {
            title = "Rate limit exceeded"
            setProperty("errorCode", "RATE_LIMIT_EXCEEDED")
            setProperty("workspaceId", exception.workspaceId)
            setProperty("limitType", exception.limitType)
            setProperty("currentValue", exception.currentValue)
            setProperty("limitValue", exception.limitValue)
        }

        // Add Retry-After header via response headers
        problemDetail.setProperty("retryAfterSeconds", exception.retryAfterSeconds)
        return problemDetail
    }

    @ExceptionHandler(InvalidCursorException::class)
    fun handle(exception: InvalidCursorException): ProblemDetail {
        logger.debug("Invalid cursor: {}", exception.message)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            exception.message ?: "Invalid pagination cursor.",
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
            exception.message ?: "Media service is temporarily unavailable.",
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
