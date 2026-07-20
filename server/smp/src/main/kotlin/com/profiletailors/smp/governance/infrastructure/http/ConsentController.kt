package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.governance.application.GetConsentHistoryQuery
import com.profiletailors.smp.governance.application.GetWorkspaceConsentRecordsQuery
import com.profiletailors.smp.governance.application.RecordWorkspaceConsentCommand
import com.profiletailors.smp.governance.application.WithdrawWorkspaceConsentCommand
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectKind
import com.profiletailors.smp.governance.domain.SubjectReference
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Locale

class EnumValidationException(val field: String, value: String, valid: Set<String>) :
    IllegalArgumentException("Invalid $field '$value'. Valid values: ${valid.joinToString()}")

@RestController
@RequestMapping("/api/governance/consent")
class ConsentController(private val mediator: Mediator) {
    private val subjectKinds: Set<String> = SubjectKind.entries.map { it.name }.toSet()
    private val consentTypes: Set<String> = ConsentType.entries.map { it.name }.toSet()

    /**
     * Records consent for the specified subject.
     *
     * @param request The consent details to validate and record.
     * @return A response containing the recorded consent and an appropriate HTTP status.
     * @throws EnumValidationException If the subject kind, consent type, or locale is invalid.
     */
    @PostMapping
    suspend fun record(@Valid @RequestBody request: RecordConsentRequest): ResponseEntity<ConsentRecordResponse> {
        validateEnum(SUBJECT_KIND_FIELD, request.subjectKind, subjectKinds)
        validateEnum(CONSENT_TYPE_FIELD, request.consentType, consentTypes)
        validateLocale(request.locale)
        val outcome = mediator.send(request.toCommand())
        return ResponseEntity.status(if (outcome.created) HttpStatus.CREATED else HttpStatus.OK)
            .body(outcome.record.toResponse())
    }

    /**
     * Withdraws consent for the specified subject and purpose.
     *
     * @param request The validated consent withdrawal request.
     * @return The resulting consent record.
     * @throws EnumValidationException If the subject kind is invalid.
     */
    @PostMapping("/withdraw")
    suspend fun withdraw(@Valid @RequestBody request: WithdrawConsentRequest): ConsentRecordResponse {
        validateEnum(SUBJECT_KIND_FIELD, request.subjectKind, subjectKinds)
        return mediator.send(request.toCommand()).toResponse()
    }

    /**
     * Lists workspace consent records, optionally filtered by subject kind and purpose.
     *
     * @param subjectKind Optional subject kind filter.
     * @param purpose Optional consent purpose filter.
     * @return The matching consent records.
     */
    @GetMapping
    suspend fun list(
        @RequestParam(required = false) subjectKind: String?,
        @RequestParam(required = false) purpose: String?,
    ): List<ConsentRecordResponse> {
        subjectKind?.let { validateEnum(SUBJECT_KIND_FIELD, it, subjectKinds) }
        val kind = subjectKind?.let(SubjectKind::valueOf)
        return mediator.send(GetWorkspaceConsentRecordsQuery(kind, purpose))
            .map { it.toResponse() }
            .toList()
    }

    /**
     * Retrieves the consent history for a subject and purpose.
     *
     * @param subjectKind The subject kind to retrieve consent history for.
     * @param subjectValue The subject's value.
     * @param purpose The purpose associated with the consent.
     * @return The consent records matching the specified subject and purpose.
     * @throws EnumValidationException If `subjectKind` is not valid.
     */
    @GetMapping("/history")
    suspend fun history(
        @RequestParam subjectKind: String,
        @RequestParam subjectValue: String,
        @RequestParam purpose: String,
    ): List<ConsentRecordResponse> {
        validateEnum(SUBJECT_KIND_FIELD, subjectKind, subjectKinds)
        return mediator.send(GetConsentHistoryQuery(SubjectKind.valueOf(subjectKind), subjectValue, purpose))
            .map { it.toResponse() }
            .toList()
    }

    /**
     * Validates that a value belongs to the allowed set.
     *
     * @param field The name of the field being validated.
     * @param value The value to validate.
     * @param valid The allowed values.
     * @throws EnumValidationException If the value is not in the allowed set.
     */
    private fun validateEnum(field: String, value: String, valid: Set<String>) {
        if (value !in valid) throw EnumValidationException(field, value, valid)
    }

    /**
     * Validates that a locale is an ISO 639-1 language tag.
     *
     * @param locale The locale value to validate.
     * @throws EnumValidationException If the locale is invalid.
     */
    private fun validateLocale(locale: String) {
        val parsed = runCatching { Locale.Builder().setLanguageTag(locale).build() }.getOrNull()
        if (parsed == null || parsed.language !in ISO_LANGUAGES) {
            throw EnumValidationException(LOCALE_FIELD, locale, setOf("ISO 639-1 language tag"))
        }
    }

    companion object {
        private const val SUBJECT_KIND_FIELD = "subjectKind"
        private const val CONSENT_TYPE_FIELD = "consentType"
        private const val LOCALE_FIELD = "locale"
        private val ISO_LANGUAGES: Set<String> = Locale.getISOLanguages().toSet()
    }
}

/** HTTP response DTO for a consent record, decoupling the API surface from the domain model. */
data class ConsentRecordResponse(
    val id: String,
    val workspaceId: String,
    val subjectReference: SubjectReference,
    val consentType: String,
    val purpose: String,
    val policyVersion: String,
    val source: String,
    val locale: String,
    val givenAt: Instant,
    val status: String,
    val withdrawnAt: Instant?,
    val withdrawalReason: String?,
    val createdAt: Instant,
    val version: Long,
)

private fun ConsentRecord.toResponse() = ConsentRecordResponse(
    id = id.value,
    workspaceId = workspaceId,
    subjectReference = subjectReference,
    consentType = consentType.name,
    purpose = purpose,
    policyVersion = policyVersion,
    source = source,
    locale = locale,
    givenAt = givenAt,
    status = status.name,
    withdrawnAt = withdrawnAt,
    withdrawalReason = withdrawalReason,
    createdAt = createdAt,
    version = version,
)

data class RecordConsentRequest(
    @field:NotBlank val subjectKind: String,
    @field:NotBlank val subjectValue: String,
    @field:NotBlank val consentType: String,
    @field:NotBlank val purpose: String,
    @field:NotBlank val policyVersion: String,
    @field:NotBlank val source: String,
    @field:NotBlank val locale: String,
) {
    /**
     * Converts this request into a command for recording workspace consent.
     *
     * @return The workspace consent recording command.
     */
    fun toCommand() = RecordWorkspaceConsentCommand(
        SubjectKind.valueOf(subjectKind),
        subjectValue,
        ConsentType.valueOf(consentType),
        purpose,
        policyVersion,
        source,
        locale,
    )
}

data class WithdrawConsentRequest(
    @field:NotBlank val subjectKind: String,
    @field:NotBlank val subjectValue: String,
    @field:NotBlank val purpose: String,
    @field:NotBlank val policyVersion: String,
    val reason: String? = null,
) {
    /**
     * Converts this request into a workspace consent withdrawal command.
     *
     * @return The workspace consent withdrawal command.
     */
    fun toCommand() = WithdrawWorkspaceConsentCommand(
        SubjectKind.valueOf(subjectKind),
        subjectValue,
        purpose,
        policyVersion,
        reason,
    )
}
