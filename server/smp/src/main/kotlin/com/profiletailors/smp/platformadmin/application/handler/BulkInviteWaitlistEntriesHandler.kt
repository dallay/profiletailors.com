package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.smp.platformadmin.application.OptimisticLockException
import com.profiletailors.smp.platformadmin.application.command.BULK_INVITE_MAX_ENTRIES
import com.profiletailors.smp.platformadmin.application.command.BulkEntryResult
import com.profiletailors.smp.platformadmin.application.command.BulkInviteOutcome
import com.profiletailors.smp.platformadmin.application.command.BulkInviteSummary
import com.profiletailors.smp.platformadmin.application.command.BulkInviteWaitlistEntriesCommand
import com.profiletailors.smp.platformadmin.application.command.BulkInviteWaitlistEntriesResult
import com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationVersionConflictException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyConvertedException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotInvitableException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryVersionConflictException
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

open class BulkInviteWaitlistEntriesHandler(
    private val singleHandler: InviteWaitlistEntryHandler,
    private val waitlistEntryAdmin: WaitlistEntryAdmin,
    private val transactionRunner: AtomicTransactionRunner,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val telemetry: InvitationTelemetry,
    private val clock: Clock,
) {

    suspend fun handle(command: BulkInviteWaitlistEntriesCommand): BulkInviteWaitlistEntriesResult {
        val entryIds = validatedEntryIds(command.entryIds)
        if (PlatformPermission.WAITLIST_INVITE !in command.operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.WAITLIST_INVITE)
        }
        val results = entryIds.map { entryId -> inviteOne(command, entryId) }
        val summary = BulkInviteSummary(
            requested = results.size,
            invited = results.count { it.outcome == BulkInviteOutcome.INVITED },
            skipped = results.count { it.outcome == BulkInviteOutcome.SKIPPED },
            failed = results.count { it.outcome == BulkInviteOutcome.FAILED },
        )
        telemetry.recordBulkInvite(
            requested = summary.requested,
            invited = summary.invited,
            skipped = summary.skipped,
            failed = summary.failed,
        )
        return BulkInviteWaitlistEntriesResult(results = results, summary = summary)
    }

    private fun validatedEntryIds(entryIds: List<String>): List<String> {
        require(entryIds.isNotEmpty()) { "At least one waitlist entry id is required" }
        require(entryIds.size <= BULK_INVITE_MAX_ENTRIES) {
            "Bulk invite supports at most $BULK_INVITE_MAX_ENTRIES entries"
        }
        require(entryIds.all { it.isNotBlank() }) { "Waitlist entry ids must not be blank" }
        return entryIds.distinct()
    }

    private suspend fun inviteOne(command: BulkInviteWaitlistEntriesCommand, entryId: String): BulkEntryResult {
        if (waitlistEntryAdmin.findById(entryId)?.status == WaitlistEntryStatus.INVITED) {
            return skipped(command, entryId, ALREADY_INVITED)
        }
        return try {
            val summary = transactionRunner.runAtomically {
                singleHandler.handle(
                    InviteWaitlistEntryCommand(
                        operatorPrincipalId = command.operatorPrincipalId,
                        operatorRoles = command.operatorRoles,
                        waitlistEntryId = entryId,
                    ),
                )
            }
            telemetry.recordInvitationCreated()
            BulkEntryResult(entryId = entryId, outcome = BulkInviteOutcome.INVITED, invitationId = summary.id)
        } catch (ex: Exception) {
            failed(command, entryId, ex)
        }
    }

    private suspend fun skipped(
        command: BulkInviteWaitlistEntriesCommand,
        entryId: String,
        code: String,
    ): BulkEntryResult {
        publishEntryAudit(command, entryId, code, AdminAuditResult.REJECTED)
        return BulkEntryResult(entryId = entryId, outcome = BulkInviteOutcome.SKIPPED, code = code)
    }

    private suspend fun failed(
        command: BulkInviteWaitlistEntriesCommand,
        entryId: String,
        ex: Exception,
    ): BulkEntryResult {
        val failure = mapFailure(ex)
        publishEntryAudit(command, entryId, failure.code, failure.auditResult)
        return BulkEntryResult(entryId = entryId, outcome = failure.outcome, code = failure.code)
    }

    private fun mapFailure(ex: Exception): EntryFailure = when {
        ex is InvitationAlreadyActiveException ->
            EntryFailure(BulkInviteOutcome.SKIPPED, INVITATION_ALREADY_ACTIVE, AdminAuditResult.REJECTED)
        ex is WaitlistEntryNotFoundException ->
            EntryFailure(BulkInviteOutcome.FAILED, ENTRY_NOT_FOUND, AdminAuditResult.FAILED)
        ex is WaitlistEntryAlreadyConvertedException ->
            EntryFailure(BulkInviteOutcome.FAILED, ENTRY_ALREADY_CONVERTED, AdminAuditResult.FAILED)
        ex is WaitlistEntryNotInvitableException ->
            EntryFailure(BulkInviteOutcome.FAILED, ENTRY_NOT_INVITABLE, AdminAuditResult.FAILED)
        ex is InvitationVersionConflictException ||
            ex is OptimisticLockException ||
            ex is WaitlistEntryVersionConflictException ||
            isUniqueViolation(ex) ->
            EntryFailure(BulkInviteOutcome.FAILED, VERSION_CONFLICT, AdminAuditResult.FAILED)
        else ->
            EntryFailure(BulkInviteOutcome.FAILED, UNEXPECTED_ERROR, AdminAuditResult.FAILED)
    }

    private fun isUniqueViolation(ex: Throwable): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            val name = current::class.simpleName ?: ""
            if (UNIQUE_VIOLATION_MARKERS.any { marker -> marker in name }) return true
            val message = current.message?.lowercase() ?: ""
            if (UNIQUE_VIOLATION_MESSAGE_MARKERS.any { marker -> marker in message }) return true
            current = current.cause
        }
        return false
    }

    private suspend fun publishEntryAudit(
        command: BulkInviteWaitlistEntriesCommand,
        entryId: String,
        code: String,
        result: AdminAuditResult,
    ) {
        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = clock.instant(),
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.WAITLIST_ENTRY_INVITED,
                targetType = "WaitlistEntry",
                targetId = entryId,
                result = result,
                reason = code,
            ),
        )
    }

    private data class EntryFailure(
        val outcome: BulkInviteOutcome,
        val code: String,
        val auditResult: AdminAuditResult,
    )

    private companion object {
        const val ALREADY_INVITED = "ALREADY_INVITED"
        const val INVITATION_ALREADY_ACTIVE = "INVITATION_ALREADY_ACTIVE"
        const val ENTRY_NOT_FOUND = "ENTRY_NOT_FOUND"
        const val ENTRY_ALREADY_CONVERTED = "ENTRY_ALREADY_CONVERTED"
        const val ENTRY_NOT_INVITABLE = "ENTRY_NOT_INVITABLE"
        const val VERSION_CONFLICT = "VERSION_CONFLICT"
        const val UNEXPECTED_ERROR = "UNEXPECTED_ERROR"
        val UNIQUE_VIOLATION_MARKERS = listOf("DuplicateKey", "DataIntegrityViolation", "UniqueConstraint")
        val UNIQUE_VIOLATION_MESSAGE_MARKERS = listOf("unique constraint", "duplicate key")
    }
}
