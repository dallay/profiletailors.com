@file:Suppress("MaxLineLength", "MagicNumber", "ReturnCount", "TooManyFunctions", "LongParameterList", "LongMethod", "CyclomaticComplexMethod", "SwallowedException", "FunctionOnlyReturningConstant", "ExpressionBodySyntax", "ktlint:standard:max-line-length")

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
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.publishing.domain.BulkImportJob
import com.profiletailors.smp.publishing.domain.BulkImportJobRepository
import com.profiletailors.smp.publishing.domain.BulkImportRow
import com.profiletailors.smp.publishing.domain.BulkJobStatus
import com.profiletailors.smp.publishing.domain.BulkRowStatus
import com.profiletailors.smp.publishing.domain.BulkRowValidation
import com.profiletailors.smp.publishing.domain.BulkTemplate
import com.profiletailors.smp.publishing.domain.BulkValidationPipeline
import com.profiletailors.smp.publishing.domain.BulkValidationResult
import com.profiletailors.smp.publishing.domain.ImportError
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

@Service
class ValidateBulkHandler(private val validationPipeline: BulkValidationPipeline) :
    CommandWithResultHandler<ValidateBulkCommand, ValidateBulkResult> {
    override suspend fun handle(command: ValidateBulkCommand): ValidateBulkResult {
        val result = validationPipeline.validate(command.workspaceId, command.csvText)
        val rows = result.rows.map { r ->
            BulkRowResult(
                rowIndex = r.rowIndex,
                status = r.status.name,
                errors = r.errors.map { BulkErrorResult(it.code, it.message) },
                bodyText = r.bodyText,
                scheduledFor = r.scheduledFor,
                mediaUrls = r.mediaUrls,
                hasConflict = r.hasConflict,
            )
        }
        return ValidateBulkResult(rows)
    }
}

@Service
@Suppress("LongParameterList")
class ScheduleBulkHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val validationPipeline: BulkValidationPipeline,
    private val bulkImportJobRepository: BulkImportJobRepository,
    private val publicationCreationService: PublicationCreationService,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val socialAccountRepository: SocialAccountRepository,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<ScheduleBulkCommand, ScheduleBulkResult> {
    override suspend fun handle(command: ScheduleBulkCommand): ScheduleBulkResult {
        val access = authorizeBulkSchedule(command.workspaceId)
        val idempotency = ensureNoDuplicate(access.workspaceId, access.principalId, command.csvText)
        val validation = validationPipeline.validate(access.workspaceId, command.csvText)
        val totalRows = validation.rows.size
        val socialAccountId = resolveSocialAccountId(access.workspaceId)
        val job = createSchedulingJob(access, idempotency, totalRows)
        bulkImportJobRepository.save(job)
        if (totalRows == 0) {
            val finished = job.withCounts(0, 0)
            bulkImportJobRepository.save(finished)
            return ScheduleBulkResult(finished.id, 0, 0, 0, emptyList())
        }
        val outcome = processAllChunks(validation, job, access, socialAccountId)
        val updatedJob = job.copy(
            status = BulkImportFinalStatus.compute(totalRows, outcome.scheduledCount, outcome.failedCount),
            scheduledCount = outcome.scheduledCount,
            failedCount = outcome.failedCount,
            updatedAt = clock.instant(),
        )
        bulkImportJobRepository.save(updatedJob)
        return ScheduleBulkResult(updatedJob.id, totalRows, outcome.scheduledCount, outcome.failedCount, outcome.rows)
    }

    private data class BulkScheduleAccess(val workspaceId: String, val principalId: String)

    private data class BulkIdempotency(val csvHash: String, val idempotencyKey: String)

    private data class BulkChunkOutcome(
        val scheduledCount: Int = 0,
        val failedCount: Int = 0,
        val rows: List<BulkRowResult> = emptyList(),
    )

    private suspend fun authorizeBulkSchedule(requestedWorkspaceId: String): BulkScheduleAccess {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.PUBLISH_CONTENT,
        )
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.SCHEDULE_POST,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        if (requestedWorkspaceId != workspaceId) {
            throw BulkWorkspaceMismatchException("Workspace path does not match the authenticated workspace.")
        }
        return BulkScheduleAccess(workspaceId, principalCtx.principalId)
    }

    private suspend fun ensureNoDuplicate(workspaceId: String, principalId: String, csvText: String): BulkIdempotency {
        val csvHash = computeSha256(csvText)
        val idempotencyKey = BulkImportJob.computeIdempotencyKey(workspaceId, principalId, csvHash)
        val existing = bulkImportJobRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            throw DuplicateBulkImportException(existing.id)
        }
        return BulkIdempotency(csvHash, idempotencyKey)
    }

    private fun createSchedulingJob(
        access: BulkScheduleAccess,
        idempotency: BulkIdempotency,
        totalRows: Int,
    ): BulkImportJob = BulkImportJob(
        id = "bulk-${UUID.randomUUID()}",
        workspaceId = access.workspaceId,
        principalId = access.principalId,
        idempotencyKey = idempotency.idempotencyKey,
        csvHash = idempotency.csvHash,
        status = BulkJobStatus.SCHEDULING,
        totalRows = totalRows,
        createdAt = clock.instant(),
    )

    private suspend fun processAllChunks(
        validation: BulkValidationResult,
        job: BulkImportJob,
        access: BulkScheduleAccess,
        socialAccountId: String,
    ): BulkChunkOutcome {
        var scheduledCount = 0
        var failedCount = 0
        val resultRows = mutableListOf<BulkRowResult>()
        for (chunk in validation.rows.chunked(BULK_CHUNK_SIZE)) {
            val chunkOutcome = transactionRunner.runAtomically {
                processChunk(chunk, job.id, access, socialAccountId)
            }
            scheduledCount += chunkOutcome.scheduledCount
            failedCount += chunkOutcome.failedCount
            resultRows.addAll(chunkOutcome.rows)
        }
        return BulkChunkOutcome(scheduledCount, failedCount, resultRows)
    }

    private suspend fun processChunk(
        chunk: List<BulkRowValidation>,
        jobId: String,
        access: BulkScheduleAccess,
        socialAccountId: String,
    ): BulkChunkOutcome {
        val chunkRows = mutableListOf<BulkImportRow>()
        val chunkResultRows = mutableListOf<BulkRowResult>()
        var chunkScheduled = 0
        var chunkFailed = 0
        for (validated in chunk) {
            val outcome = processSingleRow(validated, jobId, access, socialAccountId)
            chunkRows.add(outcome.row)
            chunkResultRows.add(outcome.result)
            if (outcome.scheduled) chunkScheduled++ else chunkFailed++
        }
        bulkImportJobRepository.saveRows(chunkRows)
        return BulkChunkOutcome(chunkScheduled, chunkFailed, chunkResultRows)
    }

    private data class BulkRowOutcome(val row: BulkImportRow, val result: BulkRowResult, val scheduled: Boolean)

    private suspend fun processSingleRow(
        validated: BulkRowValidation,
        jobId: String,
        access: BulkScheduleAccess,
        socialAccountId: String,
    ): BulkRowOutcome {
        if (validated.status == BulkRowStatus.INVALID) {
            return BulkRowOutcome(
                mapInvalidRow(validated, jobId),
                mapInvalidResult(validated),
                false,
            )
        }
        return try {
            val publication = publicationCreationService.create(
                workspaceId = access.workspaceId,
                principalId = access.principalId,
                socialAccountId = socialAccountId,
                bodyText = validated.bodyText,
                scheduledFor = validated.scheduledFor,
                mediaUrls = validated.mediaUrls,
            )
            BulkRowOutcome(
                mapScheduledRow(validated, jobId, publication.id),
                mapScheduledResult(validated),
                true,
            )
        } catch (ex: Exception) {
            if (ex is MediaServiceUnavailableException) throw ex
            val importError = BulkImportErrorMapper.map(ex)
            BulkRowOutcome(
                mapFailedRow(validated, jobId, importError),
                mapFailedResult(validated, importError),
                false,
            )
        }
    }

    private fun mapInvalidRow(validated: BulkRowValidation, jobId: String): BulkImportRow = BulkImportRow(
        id = "brow-${UUID.randomUUID()}",
        jobId = jobId,
        rowIndex = validated.rowIndex,
        status = BulkRowStatus.FAILED,
        errors = validated.errors,
        bodyText = validated.bodyText,
        scheduledFor = validated.scheduledFor,
        mediaUrls = validated.mediaUrls,
        hasConflict = validated.hasConflict,
    )

    private fun mapInvalidResult(validated: BulkRowValidation): BulkRowResult = BulkRowResult(
        rowIndex = validated.rowIndex,
        status = BulkRowStatus.FAILED.name,
        errors = validated.errors.map { BulkErrorResult(it.code, it.message) },
        bodyText = validated.bodyText,
        scheduledFor = validated.scheduledFor,
        mediaUrls = validated.mediaUrls,
        hasConflict = validated.hasConflict,
    )

    private fun mapScheduledRow(validated: BulkRowValidation, jobId: String, publicationId: String): BulkImportRow =
        BulkImportRow(
            id = "brow-${UUID.randomUUID()}",
            jobId = jobId,
            rowIndex = validated.rowIndex,
            status = BulkRowStatus.SCHEDULED,
            errors = emptyList(),
            publicationId = publicationId,
            bodyText = validated.bodyText,
            scheduledFor = validated.scheduledFor,
            mediaUrls = validated.mediaUrls,
            hasConflict = validated.hasConflict,
        )

    private fun mapScheduledResult(validated: BulkRowValidation): BulkRowResult = BulkRowResult(
        rowIndex = validated.rowIndex,
        status = BulkRowStatus.SCHEDULED.name,
        errors = emptyList(),
        bodyText = validated.bodyText,
        scheduledFor = validated.scheduledFor,
        mediaUrls = validated.mediaUrls,
        hasConflict = validated.hasConflict,
    )

    private fun mapFailedRow(validated: BulkRowValidation, jobId: String, importError: ImportError): BulkImportRow =
        BulkImportRow(
            id = "brow-${UUID.randomUUID()}",
            jobId = jobId,
            rowIndex = validated.rowIndex,
            status = BulkRowStatus.FAILED,
            errors = listOf(importError),
            bodyText = validated.bodyText,
            scheduledFor = validated.scheduledFor,
            mediaUrls = validated.mediaUrls,
            hasConflict = validated.hasConflict,
        )

    private fun mapFailedResult(validated: BulkRowValidation, importError: ImportError): BulkRowResult = BulkRowResult(
        rowIndex = validated.rowIndex,
        status = BulkRowStatus.FAILED.name,
        errors = listOf(BulkErrorResult(importError.code, importError.message)),
        bodyText = validated.bodyText,
        scheduledFor = validated.scheduledFor,
        mediaUrls = validated.mediaUrls,
        hasConflict = validated.hasConflict,
    )

    private suspend fun resolveSocialAccountId(workspaceId: String): String {
        val account = socialAccountRepository.findFirstActiveByWorkspace(workspaceId)
            ?: throw PublicationValidationException("No active social account found for workspace $workspaceId")
        return account.id
    }

    private fun computeSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BULK_CHUNK_SIZE = 50
    }
}

@Service
class GetBulkJobHandler(private val bulkImportJobRepository: BulkImportJobRepository) :
    QueryHandler<GetBulkJobQuery, BulkJobResult> {
    override suspend fun handle(query: GetBulkJobQuery): BulkJobResult {
        val job = bulkImportJobRepository.findByWorkspaceAndId(query.workspaceId, query.jobId)
            ?: throw BulkJobNotFoundException(query.jobId)
        val rows = bulkImportJobRepository.findRows(job.id)
        val rowResults = rows.map { r ->
            BulkRowResult(
                rowIndex = r.rowIndex,
                status = r.status.name,
                errors = r.errors.map { BulkErrorResult(it.code, it.message) },
                bodyText = r.bodyText,
                scheduledFor = r.scheduledFor,
                mediaUrls = r.mediaUrls,
                hasConflict = r.hasConflict,
            )
        }
        return BulkJobResult(
            jobId = job.id,
            status = job.status.name,
            totalRows = job.totalRows,
            scheduledCount = job.scheduledCount,
            failedCount = job.failedCount,
            rows = rowResults,
        )
    }
}

@Service
class BulkTemplatesHandler : QueryHandler<BulkTemplatesQuery, BulkTemplatesResult> {
    override suspend fun handle(query: BulkTemplatesQuery): BulkTemplatesResult {
        val templates = BulkTemplate.defaultTemplates().map { t ->
            BulkTemplateItem(
                id = t.id,
                name = t.name,
                description = t.description,
                header = BulkTemplate.canonicalHeader(),
            )
        }
        return BulkTemplatesResult(templates)
    }
}

@Service
class BulkTemplateCsvHandler : QueryHandler<BulkTemplateCsvQuery, BulkTemplateCsvResult> {
    override suspend fun handle(query: BulkTemplateCsvQuery): BulkTemplateCsvResult {
        val header = BulkTemplate.canonicalHeader()
        val csv = "$header\n"
        return BulkTemplateCsvResult(csv = csv, header = header)
    }
}
