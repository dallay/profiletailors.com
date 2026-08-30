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
import com.profiletailors.smp.publishing.domain.BulkImportJob
import com.profiletailors.smp.publishing.domain.BulkImportJobRepository
import com.profiletailors.smp.publishing.domain.BulkImportRow
import com.profiletailors.smp.publishing.domain.BulkJobStatus
import com.profiletailors.smp.publishing.domain.BulkRowStatus
import com.profiletailors.smp.publishing.domain.BulkTemplate
import com.profiletailors.smp.publishing.domain.BulkValidationPipeline
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
        if (command.workspaceId != workspaceId) {
            throw BulkWorkspaceMismatchException("Workspace path does not match the authenticated workspace.")
        }
        val principalId = principalCtx.principalId
        val csvHash = computeSha256(command.csvText)
        val idempotencyKey = BulkImportJob.computeIdempotencyKey(workspaceId, principalId, csvHash)
        val existing = bulkImportJobRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            throw DuplicateBulkImportException(existing.id)
        }
        val validation = validationPipeline.validate(workspaceId, command.csvText)
        val totalRows = validation.rows.size
        val now = clock.instant()
        val job = BulkImportJob(
            id = "bulk-${UUID.randomUUID()}",
            workspaceId = workspaceId,
            principalId = principalId,
            idempotencyKey = idempotencyKey,
            csvHash = csvHash,
            status = BulkJobStatus.SCHEDULING,
            totalRows = totalRows,
            createdAt = now,
        )
        bulkImportJobRepository.save(job)
        if (totalRows == 0) {
            val finished = job.withCounts(0, 0)
            bulkImportJobRepository.save(finished)
            return ScheduleBulkResult(finished.id, 0, 0, 0, emptyList())
        }
        val socialAccountId = resolveSocialAccountId(workspaceId)
        val chunkSize = 50
        val allRows = mutableListOf<BulkImportRow>()
        val resultRows = mutableListOf<BulkRowResult>()
        var scheduledCount = 0
        var failedCount = 0
        for (chunk in validation.rows.chunked(chunkSize)) {
            val chunkResult = transactionRunner.runAtomically {
                val chunkRows = mutableListOf<BulkImportRow>()
                val chunkResultRows = mutableListOf<BulkRowResult>()
                var chunkScheduled = 0
                var chunkFailed = 0
                for (validated in chunk) {
                    val errors = validated.errors.toMutableList()
                    val isInvalid = validated.status == BulkRowStatus.INVALID
                    if (isInvalid) {
                        val row = BulkImportRow(
                            id = "brow-${UUID.randomUUID()}",
                            jobId = job.id,
                            rowIndex = validated.rowIndex,
                            status = BulkRowStatus.FAILED,
                            errors = errors,
                            bodyText = validated.bodyText,
                            scheduledFor = validated.scheduledFor,
                            mediaUrls = validated.mediaUrls,
                            hasConflict = validated.hasConflict,
                        )
                        chunkRows.add(row)
                        chunkResultRows.add(
                            BulkRowResult(
                                rowIndex = validated.rowIndex,
                                status = BulkRowStatus.FAILED.name,
                                errors = errors.map { BulkErrorResult(it.code, it.message) },
                                bodyText = validated.bodyText,
                                scheduledFor = validated.scheduledFor,
                                mediaUrls = validated.mediaUrls,
                                hasConflict = validated.hasConflict,
                            ),
                        )
                        chunkFailed++
                    } else {
                        try {
                            val publication = publicationCreationService.create(
                                workspaceId = workspaceId,
                                principalId = principalId,
                                socialAccountId = socialAccountId,
                                bodyText = validated.bodyText,
                                scheduledFor = validated.scheduledFor,
                                mediaUrls = validated.mediaUrls,
                            )
                            val row = BulkImportRow(
                                id = "brow-${UUID.randomUUID()}",
                                jobId = job.id,
                                rowIndex = validated.rowIndex,
                                status = BulkRowStatus.SCHEDULED,
                                errors = emptyList(),
                                publicationId = publication.id,
                                bodyText = validated.bodyText,
                                scheduledFor = validated.scheduledFor,
                                mediaUrls = validated.mediaUrls,
                                hasConflict = validated.hasConflict,
                            )
                            chunkRows.add(row)
                            chunkResultRows.add(
                                BulkRowResult(
                                    rowIndex = validated.rowIndex,
                                    status = BulkRowStatus.SCHEDULED.name,
                                    errors = emptyList(),
                                    bodyText = validated.bodyText,
                                    scheduledFor = validated.scheduledFor,
                                    mediaUrls = validated.mediaUrls,
                                    hasConflict = validated.hasConflict,
                                ),
                            )
                            chunkScheduled++
                        } catch (ex: Exception) {
                            val code = when (ex) {
                                is PublicationValidationException -> "INVALID_MEDIA"
                                is IllegalArgumentException -> if (ex.message?.contains("CAPABILITY") ==
                                    true
                                ) {
                                    "CAPABILITY_VIOLATION"
                                } else {
                                    "INVALID_DATE"
                                }
                                else -> "UNKNOWN"
                            }
                            val importError = ImportError(code = code, message = ex.message ?: "failed")
                            val row = BulkImportRow(
                                id = "brow-${UUID.randomUUID()}",
                                jobId = job.id,
                                rowIndex = validated.rowIndex,
                                status = BulkRowStatus.FAILED,
                                errors = listOf(importError),
                                bodyText = validated.bodyText,
                                scheduledFor = validated.scheduledFor,
                                mediaUrls = validated.mediaUrls,
                                hasConflict = validated.hasConflict,
                            )
                            chunkRows.add(row)
                            chunkResultRows.add(
                                BulkRowResult(
                                    rowIndex = validated.rowIndex,
                                    status = BulkRowStatus.FAILED.name,
                                    errors = listOf(BulkErrorResult(importError.code, importError.message)),
                                    bodyText = validated.bodyText,
                                    scheduledFor = validated.scheduledFor,
                                    mediaUrls = validated.mediaUrls,
                                    hasConflict = validated.hasConflict,
                                ),
                            )
                            chunkFailed++
                        }
                    }
                }
                bulkImportJobRepository.saveRows(chunkRows)
                Triple(chunkRows, chunkResultRows, chunkScheduled to chunkFailed)
            }
            allRows.addAll(chunkResult.first)
            resultRows.addAll(chunkResult.second)
            scheduledCount += chunkResult.third.first
            failedCount += chunkResult.third.second
        }
        val finalStatus = when {
            failedCount == 0 && scheduledCount == totalRows -> BulkJobStatus.SCHEDULED
            scheduledCount == 0 && failedCount == totalRows && totalRows > 0 -> BulkJobStatus.FAILED
            else -> BulkJobStatus.PARTIAL
        }
        val updatedJob = job.copy(
            status = finalStatus,
            scheduledCount = scheduledCount,
            failedCount = failedCount,
            updatedAt = clock.instant(),
        )
        bulkImportJobRepository.save(updatedJob)
        return ScheduleBulkResult(updatedJob.id, totalRows, scheduledCount, failedCount, resultRows)
    }

    private suspend fun resolveSocialAccountId(workspaceId: String): String {
        val account = socialAccountRepository.findFirstActiveByWorkspace(workspaceId)
            ?: throw PublicationValidationException("No active social account found for workspace $workspaceId")
        return account.id
    }

    private fun computeSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
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
