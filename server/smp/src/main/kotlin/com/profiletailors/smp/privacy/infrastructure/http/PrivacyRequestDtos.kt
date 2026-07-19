package com.profiletailors.smp.privacy.infrastructure.http

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Request DTO for submitting a new privacy data subject request.
 *
 * @property type The DSAR type: ACCESS, EXPORT, CORRECTION, or DELETION
 * @property notes Optional user-provided context for the request
 * @property newEmail Required for CORRECTION when correcting email
 * @property newUsername Required for CORRECTION when correcting username
 */
data class SubmitPrivacyRequestDto(
    @field:NotBlank(message = "Type is required (ACCESS, EXPORT, CORRECTION, DELETION)")
    val type: String,

    val notes: String?,

    @field:Email(message = "Invalid email format")
    val newEmail: String?,

    val newUsername: String?,
) {
    @AssertTrue(message = "Either newEmail or newUsername must be provided for CORRECTION requests")
    fun isCorrectionFieldsValid(): Boolean = !type.equals("CORRECTION", ignoreCase = true) ||
        !newEmail.isNullOrBlank() ||
        !newUsername.isNullOrBlank()
}

/**
 * Response DTO for a successfully submitted request.
 *
 * @property id The unique request identifier
 * @property status The current status of the request
 * @property message A human-readable status message
 * @property oldValues Previous values for CORRECTION requests (null otherwise)
 * @property downloadUrl Download URL for EXPORT requests (null otherwise)
 */
data class SubmitPrivacyResponseDto(
    val id: String,
    val status: String,
    val message: String,
    val oldValues: Map<String, String>?,
    val downloadUrl: String?,
)

/**
 * Response DTO for a single request status.
 *
 * @property id The unique request identifier
 * @property type The DSAR type
 * @property status The current lifecycle status
 * @property result The result payload (if completed)
 * @property createdAt ISO-8601 timestamp of creation
 * @property updatedAt ISO-8601 timestamp of last update
 */
data class PrivacyRequestStatusResponseDto(
    val id: String,
    val type: String,
    val status: String,
    val result: Map<String, Any?>?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Response DTO for paginated list of requests.
 *
 * @property requests The list of requests for this page
 * @property total Total number of requests
 * @property page Current page number
 * @property perPage Number of items per page
 */
data class PrivacyRequestListResponseDto(
    val requests: List<PrivacyRequestStatusResponseDto>,
    val total: Int,
    val page: Int,
    val perPage: Int,
)
