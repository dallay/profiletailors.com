package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.RecurrenceRule
import com.profiletailors.smp.publishing.domain.RecurringSchedule
import com.profiletailors.smp.publishing.domain.RecurringScheduleRepository
import com.profiletailors.smp.publishing.domain.RecurringScheduleStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private const val DEFAULT_HORIZON_DAYS = 30L

data class CreateRecurringScheduleCommand(
    val templatePostId: String,
    val recurrenceRule: RecurrenceRule,
    val startsAt: Instant,
    val timezone: String = "UTC",
) : com.profiletailors.common.domain.bus.command.CommandWithResult<RecurringScheduleResult>

data class UpdateRecurringScheduleCommand(
    val id: String,
    val recurrenceRule: RecurrenceRule? = null,
    val startsAt: Instant? = null,
    val timezone: String? = null,
    val status: RecurringScheduleStatus? = null,
) : com.profiletailors.common.domain.bus.command.CommandWithResult<RecurringScheduleResult>

data class DeleteRecurringScheduleCommand(val id: String) :
    com.profiletailors.common.domain.bus.command.CommandWithResult<Unit>
data object ListRecurringSchedulesQuery : com.profiletailors.common.domain.bus.query.Query<RecurringSchedulesResponse>
data class RecurringSchedulesResponse(val schedules: List<RecurringScheduleResult>)
data class RecurringScheduleResult(
    val id: String,
    val workspaceId: String,
    val createdBy: String,
    val templatePostId: String,
    val frequency: String,
    val interval: Int,
    val daysOfWeek: Set<Int>,
    val dayOfMonth: Int?,
    val endDate: LocalDate?,
    val maxOccurrences: Int?,
    val timezone: String,
    val nextScheduledAt: Instant?,
    val status: RecurringScheduleStatus,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

@Service
class CreateRecurringScheduleHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val scheduleRepository: RecurringScheduleRepository,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val schedulingPolicy: com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<CreateRecurringScheduleCommand, RecurringScheduleResult> {
    override suspend fun handle(command: CreateRecurringScheduleCommand): RecurringScheduleResult {
        val principal = principalContextProvider.require()
        requireEmailVerification(principal, principalIdentityLookup, emailVerificationPolicy, AuthFeature.SCHEDULE_POST)
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val source = publicationRepository.findByWorkspaceAndId(workspaceId, command.templatePostId)
            ?: throw PublicationNotFoundException(command.templatePostId)
        require(source.status in setOf(PublicationStatus.PUBLISHED, PublicationStatus.SCHEDULED)) {
            "Recurring schedules require a published or scheduled template post."
        }
        val zone = ZoneId.of(command.timezone)
        val now = clock.instant()
        require(!command.startsAt.isBefore(now.plusSeconds(1))) { "startsAt must be in the future." }
        val rule = command.recurrenceRule
        val start = command.startsAt.atZone(zone)
        val until = rule.endDate ?: start.toLocalDate().plusDays(DEFAULT_HORIZON_DAYS)
        val occurrences = rule.occurrences(start, until)
        require(occurrences.isNotEmpty()) { "The recurrence rule produces no occurrences." }
        val schedule = RecurringSchedule(
            id = "recur-${UUID.randomUUID()}", workspaceId = workspaceId, createdBy = principal.principalId,
            templatePostId = source.id, recurrenceRule = rule, timezone = zone.id,
            nextScheduledAt = occurrences.first().toInstant(), status = RecurringScheduleStatus.ACTIVE,
            createdAt = now, updatedAt = now,
        )
        transactionRunner.runAtomically {
            scheduleRepository.create(schedule)
            occurrences.map { occurrence ->
                val publication = PublicationLifecyclePolicy.queue(
                    PublicationDraft(
                        id = "pub-${UUID.randomUUID()}", workspaceId = workspaceId,
                        authorPrincipalId = principal.principalId, provider = source.provider,
                        socialAccountId = source.socialAccountId, status = PublicationStatus.DRAFT,
                        scheduleMode = ScheduleMode.SCHEDULED_AT, priority = source.priority,
                        title = source.title, bodyText = source.bodyText, assetIds = source.assetIds,
                        scheduledFor = occurrence.toInstant(), createdAt = now, updatedAt = now,
                    ),
                    occurrence.toInstant(),
                )
                publicationRepository.createDraft(publication)
                publicationJobRepository.enqueue(replacementJobFor(publication, schedulingPolicy, now))
            }
        }
        return schedule.toResult()
    }
}

@Service
class ListRecurringSchedulesHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val scheduleRepository: RecurringScheduleRepository,
) : QueryHandler<ListRecurringSchedulesQuery, RecurringSchedulesResponse> {
    override suspend fun handle(query: ListRecurringSchedulesQuery): RecurringSchedulesResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        return RecurringSchedulesResponse(
            scheduleRepository.findByWorkspace(workspaceId).map(RecurringSchedule::toResult),
        )
    }
}

@Service
class UpdateRecurringScheduleHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val scheduleRepository: RecurringScheduleRepository,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<UpdateRecurringScheduleCommand, RecurringScheduleResult> {
    /**
     * Updates a recurring publication schedule within the current workspace.
     *
     * @param command The command containing the schedule identifier and optional updated settings.
     * @return The updated recurring schedule.
     * @throws RecurringScheduleNotFoundException If the schedule does not exist in the current workspace.
     */
    override suspend fun handle(command: UpdateRecurringScheduleCommand): RecurringScheduleResult {
        val principal = principalContextProvider.require()
        requireEmailVerification(principal, principalIdentityLookup, emailVerificationPolicy, AuthFeature.SCHEDULE_POST)
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current =
            scheduleRepository.findByWorkspaceAndId(workspaceId, command.id)
                ?: throw RecurringScheduleNotFoundException(command.id)
        val rule = command.recurrenceRule ?: current.recurrenceRule
        val zone = ZoneId.of(command.timezone ?: current.timezone)
        val start = (command.startsAt ?: current.nextScheduledAt ?: clock.instant()).atZone(zone)
        val next = rule.occurrences(
            start,
            rule.endDate ?: start.toLocalDate().plusDays(DEFAULT_HORIZON_DAYS),
        ).firstOrNull()?.toInstant()
        return scheduleRepository.update(
            current.copy(
                recurrenceRule = rule,
                timezone = zone.id,
                nextScheduledAt = next,
                status =
                command.status ?: current.status,
                updatedAt = clock.instant(),
            ),
        ).toResult()
    }
}

@Service
class DeleteRecurringScheduleHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val scheduleRepository: RecurringScheduleRepository,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<DeleteRecurringScheduleCommand, Unit> {
    /**
     * Deletes a recurring schedule from the current workspace.
     *
     * @param command The command identifying the recurring schedule to delete.
     * @throws RecurringScheduleNotFoundException If the schedule does not exist in the current workspace.
     */
    override suspend fun handle(command: DeleteRecurringScheduleCommand) {
        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.SCHEDULE_POST,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        if (!scheduleRepository.delete(workspaceId, command.id)) {
            throw RecurringScheduleNotFoundException(command.id)
        }
    }
}

private fun RecurringSchedule.toResult() = RecurringScheduleResult(
    id, workspaceId, createdBy, templatePostId, recurrenceRule.frequency.name.lowercase(), recurrenceRule.interval,
    recurrenceRule.daysOfWeek, recurrenceRule.dayOfMonth, recurrenceRule.endDate, recurrenceRule.maxOccurrences,
    timezone, nextScheduledAt, status, createdAt, updatedAt,
)
