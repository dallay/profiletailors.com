package com.profiletailors.smp.publishing.infrastructure.threads

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ProviderMediaUrl
import com.profiletailors.smp.publishing.domain.ProviderMediaUrlResolver
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.ProviderTransportUncertaintyException
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ThreadsPublishingAdapterTest {
    private val objectMapper = ObjectMapper()
    private val properties = ThreadsPublishingProperties(
        enabled = true,
        clientId = "client",
        clientSecret = "secret",
        redirectUri = "https://app.example.com/callback",
        apiBaseUrl = "https://graph.example",
        containerPollInterval = Duration.ofSeconds(1),
        containerPollTimeout = Duration.ofSeconds(3),
        containerPollMaxAttempts = 3,
        mediaUrlTtl = Duration.ofSeconds(30),
    )
    private val account = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.THREADS,
        providerAccountId = "threads-user-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Threads user",
        status = SocialConnectionStatus.ACTIVE,
    )

    @Test
    fun `publishes text through create poll and finalize`() = runTest {
        val transport = RecordingTransport(
            LinkedInHttpResponse(200, headers(), """{"id":"container-1"}"""),
            LinkedInHttpResponse(200, headers(), """{"status":"FINISHED"}"""),
            LinkedInHttpResponse(200, headers(), """{"id":"post-1"}"""),
        )
        val result = adapter(transport).publish(command(body = "Hello Threads"))

        result.externalPublicationId shouldBe "post-1"
        result.providerOperationRef shouldBe "container-1"
        transport.requests.map { it.method() } shouldBe listOf("POST", "GET", "POST")
        transport.requests.map { it.uri().path } shouldBe listOf(
            "/v1.0/threads-user-1/threads",
            "/v1.0/container-1",
            "/v1.0/threads-user-1/threads_publish",
        )
    }

    @Test
    fun `publishes mixed carousel children before parent`() = runTest {
        val transport = RecordingTransport(
            LinkedInHttpResponse(200, headers(), """{"id":"child-1"}"""),
            LinkedInHttpResponse(200, headers(), """{"status":"FINISHED"}"""),
            LinkedInHttpResponse(200, headers(), """{"id":"child-2"}"""),
            LinkedInHttpResponse(200, headers(), """{"status":"FINISHED"}"""),
            LinkedInHttpResponse(200, headers(), """{"id":"parent-1"}"""),
            LinkedInHttpResponse(200, headers(), """{"status":"FINISHED"}"""),
            LinkedInHttpResponse(200, headers(), """{"id":"post-2"}"""),
        )
        val result = adapter(transport).publish(
            command(
                assets = listOf(
                    asset("image/jpeg"),
                    asset("video/mp4", "asset-2"),
                ),
            ),
        )

        result.externalPublicationId shouldBe "post-2"
        check(transport.bodies[4].contains("CAROUSEL"))
        check(transport.bodies[4].contains("child-1"))
        check(transport.bodies[4].contains("child-2"))
    }

    @Test
    fun `polling is bounded and retains operation reference`() = runTest {
        val transport = RecordingTransport(
            LinkedInHttpResponse(200, headers(), """{"id":"container-2"}"""),
            LinkedInHttpResponse(200, headers(), """{"status":"IN_PROGRESS"}"""),
            LinkedInHttpResponse(200, headers(), """{"status":"IN_PROGRESS"}"""),
            LinkedInHttpResponse(200, headers(), """{"status":"IN_PROGRESS"}"""),
        )
        val exception = runCatching {
            adapter(transport).publish(command(body = "bounded"))
        }.exceptionOrNull()

        val uncertainty = exception.shouldBeInstanceOf<ProviderTransportUncertaintyException>()
        uncertainty.providerOperationRef shouldBe "container-2"
        transport.requests.count { it.method() == "GET" } shouldBe 3
    }

    @Test
    fun `media publishing resolves temporary URLs before provider create`() = runTest {
        val resolved = AtomicInteger()
        val mediaResolver = ProviderMediaUrlResolver { _, assets, _ ->
            resolved.addAndGet(assets.size)
            assets.map { ProviderMediaUrl("https://cdn.example/${it.id}", Instant.parse("2026-09-24T13:00:00Z")) }
        }
        val transport = RecordingTransport(
            LinkedInHttpResponse(200, headers(), """{"id":"container-3"}"""),
            LinkedInHttpResponse(200, headers(), """{"status":"FINISHED"}"""),
            LinkedInHttpResponse(200, headers(), """{"id":"post-3"}"""),
        )

        adapter(transport, mediaResolver).publish(command(assets = listOf(asset("image/jpeg"))))

        resolved.get() shouldBe 1
        check(transport.bodies.first().contains("https://cdn.example/asset-1"))
    }

    private fun adapter(
        transport: RecordingTransport,
        mediaResolver: ProviderMediaUrlResolver = ProviderMediaUrlResolver { _, assets, _ ->
            assets.map { ProviderMediaUrl("https://cdn.example/${it.id}", Instant.parse("2026-09-24T13:00:00Z")) }
        },
    ) = ThreadsPublishingAdapter(
        properties = properties,
        objectMapper = objectMapper,
        httpTransport = transport,
        credentialResolver = RefreshAwareCredentialResolver { "access-token" },
        mediaUrlResolver = mediaResolver,
        clock = java.time.Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), java.time.ZoneOffset.UTC),
        sleeper = {},
    )

    private fun command(body: String? = null, assets: List<PublicationAsset> = emptyList()) = ProviderPublishCommand(
        publicationId = "publication-1",
        workspaceId = "workspace-1",
        socialAccount = account,
        publication = PublicationDraft(
            id = "publication-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.THREADS,
            socialAccountId = "account-1",
            status = PublicationStatus.PROCESSING,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = body,
        ),
        assets = assets,
        operationKey = "job-1:1",
    )

    private fun asset(mediaType: String, id: String = "asset-1") = PublicationAsset(
        id = id,
        workspaceId = "workspace-1",
        sourceType = AssetSourceType.UPLOADED,
        mediaType = mediaType,
        storageKey = "assets/$id",
        status = PublicationAssetStatus.READY,
        createdByPrincipalId = "principal-1",
    )

    private fun headers() = HttpHeaders.of(emptyMap()) { _, _ -> true }

    private class RecordingTransport(private vararg val responses: LinkedInHttpResponse) : LinkedInHttpTransport {
        val requests = mutableListOf<HttpRequest>()
        val bodies = mutableListOf<String>()
        private var index = 0

        override suspend fun send(request: HttpRequest): LinkedInHttpResponse {
            requests += request
            request.bodyPublisher().ifPresent { publisher ->
                val bytes = java.io.ByteArrayOutputStream()
                val latch = java.util.concurrent.CountDownLatch(1)
                publisher.subscribe(object : java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
                    override fun onSubscribe(subscription: java.util.concurrent.Flow.Subscription) =
                        subscription.request(Long.MAX_VALUE)
                    override fun onNext(item: java.nio.ByteBuffer) {
                        val copy = item.duplicate()
                        val data = ByteArray(copy.remaining())
                        copy.get(data)
                        bytes.write(data)
                    }
                    override fun onError(throwable: Throwable) = latch.countDown()
                    override fun onComplete() = latch.countDown()
                })
                check(latch.await(1, java.util.concurrent.TimeUnit.SECONDS))
                bodies += bytes.toString(Charsets.UTF_8)
            }
            return responses[index++]
        }
    }
}
