package com.profiletailors.smp.mediaprovider.unsplash

import kotlinx.coroutines.flow.Flow

data class UnsplashSearchResponse(val total: Int, val totalPages: Int, val results: List<UnsplashPhoto>)

data class UnsplashPhoto(
    val id: String,
    val width: Int,
    val height: Int,
    val color: String?,
    val altDescription: String?,
    val urls: UnsplashPhotoUrls,
    val links: UnsplashPhotoLinks,
    val user: UnsplashUser,
    val tags: List<UnsplashTag> = emptyList(),
)

data class UnsplashPhotoUrls(val thumb: String, val full: String)

data class UnsplashPhotoLinks(val html: String, val download: String)

data class UnsplashUser(val name: String, val links: UnsplashUserLinks)

data class UnsplashUserLinks(val html: String)

data class UnsplashTag(val title: String)

data class UnsplashBinary(val mediaType: String, val contentLength: Long, val bytes: Flow<ByteArray>)

sealed class UnsplashProviderException(val errorCode: String, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class ProviderImportRejectedException(message: String) : UnsplashProviderException("IMPORT_REJECTED", message)
class ProviderErrorException(message: String) : UnsplashProviderException("PROVIDER_ERROR", message)
class ProviderUnavailableException(message: String, cause: Throwable? = null) :
    UnsplashProviderException("PROVIDER_UNREACHABLE", message, cause)

class UnsplashRateLimitedException(val retryAfterSeconds: Int) :
    UnsplashProviderException(
        "PROVIDER_RATE_LIMITED",
        "Unsplash rate limit reached (Retry-After: ${retryAfterSeconds}s)",
    )
