package com.profiletailors.smp.governance.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.time.Instant

@ValueObject
@JvmInline
value class ConsentRecordId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConsentRecordId must not be blank" }
    }
}

/** Nature of the legal acceptance being recorded. */
@ValueObject
enum class ConsentType {
    /** Freely given, specific, informed and unambiguous consent (RGPD Art. 4.11). */
    CONSENT,

    /** Acceptance of contractual terms (Terms of Service, DPA, etc.). */
    CONTRACT_ACCEPTANCE,

    /** Reliance on a lawful basis that is not consent (RGPD Art. 6.1.f). */
    LEGITIMATE_INTEREST,
}

/** Lifecycle state of a consent record. */
@ValueObject
enum class ConsentStatus { ACTIVE, WITHDRAWN }

/** Kind of subject a consent record refers to. */
@ValueObject
enum class SubjectKind { WORKSPACE, USER, ANONYMOUS }

/**
 * Identifier of the entity that gave (or withdrew) consent.
 *
 * For workspace-scoped subjects the value is a workspace id, for authenticated
 * users it is a user id, and for pre-account subjects (e.g. a waitlist lead) it
 * is a stable identifier derived from a low-risk representation such as a
 * normalised email hash. Raw IP addresses and full email addresses are
 * deliberately not stored here.
 */
@ValueObject
data class SubjectReference(val value: String, val kind: SubjectKind) {
    init {
        require(value.isNotBlank()) { "Subject reference value must not be blank" }
    }

    companion object {
        fun workspace(id: String): SubjectReference = SubjectReference(id, SubjectKind.WORKSPACE)
        fun user(id: String): SubjectReference = SubjectReference(id, SubjectKind.USER)
        fun anonymous(id: String): SubjectReference = SubjectReference(id, SubjectKind.ANONYMOUS)
    }
}

/**
 * A versioned, append-only record of a single consent, contract acceptance or
 * legitimate-interest decision.
 *
 * Withdrawals never delete history: [withdraw] returns a new instance with
 * [status] set to [ConsentStatus.WITHDRAWN] and [withdrawnAt] populated while
 * preserving the original identity, subject reference, purpose, policy version
 * and given timestamp. This satisfies RGPD accountability (Art. 5.2) without
 * destroying the evidence of prior consent.
 */
@AggregateRoot
data class ConsentRecord(
    val id: ConsentRecordId,
    val workspaceId: String,
    val subjectReference: SubjectReference,
    val consentType: ConsentType,
    val purpose: String,
    val policyVersion: String,
    val source: String,
    val locale: String,
    val givenAt: Instant,
    val status: ConsentStatus = ConsentStatus.ACTIVE,
    val withdrawnAt: Instant? = null,
    val withdrawalReason: String? = null,
    val createdAt: Instant = Instant.now(),
    val version: Long = 1,
) {
    init {
        require(workspaceId.isNotBlank()) { "Consent workspaceId must not be blank" }
        require(purpose.isNotBlank()) { "Consent purpose must not be blank" }
        require(policyVersion.isNotBlank()) { "Consent policy version must not be blank" }
        require(source.isNotBlank()) { "Consent source must not be blank" }
        require(locale.isNotBlank()) { "Consent locale must not be blank" }
    }

    /**
     * Returns a new [ConsentRecord] representing the withdrawal of this record.
     *
     * @throws IllegalStateException if this record is already withdrawn.
     * @throws IllegalArgumentException if [at] is not strictly after [givenAt].
     */
    fun withdraw(at: Instant, reason: String? = null): ConsentRecord {
        check(status == ConsentStatus.ACTIVE) {
            "Consent record ${id.value} is already withdrawn"
        }
        require(at.isAfter(givenAt)) {
            "Withdrawal timestamp ($at) must be after givenAt ($givenAt)"
        }
        return copy(
            status = ConsentStatus.WITHDRAWN,
            withdrawnAt = at,
            withdrawalReason = reason,
        )
    }

    /** Returns `true` when this record currently represents an active consent. */
    fun isActive(): Boolean = status == ConsentStatus.ACTIVE
}
