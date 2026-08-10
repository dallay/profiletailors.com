package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class ConsentRecordModelsTest {

    private val givenAt = Instant.parse("2026-07-17T10:00:00Z")

    @Test
    fun `creates consent record with required fields`() {
        val record = ConsentRecord(
            id = ConsentRecordId("cs-001"),
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
            givenAt = givenAt,
        )

        assertEquals("cs-001", record.id.value)
        assertEquals("ws-001", record.subjectReference.value)
        assertEquals(SubjectKind.WORKSPACE, record.subjectReference.kind)
        assertEquals(ConsentType.CONSENT, record.consentType)
        assertEquals("waitlist.early_access", record.purpose)
        assertEquals("2026-07-01", record.policyVersion)
        assertEquals(ConsentStatus.ACTIVE, record.status)
        assertEquals(givenAt, record.givenAt)
        assertEquals(null, record.withdrawnAt)
        assertNotNull(record.createdAt)
    }

    @Test
    fun `withdrawal preserves historical record and sets status to WITHDRAWN`() {
        val record = ConsentRecord(
            id = ConsentRecordId("cs-001"),
            workspaceId = "ws-001",
            subjectReference = SubjectReference.user("user-001"),
            consentType = ConsentType.CONSENT,
            purpose = "marketing_emails",
            policyVersion = "2026-07-01",
            source = "settings_update",
            locale = "en-US",
            givenAt = givenAt,
        )

        val withdrawnAt = Instant.parse("2026-08-01T10:00:00Z")
        val withdrawn = record.withdraw(at = withdrawnAt)

        assertEquals(ConsentStatus.WITHDRAWN, withdrawn.status)
        assertEquals(withdrawnAt, withdrawn.withdrawnAt)
        assertEquals(record.givenAt, withdrawn.givenAt)
        assertEquals(record.id, withdrawn.id)
        assertEquals(record.subjectReference, withdrawn.subjectReference)
        assertEquals(record.purpose, withdrawn.purpose)
    }

    @Test
    fun `cannot withdraw an already withdrawn record`() {
        val record = ConsentRecord(
            id = ConsentRecordId("cs-001"),
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
            givenAt = givenAt,
        ).withdraw(at = Instant.parse("2026-08-01T10:00:00Z"))

        assertThrows<IllegalStateException> {
            record.withdraw(at = Instant.parse("2026-09-01T10:00:00Z"))
        }
    }

    @Test
    fun `withdraw with timestamp before givenAt throws`() {
        val record = ConsentRecord(
            id = ConsentRecordId("cs-001"),
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
            givenAt = givenAt,
        )

        assertThrows<IllegalArgumentException> {
            record.withdraw(at = Instant.parse("2026-07-01T00:00:00Z"))
        }
    }

    @Test
    fun `subject reference distinguishes workspace and user kinds`() {
        val ws = SubjectReference.workspace("ws-001")
        val user = SubjectReference.user("user-001")
        val anon = SubjectReference.anonymous("email-hash-001")

        assertEquals(SubjectKind.WORKSPACE, ws.kind)
        assertEquals(SubjectKind.USER, user.kind)
        assertEquals(SubjectKind.ANONYMOUS, anon.kind)
        assertEquals("ws-001", ws.value)
        assertEquals("user-001", user.value)
    }

    @Test
    fun `consent type supports all three categories`() {
        assertEquals(3, ConsentType.entries.size)
        assertTrue(ConsentType.CONSENT in ConsentType.entries)
        assertTrue(ConsentType.CONTRACT_ACCEPTANCE in ConsentType.entries)
        assertTrue(ConsentType.LEGITIMATE_INTEREST in ConsentType.entries)
    }

    @Test
    fun `subject reference rejects blank values`() {
        assertThrows<IllegalArgumentException> { SubjectReference.workspace("") }
        assertThrows<IllegalArgumentException> { SubjectReference.user("   ") }
        assertThrows<IllegalArgumentException> { SubjectReference.anonymous("") }
    }

    @Test
    fun `consent record id rejects blank values`() {
        assertThrows<IllegalArgumentException> { ConsentRecordId("") }
        assertThrows<IllegalArgumentException> { ConsentRecordId("   ") }
    }

    @Test
    fun `consent record rejects blank purpose and policy version`() {
        assertThrows<IllegalArgumentException> {
            ConsentRecord(
                id = ConsentRecordId("cs-001"),
                workspaceId = "ws-001",
                subjectReference = SubjectReference.workspace("ws-001"),
                consentType = ConsentType.CONSENT,
                purpose = "",
                policyVersion = "2026-07-01",
                source = "waitlist_join",
                locale = "es-ES",
                givenAt = givenAt,
            )
        }

        assertThrows<IllegalArgumentException> {
            ConsentRecord(
                id = ConsentRecordId("cs-001"),
                workspaceId = "ws-001",
                subjectReference = SubjectReference.workspace("ws-001"),
                consentType = ConsentType.CONSENT,
                purpose = "waitlist.early_access",
                policyVersion = "",
                source = "waitlist_join",
                locale = "es-ES",
                givenAt = givenAt,
            )
        }
    }

    @Test
    fun `isActive helper reflects current status`() {
        val active = ConsentRecord(
            id = ConsentRecordId("cs-001"),
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
            givenAt = givenAt,
        )
        assertTrue(active.isActive())

        val withdrawn = active.withdraw(at = Instant.parse("2026-08-01T10:00:00Z"))
        assertFalse(withdrawn.isActive())
    }
}
