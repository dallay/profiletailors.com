package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.LinkedInAvatarFetcher
import com.profiletailors.smp.publishing.domain.ProviderTransportUncertaintyException
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest

internal class LinkedInAvatarFetcherImpl(
    private val properties: LinkedInPublishingProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: LinkedInHttpTransport,
) : LinkedInAvatarFetcher {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun fetchAvatarUrl(accessToken: String): String? {
        val response = try {
            httpTransport.send(
                HttpRequest.newBuilder(URI.create("${properties.apiBaseUrl}/v2/userinfo"))
                    .header("Authorization", "Bearer $accessToken")
                    .header(CONTENT_TYPE, "application/json")
                    .GET()
                    .build(),
            )
        } catch (exception: ProviderTransportUncertaintyException) {
            log.warn("LinkedIn avatar lookup transport uncertain: type={}", exception::class.simpleName)
            return null
        }
        if (response.statusCode !in HTTP_SUCCESS_RANGE) {
            log.warn("LinkedIn avatar lookup failed: status={}", response.statusCode)
            return null
        }
        val profile = objectMapper.readValue(response.body, LinkedInUserInfoResponse::class.java)
        return sanitizeLinkedInAvatarUrl(profile.picture)
    }

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
    }
}

internal fun sanitizeLinkedInAvatarUrl(picture: String?): String? {
    val trimmed = picture?.trim()
    if (trimmed.isNullOrBlank() || !trimmed.startsWith("https://", ignoreCase = true)) {
        return null
    }
    return trimmed
}
