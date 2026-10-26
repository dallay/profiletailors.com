package com.profiletailors.smp.publishing.integration

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.authorization.application.WorkspaceMembershipGate
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.integration.support.countPublicationJobs
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.publishing.application.BatchPublicationTarget
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionHandler
import com.profiletailors.smp.publishing.application.CreatePublicationBatchCommand
import com.profiletailors.smp.publishing.application.CreatePublicationBatchHandler
import com.profiletailors.smp.publishing.application.PublishingMediaIntegrationSettings
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishingHandlersTransactionPostgresIntegrationTest : PublicationTransactionDatabaseFixture() {

    override val postgres = postgresContainer

    @Test
    fun `batch creation rolls back earlier publication and job when a later target persist fails`() = runTest {
        var createCount = 0
        val failingPublicationRepository = object : PublicationRepository by publicationRepository {
            override suspend fun createDraft(draft: PublicationDraft): PublicationDraft {
                createCount += 1
                if (createCount == 2) throw InjectedPublicationFailure()
                return publicationRepository.createDraft(draft)
            }
        }
        val batchHandler = CreatePublicationBatchHandler(
            principalContextProvider = FixedPrincipalContextProvider(),
            resourceContextProvider = FixedResourceContextProvider(),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = failingPublicationRepository,
            publicationAssetRepository = object : PublicationAssetRepository {
                override suspend fun findByWorkspaceAndIds(
                    workspaceId: String,
                    assetIds: Collection<String>,
                ): List<PublicationAsset> = emptyList()

                override suspend fun create(asset: PublicationAsset): PublicationAsset = asset

                override suspend fun updateStatus(
                    assetId: String,
                    status: com.profiletailors.smp.publishing.domain.PublicationAssetStatus,
                ) = Unit

                override suspend fun updateProviderAssetRef(
                    assetId: String,
                    providerAssetRef: com.profiletailors.smp.publishing.domain.ProviderAssetRef,
                ) = Unit
            },
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            providerCapabilityValidator = ProviderCapabilityValidator { },
            schedulingPolicy = schedulingPolicy,
            mediaAssetResolver = MediaAssetResolver { _, _ -> emptyList() },
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = false),
            clock = Clock.fixed(now, java.time.ZoneOffset.UTC),
            principalIdentityLookup = NoOpPrincipalIdentityLookup(),
            membershipGate = WorkspaceMembershipGate(
                FixedPrincipalContextProvider(),
                object : com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver {
                    override suspend fun resolve(
                        principalContext: PrincipalContext,
                        resourceContext: ResourceContext,
                    ) = WorkspaceMembership(
                        workspaceId = requireNotNull(resourceContext.workspaceId),
                        principalId = principalContext.principalId,
                        principalType = principalContext.principalType,
                        status = WorkspaceMembershipStatus.ACTIVE,
                    )
                },
            ),
        )

        assertThrows(InjectedPublicationFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                batchHandler.handle(
                    CreatePublicationBatchCommand(
                        targets = listOf(
                            BatchPublicationTarget("00000000-0000-0000-0000-0000000003e8", "first", emptyList()),
                            BatchPublicationTarget("00000000-0000-0000-0000-0000000003e8", "second", emptyList()),
                        ),
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        val publicationCount = databaseClient.sql(
            "SELECT COUNT(*) AS count FROM publications WHERE workspace_id = :workspaceId",
        )
            .bind("workspaceId", UUID.fromString("00000000-0000-0000-0000-00000000049d"))
            .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
            .one()
            .awaitSingle()
        assertEquals(0L, publicationCount)
        val jobCount = databaseClient.sql(
            "SELECT COUNT(*) AS count FROM publication_jobs WHERE workspace_id = :workspaceId",
        )
            .bind("workspaceId", UUID.fromString("00000000-0000-0000-0000-00000000049d"))
            .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
            .one()
            .awaitSingle()
        assertEquals(0L, jobCount)
    }

    @Test
    fun `linkedin completion rolls back social connection when account upsert fails`() = runTest {
        val eventPublisher = CapturingChannelEventPublisher()
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(),
            resourceContextProvider = FixedResourceContextProvider(),
            socialConnectionProvider = FakeSocialConnectionProvider(),
            oauthStateSigner = FixedOAuthStateSigner(),
            authorizationUrlBuilder = AllowlistedAuthorizationUrlBuilder(),
            socialConnectionRepository = socialConnectionRepository,
            socialAccountRepository = FailingSocialAccountRepository(socialAccountRepository),
            channelEventPublisher = eventPublisher,
            clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
            transactionRunner = transactionRunner,
        )

        assertThrows(InjectedSocialAccountFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteLinkedInConnectionCommand(
                        authorizationCode = "oauth-code-193",
                        redirectUri = "https://app.example.com/callback",
                        state = "state-193",
                    ),
                )
            }
        }

        assertNull(socialConnectionByProviderRef("linkedin-connection-193"))
        assertNull(socialAccountByProviderAccountId("linkedin-account-193"))
        assertEquals(emptyList<ChannelEvent>(), eventPublisher.events)
    }

    @Test
    fun `create commits publication asset links and job together`() = runTest {
        createPublicationAndJob("00000000-0000-0000-0000-000000000440", jobRepository)

        assertPublication(
            "00000000-0000-0000-0000-000000000440",
            PublicationStatus.QUEUED,
            "create",
            listOf("00000000-0000-0000-0000-0000000003ed", "00000000-0000-0000-0000-0000000003ee"),
        )
        assertJob("00000000-0000-0000-0000-000000000440", status = JobStatus.PENDING, priorityRank = 100)
    }

    @Test
    fun `create rolls back publication asset links and job when enqueue fails`() = runTest {
        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                createPublicationAndJob(
                    "00000000-0000-0000-0000-000000000442",
                    FailingJobRepository(jobRepository, failEnqueue = true),
                )
            }
        }

        assertNull(
            publicationRepository.findByWorkspaceAndId(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-000000000442",
            ),
        )
        assertEquals(emptyList<String>(), assetLinks("00000000-0000-0000-0000-000000000442"))
        assertNull(jobRow("00000000-0000-0000-0000-000000000442"))
    }

    @Test
    fun `edit commits persisted publication asset links and replacement job`() = runTest {
        seedPublicationAndJob(
            "00000000-0000-0000-0000-000000000449",
            "old",
            listOf("00000000-0000-0000-0000-0000000003ed"),
        )

        updatePublicationAndReplaceJob(
            publicationId = "00000000-0000-0000-0000-000000000449",
            draft = draft(
                "00000000-0000-0000-0000-000000000449",
                "edited",
                listOf("00000000-0000-0000-0000-0000000003ee"),
                priority = true,
            ),
            repository = jobRepository,
        )

        assertPublication(
            "00000000-0000-0000-0000-000000000449",
            PublicationStatus.QUEUED,
            "edited",
            listOf("00000000-0000-0000-0000-0000000003ee"),
        )
        assertJob("00000000-0000-0000-0000-000000000449", status = JobStatus.PENDING, priorityRank = 100)
    }

    @Test
    fun `edit rolls back publication asset links and preserves job when replacement fails`() = runTest {
        val originalJobId =
            seedPublicationAndJob(
                "00000000-0000-0000-0000-00000000044a",
                "old",
                listOf("00000000-0000-0000-0000-0000000003ed"),
            )

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                updatePublicationAndReplaceJob(
                    publicationId = "00000000-0000-0000-0000-00000000044a",
                    draft = draft(
                        "00000000-0000-0000-0000-00000000044a",
                        "edited",
                        listOf("00000000-0000-0000-0000-0000000003ee"),
                        priority = true,
                    ),
                    repository = FailingJobRepository(jobRepository, failReplace = true),
                )
            }
        }

        assertPublication(
            "00000000-0000-0000-0000-00000000044a",
            PublicationStatus.QUEUED,
            "old",
            listOf("00000000-0000-0000-0000-0000000003ed"),
        )
        assertEquals(originalJobId, requireNotNull(jobRow("00000000-0000-0000-0000-00000000044a"))["id"])
    }

    @Test
    fun `cancel commits publication and job cancellation together`() = runTest {
        seedPublicationAndJob(
            "00000000-0000-0000-0000-000000000438",
            "cancel me",
            listOf("00000000-0000-0000-0000-0000000003ed"),
        )

        transactionRunner.runAtomically {
            publicationRepository.markCancelled("00000000-0000-0000-0000-000000000438", now)
            jobRepository.cancel("00000000-0000-0000-0000-000000000438", now)
        }

        assertPublication(
            "00000000-0000-0000-0000-000000000438",
            PublicationStatus.CANCELLED,
            "cancel me",
            listOf("00000000-0000-0000-0000-0000000003ed"),
        )
        assertJob("00000000-0000-0000-0000-000000000438", status = JobStatus.CANCELLED)
    }

    @Test
    fun `cancel rolls back publication status and preserves job when cancel fails`() = runTest {
        val originalJobId =
            seedPublicationAndJob(
                "00000000-0000-0000-0000-000000000439",
                "cancel me",
                listOf("00000000-0000-0000-0000-0000000003ed"),
            )

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                transactionRunner.runAtomically {
                    publicationRepository.markCancelled("00000000-0000-0000-0000-000000000439", now)
                    FailingJobRepository(
                        jobRepository,
                        failCancel = true,
                    ).cancel("00000000-0000-0000-0000-000000000439", now)
                }
            }
        }

        assertPublication(
            "00000000-0000-0000-0000-000000000439",
            PublicationStatus.QUEUED,
            "cancel me",
            listOf("00000000-0000-0000-0000-0000000003ed"),
        )
        val job = requireNotNull(jobRow("00000000-0000-0000-0000-000000000439"))
        assertEquals(originalJobId, job["id"])
        assertEquals(JobStatus.PENDING.name, job["status"])
    }

    @Test
    fun `retry commits publication asset links and replacement job`() = runTest {
        val originalJobId =
            seedPublicationAndJob(
                "00000000-0000-0000-0000-000000000465",
                "failed",
                listOf("00000000-0000-0000-0000-0000000003ed"),
                status = PublicationStatus.FAILED,
            )
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003f9",
                publicationId = "00000000-0000-0000-0000-000000000465",
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )

        updatePublicationAndReplaceJob(
            publicationId = "00000000-0000-0000-0000-000000000465",
            draft = draft(
                "00000000-0000-0000-0000-000000000465",
                "retry",
                listOf("00000000-0000-0000-0000-0000000003ee"),
                priority = true,
                status = PublicationStatus.QUEUED,
            ),
            repository = jobRepository,
        )

        assertPublication(
            "00000000-0000-0000-0000-000000000465",
            PublicationStatus.QUEUED,
            "retry",
            listOf("00000000-0000-0000-0000-0000000003ee"),
        )
        assertJob("00000000-0000-0000-0000-000000000465", status = JobStatus.PENDING, priorityRank = 100)
        assertNull(deliveryAttemptRow("00000000-0000-0000-0000-000000000465"))
    }

    @Test
    fun `retry rolls back publication asset links and preserves job when replacement fails`() = runTest {
        val originalJobId =
            seedPublicationAndJob(
                "00000000-0000-0000-0000-000000000467",
                "failed",
                listOf("00000000-0000-0000-0000-0000000003ed"),
                status = PublicationStatus.FAILED,
            )
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003fb",
                publicationId = "00000000-0000-0000-0000-000000000467",
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                updatePublicationAndReplaceJob(
                    publicationId = "00000000-0000-0000-0000-000000000467",
                    draft = draft(
                        "00000000-0000-0000-0000-000000000467",
                        "retry",
                        listOf("00000000-0000-0000-0000-0000000003ee"),
                        priority = true,
                        status = PublicationStatus.QUEUED,
                    ),
                    repository = FailingJobRepository(jobRepository, failReplace = true),
                )
            }
        }

        assertPublication(
            "00000000-0000-0000-0000-000000000467",
            PublicationStatus.FAILED,
            "failed",
            listOf("00000000-0000-0000-0000-0000000003ed"),
        )
        assertEquals(originalJobId, requireNotNull(jobRow("00000000-0000-0000-0000-000000000467"))["id"])
        assertEquals(
            "00000000-0000-0000-0000-0000000003fb",
            requireNotNull(deliveryAttemptRow("00000000-0000-0000-0000-000000000467"))["id"],
        )
    }

    @Test
    fun `retry rolls back deleted attempts and job when replacement insert fails`() = runTest {
        val publicationId = "00000000-0000-0000-0000-000000000468"
        val originalJobId =
            seedPublicationAndJob(
                publicationId,
                "failed",
                listOf("00000000-0000-0000-0000-0000000003ed"),
                status = PublicationStatus.FAILED,
            )
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003fc",
                publicationId = publicationId,
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )
        val collisionHolder = publicationRepository.createDraft(
            draft("00000000-0000-0000-0000-000000000464", "collision", emptyList()),
        )
        jobRepository.enqueue(
            jobFor(collisionHolder).copy(id = publicationId),
        )

        assertThrows(DataIntegrityViolationException::class.java) {
            kotlinx.coroutines.runBlocking {
                updatePublicationAndReplaceJob(
                    publicationId = publicationId,
                    draft = draft(
                        publicationId,
                        "retry",
                        listOf("00000000-0000-0000-0000-0000000003ee"),
                        priority = true,
                        status = PublicationStatus.QUEUED,
                    ),
                    repository = jobRepository,
                )
            }
        }

        assertPublication(
            publicationId,
            PublicationStatus.FAILED,
            "failed",
            listOf("00000000-0000-0000-0000-0000000003ed"),
        )
        assertEquals(originalJobId, requireNotNull(jobRow(publicationId))["id"])
        assertEquals(
            "00000000-0000-0000-0000-0000000003fc",
            requireNotNull(deliveryAttemptRow(publicationId))["id"],
        )
    }

    @Test
    fun `concurrent retries leave one replacement job without delivery attempts`() = runTest {
        val publicationId = "00000000-0000-0000-0000-000000000466"
        val originalJobId =
            seedPublicationAndJob(
                publicationId,
                "failed",
                listOf("00000000-0000-0000-0000-0000000003ed"),
                status = PublicationStatus.FAILED,
            )
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003fa",
                publicationId = publicationId,
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )

        val results = coroutineScope {
            listOf(false, true).map { priority ->
                async {
                    runCatching {
                        updatePublicationAndReplaceJob(
                            publicationId = publicationId,
                            draft = draft(
                                publicationId,
                                "retry",
                                listOf("00000000-0000-0000-0000-0000000003ee"),
                                priority = priority,
                                status = PublicationStatus.QUEUED,
                            ),
                            repository = jobRepository,
                        )
                    }
                }
            }.awaitAll()
        }

        assertTrue(results.all { it.isSuccess })
        assertEquals(1L, databaseClient.countPublicationJobs(publicationId))
        assertNull(deliveryAttemptRow(publicationId))
    }

    @Test
    fun `reschedule commits publication asset links and replacement job`() = runTest {
        val originalJobId =
            seedPublicationAndJob(
                "00000000-0000-0000-0000-000000000462",
                "old time",
                listOf("00000000-0000-0000-0000-0000000003ed"),
            )
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003f8",
                publicationId = "00000000-0000-0000-0000-000000000462",
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )

        updatePublicationAndReplaceJob(
            publicationId = "00000000-0000-0000-0000-000000000462",
            draft = draft(
                "00000000-0000-0000-0000-000000000462",
                "new time",
                listOf("00000000-0000-0000-0000-0000000003ee"),
                status = PublicationStatus.SCHEDULED,
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = Instant.parse("2026-06-15T10:00:00Z"),
            ),
            repository = jobRepository,
        )

        assertPublication(
            "00000000-0000-0000-0000-000000000462",
            PublicationStatus.SCHEDULED,
            "new time",
            listOf("00000000-0000-0000-0000-0000000003ee"),
        )
        assertJob("00000000-0000-0000-0000-000000000462", status = JobStatus.PENDING)
        assertNull(deliveryAttemptRow("00000000-0000-0000-0000-000000000462"))
    }

    @Test
    fun `reschedule rolls back publication timing assets and preserves job when replacement fails`() = runTest {
        val originalJobId =
            seedPublicationAndJob(
                "00000000-0000-0000-0000-000000000463",
                "old time",
                listOf("00000000-0000-0000-0000-0000000003ed"),
            )

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                updatePublicationAndReplaceJob(
                    publicationId = "00000000-0000-0000-0000-000000000463",
                    draft = draft(
                        "00000000-0000-0000-0000-000000000463",
                        "new time",
                        listOf("00000000-0000-0000-0000-0000000003ee"),
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = Instant.parse("2026-06-15T10:00:00Z"),
                    ),
                    repository = FailingJobRepository(jobRepository, failReplace = true),
                )
            }
        }

        assertPublication(
            "00000000-0000-0000-0000-000000000463",
            PublicationStatus.QUEUED,
            "old time",
            listOf("00000000-0000-0000-0000-0000000003ed"),
        )
        assertEquals(originalJobId, requireNotNull(jobRow("00000000-0000-0000-0000-000000000463"))["id"])
    }

    private suspend fun createPublicationAndJob(publicationId: String, repository: PublicationJobRepository) {
        transactionRunner.runAtomically {
            val created = publicationRepository.createDraft(
                draft(
                    publicationId,
                    "create",
                    listOf("00000000-0000-0000-0000-0000000003ed", "00000000-0000-0000-0000-0000000003ee"),
                    priority = true,
                ),
            )
            repository.enqueue(jobFor(created))
            created
        }
    }

    private suspend fun updatePublicationAndReplaceJob(
        publicationId: String,
        draft: PublicationDraft,
        repository: PublicationJobRepository,
    ) {
        transactionRunner.runAtomically {
            val persisted = publicationRepository.updateEditableDraft(draft)
            repository.replaceForPublication(jobFor(persisted))
            publicationId
        }
    }

    private suspend fun assertPublication(
        publicationId: String,
        status: PublicationStatus,
        bodyText: String,
        assetIds: List<String>,
    ) {
        val publication =
            requireNotNull(
                publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", publicationId),
            )
        assertEquals(status, publication.status)
        assertEquals(bodyText, publication.bodyText)
        assertEquals(assetIds, publication.assetIds)
    }

    private suspend fun assertJob(publicationId: String, status: JobStatus, priorityRank: Int? = null) {
        val job = requireNotNull(jobRow(publicationId))
        assertEquals(status.name, job["status"])
        priorityRank?.let { assertEquals(it, (job["priority_rank"] as Number).toInt()) }
    }

    private suspend fun jobRow(publicationId: String): Map<String, Any>? = databaseClient.sql(
        "SELECT id, status, priority_rank FROM publication_jobs WHERE publication_id = :publicationId",
    )
        .bind("publicationId", UUID.fromString(publicationId))
        .map { row, _ ->
            mapOf(
                "id" to requireNotNull(row.get("id", UUID::class.java)).toString(),
                "status" to requireNotNull(row.get("status", String::class.java)),
                "priority_rank" to requireNotNull(row.get("priority_rank", Int::class.javaObjectType)),
            )
        }
        .one()
        .awaitSingleOrNull()

    private suspend fun deliveryAttemptRow(publicationId: String): Map<String, Any>? = databaseClient.sql(
        "SELECT id FROM delivery_attempts WHERE publication_id = :publicationId",
    )
        .bind("publicationId", UUID.fromString(publicationId))
        .map { row, _ -> mapOf("id" to requireNotNull(row.get("id", UUID::class.java)).toString()) }
        .one()
        .awaitSingleOrNull()

    private suspend fun socialConnectionByProviderRef(providerConnectionRef: String): Map<String, Any>? =
        databaseClient.sql(
            "SELECT id FROM social_connections WHERE provider_connection_ref = :providerConnectionRef",
        )
            .bind("providerConnectionRef", providerConnectionRef)
            .fetch()
            .one()
            .awaitSingleOrNull()

    private suspend fun socialAccountByProviderAccountId(providerAccountId: String): Map<String, Any>? =
        databaseClient.sql(
            "SELECT id FROM social_accounts WHERE provider_account_id = :providerAccountId",
        )
            .bind("providerAccountId", providerAccountId)
            .fetch()
            .one()
            .awaitSingleOrNull()

    private suspend fun assetLinks(publicationId: String): List<String> = databaseClient.sql(
        """
        SELECT asset_id
        FROM publication_asset_links
        WHERE publication_id = :publicationId
        ORDER BY position_index ASC
        """.trimIndent(),
    )
        .bind("publicationId", UUID.fromString(publicationId))
        .map { row, _ -> requireNotNull(row.get("asset_id", UUID::class.java)).toString() }
        .all()
        .collectList()
        .awaitSingle()

    private class InjectedPublicationFailure : RuntimeException("Injected publication repository failure")

    private class InjectedSocialAccountFailure : RuntimeException("Injected social account repository failure")

    private class InjectedJobFailure : RuntimeException("Injected job repository failure")

    private class FixedPrincipalContextProvider : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = PrincipalContext(
            principalId = "00000000-0000-0000-0000-000000000430",
            principalType = PrincipalType.USER,
            subject = "local:owner@example.com",
        )
    }

    private class FixedResourceContextProvider : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "00000000-0000-0000-0000-00000000049d",
        )
    }

    private class FixedOAuthStateSigner : OAuthStateSigner {
        override fun sign(payload: LinkedInOAuthStatePayload): String = "state-193"

        override fun verify(state: String): LinkedInOAuthStatePayload = LinkedInOAuthStatePayload(
            provider = SocialProvider.LINKEDIN,
            workspaceId = "00000000-0000-0000-0000-00000000049d",
            principalId = "00000000-0000-0000-0000-000000000430",
            redirectUri = "https://app.example.com/callback",
            nonce = "nonce-193",
            issuedAt = Instant.parse("2026-05-26T12:00:00Z"),
            expiresAt = Instant.parse("2026-05-26T12:10:00Z"),
        )
    }

    private class AllowlistedAuthorizationUrlBuilder : LinkedInAuthorizationUrlBuilder {
        override fun buildAuthorizationUrl(state: String, redirectUri: String): String =
            "https://linkedin.example/authorize?state=$state"

        override fun isConfigured(): Boolean = true

        override fun isAllowedRedirectUri(redirectUri: String): Boolean =
            redirectUri == "https://app.example.com/callback"
    }

    private class FakeSocialConnectionProvider : SocialConnectionProvider {
        override suspend fun completeConnection(command: CompleteProviderConnectionCommand): ProviderConnectionResult =
            ProviderConnectionResult(
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-connection-193",
                credentialReference = "secret-ref-193",
                account = ProviderAccountProfile(
                    providerAccountId = "linkedin-account-193",
                    displayName = "Issue 193",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    profileUrn = "urn:li:person:193",
                ),
            )
    }

    private class FailingSocialAccountRepository(private val delegate: SocialAccountRepository) :
        SocialAccountRepository by delegate {
        override suspend fun upsert(account: SocialAccount): SocialAccount = throw InjectedSocialAccountFailure()
    }

    private class CapturingChannelEventPublisher : ChannelEventPublisher {
        val events = mutableListOf<ChannelEvent>()

        override fun publish(event: ChannelEvent) {
            events += event
        }
    }

    private class FailingJobRepository(
        private val delegate: PublicationJobRepository,
        private val failEnqueue: Boolean = false,
        private val failReplace: Boolean = false,
        private val failCancel: Boolean = false,
    ) : PublicationJobRepository by delegate {
        override suspend fun enqueue(job: PublicationJob) {
            if (failEnqueue) throw InjectedJobFailure()
            delegate.enqueue(job)
        }

        override suspend fun replaceForPublication(job: PublicationJob) {
            if (failReplace) throw InjectedJobFailure()
            delegate.replaceForPublication(job)
        }

        override suspend fun cancel(jobId: String, cancelledAt: Instant) {
            if (failCancel) throw InjectedJobFailure()
            delegate.cancel(jobId, cancelledAt)
        }

        override suspend fun claimNextDue(now: Instant, workerId: String, claimLease: Duration): PublicationJobClaim? =
            delegate.claimNextDue(now, workerId, claimLease)
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("publishing_transactions_postgres")
    }
}
