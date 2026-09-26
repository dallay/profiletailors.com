package com.profiletailors.smp.publishing.infrastructure.threads

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.ProviderMediaUrlResolver
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.ProviderPublishResult
import com.profiletailors.smp.publishing.domain.ProviderTransportUncertaintyException
import com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver
import com.profiletailors.smp.publishing.domain.SocialPublisher
import com.profiletailors.smp.publishing.infrastructure.PublishingFailure
import com.profiletailors.smp.publishing.infrastructure.PublishingFailureException
import com.profiletailors.smp.publishing.infrastructure.http.CONTENT_TYPE
import com.profiletailors.smp.publishing.infrastructure.http.ProviderHttpResponse
import com.profiletailors.smp.publishing.infrastructure.http.ProviderHttpTransport
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpRequest
import java.time.Clock
import java.time.Duration

class ThreadsPublishingAdapter(
    private val properties: ThreadsPublishingProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: ProviderHttpTransport,
    private val credentialResolver: RefreshAwareCredentialResolver,
    private val mediaUrlResolver: ProviderMediaUrlResolver,
    private val clock: Clock = Clock.systemUTC(),
    private val sleeper: suspend (Duration) -> Unit = { delay(it.toMillis()) },
) : SocialPublisher {
    override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult {
        val accessToken = credentialResolver.resolve(command.socialAccount)
        val media = mediaUrlResolver.resolve(
            workspaceId = command.workspaceId,
            assets = command.assets,
            deadline = clock.instant().plus(
                properties.containerPollTimeout.multipliedBy(command.assets.size.toLong() + 1),
            ),
        )
        val children = if (command.assets.size > 1) {
            command.assets.zip(media).map { (asset, url) ->
                createContainer(
                    command = command,
                    accessToken = accessToken,
                    mediaUrl = url.url,
                    mediaType = asset.mediaType,
                ).also { awaitFinished(it, accessToken) }
            }
        } else {
            emptyList()
        }
        val parent = createContainer(
            command = command,
            accessToken = accessToken,
            mediaUrl = media.singleOrNull()?.url,
            mediaType = command.assets.singleOrNull()?.mediaType,
            children = children,
        )
        awaitFinished(parent, accessToken)
        val published = finalize(parent, accessToken, command.socialAccount.providerAccountId)
        return ProviderPublishResult(
            externalPublicationId = published,
            providerOperationRef = parent,
        )
    }

    private suspend fun createContainer(
        command: ProviderPublishCommand,
        accessToken: String,
        mediaUrl: String?,
        mediaType: String?,
        children: List<String> = emptyList(),
    ): String {
        val body = linkedMapOf<String, Any>(
            "access_token" to accessToken,
            "text" to command.publication.bodyText.orEmpty(),
        )
        if (children.isNotEmpty()) {
            body[MEDIA_TYPE] = "CAROUSEL"
            body["children"] = children
        } else if (mediaUrl != null && mediaType != null) {
            body[MEDIA_TYPE] = if (mediaType.startsWith("video/", ignoreCase = true)) "VIDEO" else "IMAGE"
            mediaUrl.takeIf { body[MEDIA_TYPE] == "IMAGE" }?.let { body["image_url"] = it }
            mediaUrl.takeIf { body[MEDIA_TYPE] == "VIDEO" }?.let { body["video_url"] = it }
        }
        val response = send(
            request = HttpRequest.newBuilder(endpoint("${command.socialAccount.providerAccountId}/threads"))
                .header(CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build(),
            accessToken = accessToken,
        )
        ensureSuccess(response)
        return checkNotNull(decode<ThreadsIdResponse>(response, "Threads container create").id)
    }

    private suspend fun awaitFinished(containerId: String, accessToken: String) {
        var attempts = 0
        while (attempts < properties.containerPollMaxAttempts) {
            val response = send(
                request = HttpRequest.newBuilder(endpoint(containerId)).GET().build(),
                accessToken = accessToken,
            )
            ensureSuccess(response)
            val status = decode<ThreadsContainerStatusResponse>(response, "Threads container status").status
            when (status.orEmpty().uppercase()) {
                "FINISHED" -> return
                "ERROR", "EXPIRED" -> throw PublishingFailureException(
                    PublishingFailure.validationFailed("status=$status"),
                )
            }
            attempts++
            if (attempts < properties.containerPollMaxAttempts) {
                sleeper(properties.containerPollInterval)
            }
        }
        throw ProviderTransportUncertaintyException(providerOperationRef = containerId)
    }

    private suspend fun finalize(containerId: String, accessToken: String, accountId: String): String {
        val request = HttpRequest.newBuilder(endpoint("$accountId/threads_publish"))
            .header(CONTENT_TYPE, "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(
                        mapOf("creation_id" to containerId, "access_token" to accessToken),
                    ),
                ),
            )
            .build()
        val response = send(request, accessToken)
        ensureSuccess(response)
        return checkNotNull(decode<ThreadsIdResponse>(response, "Threads publication finalize").id)
    }

    private fun endpoint(path: String): URI = URI.create("${properties.apiBaseUrl}/${properties.apiVersion}/$path")

    private suspend fun send(request: HttpRequest, accessToken: String): ProviderHttpResponse = httpTransport.send(
        HttpRequest.newBuilder(request.uri())
            .method(
                request.method(),
                request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()),
            )
            .header("Authorization", "Bearer $accessToken")
            .build(),
    )

    private fun ensureSuccess(response: ProviderHttpResponse) {
        if (response.statusCode in HTTP_SUCCESS_RANGE) return
        throw PublishingFailureException(failureFor(response.statusCode))
    }

    private fun failureFor(statusCode: Int): PublishingFailure = when (statusCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
            PublishingFailure.accountReconnectRequired(status(statusCode))
        HTTP_TOO_MANY_REQUESTS -> PublishingFailure.providerRateLimited(status(statusCode))
        in HTTP_SERVER_ERROR_RANGE -> PublishingFailure.providerUnavailable(status(statusCode))
        else -> PublishingFailure.validationFailed(status(statusCode))
    }

    private fun status(statusCode: Int): String = "status=$statusCode"

    private inline fun <reified T> decode(response: ProviderHttpResponse, operation: String): T = try {
        objectMapper.readValue(response.body, T::class.java)
    } catch (exception: JsonProcessingException) {
        throw IllegalStateException("$operation returned an invalid response.", exception)
    }

    private companion object {
        const val MEDIA_TYPE = "media_type"
        val HTTP_SUCCESS_RANGE = 200..299
        val HTTP_SERVER_ERROR_RANGE = 500..599
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsIdResponse(@JsonProperty("id") val id: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsContainerStatusResponse(@JsonProperty("status") val status: String? = null)
