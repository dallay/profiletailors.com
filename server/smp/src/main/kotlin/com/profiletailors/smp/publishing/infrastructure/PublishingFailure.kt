package com.profiletailors.smp.publishing.infrastructure

enum class PublishingFailureCategory(val code: String, val retryable: Boolean, val blocked: Boolean = false) {
    MEDIA_NOT_FOUND("MEDIA_NOT_FOUND", false),
    MEDIA_UNAVAILABLE("MEDIA_UNAVAILABLE", true),
    PROVIDER_VALIDATION_FAILED("PROVIDER_VALIDATION_FAILED", false),
    PROVIDER_RATE_LIMITED("PROVIDER_RATE_LIMITED", true),
    PROVIDER_UNAVAILABLE("PROVIDER_UNAVAILABLE", true),
    ACCOUNT_RECONNECT_REQUIRED("ACCOUNT_RECONNECT_REQUIRED", false, blocked = true),
    ACCOUNT_UNAVAILABLE("ACCOUNT_UNAVAILABLE", false),
    PUBLISHING_FAILED("PUBLISHING_FAILED", false),
    AMBIGUOUS_OUTCOME("AMBIGUOUS_OUTCOME", false, blocked = true),
}

data class PublishingFailure(val category: PublishingFailureCategory, val diagnostic: String? = null) {
    val retryable: Boolean = category.retryable

    companion object {
        fun mediaNotFound(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.MEDIA_NOT_FOUND,
            diagnostic,
        )

        fun mediaUnavailable(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.MEDIA_UNAVAILABLE,
            diagnostic,
        )

        fun validationFailed(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.PROVIDER_VALIDATION_FAILED,
            diagnostic,
        )

        fun providerRateLimited(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.PROVIDER_RATE_LIMITED,
            diagnostic,
        )

        fun providerUnavailable(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.PROVIDER_UNAVAILABLE,
            diagnostic,
        )

        fun accountReconnectRequired(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED,
            diagnostic,
        )

        fun accountUnavailable(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.ACCOUNT_UNAVAILABLE,
            diagnostic,
        )

        fun publishingFailed(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.PUBLISHING_FAILED,
            diagnostic,
        )
    }
}

class PublishingFailureException(val failure: PublishingFailure) : RuntimeException(failure.category.code)
