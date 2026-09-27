package com.profiletailors.smp.publishing.infrastructure.threads

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentialGateway
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentials
import com.profiletailors.smp.publishing.infrastructure.linkedin.CONTENT_TYPE
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport
import com.profiletailors.smp.publishing.infrastructure.linkedin.formUrlEncoded
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest
import java.time.Clock
import java.util.UUID

class ThreadsConnectionProvider(
    private val properties: ThreadsPublishingProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: LinkedInHttpTransport,
    private val credentialGateway: ProviderCredentialGateway,
    private val clock: Clock = Clock.systemUTC(),
) : SocialConnectionProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun completeConnection(command: CompleteProviderConnectionCommand): ProviderConnectionResult {
        require(properties.isConfigured()) { "Threads OAuth provider is not configured." }
        require(properties.isAllowedRedirectUri(command.redirectUri)) { "Threads redirect URI is not registered." }
        val shortLived = exchangeCode(command)
        val longLived = exchangeLongLived(shortLived.accessToken)
        val profile = fetchProfile(longLived.accessToken)
        val providerAccountId = profile.id ?: shortLived.userId
            ?: throw IllegalStateException("Threads profile response did not include an account id.")
        val credentials = ProviderCredentials(
            provider = SocialProvider.THREADS,
            accessToken = longLived.accessToken,
            refreshToken = null,
            expiresAtEpochSeconds = longLived.expiresIn?.let { clock.instant().epochSecond + it },
            grantedScopes = properties.requiredScopes.sorted().joinToString(" "),
            scope = properties.requiredScopes.sorted().joinToString(" "),
        )
        val credentialReference = credentialGateway.storeForOwner(
            ownerType = "threads:user",
            ownerId = UUID.nameUUIDFromBytes("threads:$providerAccountId".toByteArray()),
            credentials = credentials,
        )
        return ProviderConnectionResult(
            provider = SocialProvider.THREADS,
            providerConnectionRef = "threads-user-$providerAccountId",
            credentialReference = credentialReference.toString(),
            account = ProviderAccountProfile(
                providerAccountId = providerAccountId,
                displayName = profile.displayName(providerAccountId),
                kind = SocialAccountKind.PERSONAL_PROFILE,
                profileUrn = "threads:$providerAccountId",
                avatarUrl = profile.pictureUrl?.takeIf { it.startsWith("https://", ignoreCase = true) },
            ),
        )
    }

    private suspend fun exchangeCode(command: CompleteProviderConnectionCommand): ThreadsShortLivedTokenResponse {
        val response = httpTransport.send(
            HttpRequest.newBuilder(URI.create("${properties.apiBaseUrl}/${properties.apiVersion}/oauth/access_token"))
                .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        formUrlEncoded(
                            "client_id" to properties.clientId,
                            "client_secret" to properties.clientSecret,
                            "redirect_uri" to command.redirectUri,
                            "code" to command.authorizationCode,
                            "grant_type" to "authorization_code",
                        ),
                    ),
                )
                .build(),
        )
        return decodeOrThrow(response, "Threads token exchange")
    }

    private suspend fun exchangeLongLived(accessToken: String): ThreadsLongLivedTokenResponse {
        val response = httpTransport.send(
            HttpRequest.newBuilder(
                URI.create(
                    "${properties.apiBaseUrl}/${properties.apiVersion}/access_token?" +
                        formUrlEncoded(
                            "grant_type" to "th_exchange_token",
                            "client_secret" to properties.clientSecret,
                            "access_token" to accessToken,
                        ),
                ),
            ).GET().build(),
        )
        return decodeOrThrow(response, "Threads long-lived token exchange")
    }

    private suspend fun fetchProfile(accessToken: String): ThreadsProfileResponse {
        val response = httpTransport.send(
            HttpRequest.newBuilder(
                URI.create(
                    "${properties.apiBaseUrl}/${properties.apiVersion}/me?" +
                        formUrlEncoded("fields" to "id,username,name,threads_profile_picture_url"),
                ),
            ).header("Authorization", "Bearer $accessToken").GET().build(),
        )
        return decodeOrThrow(response, "Threads profile lookup")
    }

    private inline fun <reified T> decodeOrThrow(
        response: com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse,
        operation: String,
    ): T {
        if (response.statusCode !in HTTP_SUCCESS_RANGE) {
            log.warn("{} failed with status {}", operation, response.statusCode)
            throw IllegalStateException("$operation failed.")
        }
        return try {
            objectMapper.readValue(response.body, T::class.java)
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException("$operation returned an invalid response.", exception)
        }
    }

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsShortLivedTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("user_id") val userId: String? = null,
    @JsonProperty("expires_in") val expiresIn: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsLongLivedTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsProfileResponse(
    val id: String? = null,
    val username: String? = null,
    val name: String? = null,
    @JsonProperty("threads_profile_picture_url") val pictureUrl: String? = null,
) {
    fun displayName(fallback: String): String = name?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }
        ?: fallback
}
