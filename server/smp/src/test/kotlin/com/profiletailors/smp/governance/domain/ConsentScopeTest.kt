package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ConsentScopeTest {
    @Test
    fun `workspace and waitlist remain distinct when entity UUIDs match`() {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val workspace: ConsentScope = ConsentScope.Workspace(id)
        val waitlist: ConsentScope = ConsentScope.Waitlist(id)

        assertNotEquals(workspace, waitlist)
        assertEquals(workspace, ConsentScope.Workspace(id))
        assertEquals(waitlist, ConsentScope.Waitlist(id))
    }

    @Test
    fun `withdrawal retains the waitlist identity and leaves original consent unchanged`() {
        val scope = ConsentScope.Waitlist(UUID.fromString("00000000-0000-0000-0000-000000000101"))
        val record = ConsentRecord(
            id = ConsentRecordId(UUID.randomUUID().toString()),
            scope = scope,
            subjectReference = SubjectReference.anonymous("opaque-email-hash"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-10-26",
            source = "public-waitlist",
            locale = "en",
            givenAt = Instant.parse("2026-10-26T10:00:00Z"),
        )

        val withdrawn = record.withdraw(record.givenAt.plusSeconds(60), "subject request")

        assertEquals(scope, withdrawn.scope)
        assertEquals(record.id, withdrawn.id)
        assertEquals(record.subjectReference, withdrawn.subjectReference)
        assertEquals(ConsentStatus.ACTIVE, record.status)
        assertEquals(ConsentStatus.WITHDRAWN, withdrawn.status)
    }
}
