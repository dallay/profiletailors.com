package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.governance.application.GetConsentHistoryQuery
import com.profiletailors.smp.governance.application.GetWorkspaceConsentRecordsQuery
import com.profiletailors.smp.governance.application.RecordWorkspaceConsentCommand
import com.profiletailors.smp.governance.application.WithdrawWorkspaceConsentCommand
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectKind
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

class EnumValidationException(val field: String, value: String, valid: Set<String>) :
    IllegalArgumentException("Invalid $field '$value'. Valid values: ${valid.joinToString()}")

@RestController
@RequestMapping("/api/governance/consent")
class ConsentController(private val mediator: Mediator) {
    private val subjectKinds: Set<String> = SubjectKind.entries.map { it.name }.toSet()
    private val consentTypes: Set<String> = ConsentType.entries.map { it.name }.toSet()

    @PostMapping
    suspend fun record(@Valid @RequestBody request: RecordConsentRequest): ResponseEntity<ConsentRecord> {
        validateEnum(SUBJECT_KIND_FIELD, request.subjectKind, subjectKinds)
        validateEnum(CONSENT_TYPE_FIELD, request.consentType, consentTypes)
        validateLocale(request.locale)
        val outcome = mediator.send(request.toCommand())
        return ResponseEntity.status(if (outcome.created) HttpStatus.CREATED else HttpStatus.OK).body(outcome.record)
    }

    @PostMapping("/withdraw")
    suspend fun withdraw(@Valid @RequestBody request: WithdrawConsentRequest): ConsentRecord {
        validateEnum(SUBJECT_KIND_FIELD, request.subjectKind, subjectKinds)
        return mediator.send(request.toCommand())
    }

    @GetMapping
    suspend fun list(
        @RequestParam(required = false) subjectKind: String?,
        @RequestParam(required = false) purpose: String?,
    ): List<ConsentRecord> {
        subjectKind?.let { validateEnum(SUBJECT_KIND_FIELD, it, subjectKinds) }
        return mediator.send(GetWorkspaceConsentRecordsQuery(subjectKind, purpose))
    }

    @GetMapping("/history")
    suspend fun history(
        @RequestParam subjectKind: String,
        @RequestParam subjectValue: String,
        @RequestParam purpose: String,
    ): List<ConsentRecord> {
        validateEnum(SUBJECT_KIND_FIELD, subjectKind, subjectKinds)
        return mediator.send(GetConsentHistoryQuery(subjectKind, subjectValue, purpose))
    }

    private fun validateEnum(field: String, value: String, valid: Set<String>) {
        if (value !in valid) throw EnumValidationException(field, value, valid)
    }

    private fun validateLocale(locale: String) {
        val parsed = runCatching { java.util.Locale.Builder().setLanguageTag(locale).build() }.getOrNull()
        if (parsed == null || parsed.language !in java.util.Locale.getISOLanguages()) {
            throw EnumValidationException(LOCALE_FIELD, locale, setOf("ISO 639-1 language tag"))
        }
    }

    companion object {
        private const val SUBJECT_KIND_FIELD = "subjectKind"
        private const val CONSENT_TYPE_FIELD = "consentType"
        private const val LOCALE_FIELD = "locale"
    }
}

data class RecordConsentRequest(
    @field:NotBlank val subjectKind: String,
    @field:NotBlank val subjectValue: String,
    @field:NotBlank val consentType: String,
    @field:NotBlank val purpose: String,
    @field:NotBlank val policyVersion: String,
    @field:NotBlank val source: String,
    @field:NotBlank val locale: String,
) {
    fun toCommand() = RecordWorkspaceConsentCommand(
        subjectKind,
        subjectValue,
        consentType,
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
    fun toCommand() = WithdrawWorkspaceConsentCommand(subjectKind, subjectValue, purpose, policyVersion, reason)
}
