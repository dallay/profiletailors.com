package com.profiletailors.smp.leadcapture.infrastructure.governance

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistConsentRecordRequest
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistConsentRecorder
import com.profiletailors.smp.governance.application.RecordConsentCommand
import com.profiletailors.smp.governance.application.RecordConsentHandler
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
internal class GovernanceWaitlistConsentRecorder(private val recordConsentHandler: RecordConsentHandler) :
    WaitlistConsentRecorder {

    override fun record(request: WaitlistConsentRecordRequest) {
        val subject = SubjectReference.anonymous(anonymousSubjectHash(request.normalizedEmail))
        val workspaceId = "waitlist:${request.waitlistKey.value}"
        val locale = request.locale?.value ?: DEFAULT_LOCALE
        val source = request.source.value

        runBlocking {
            recordConsentHandler.handle(
                RecordConsentCommand(
                    workspaceId = workspaceId,
                    subjectReference = subject,
                    consentType = ConsentType.CONSENT,
                    purpose = EARLY_ACCESS_PURPOSE,
                    policyVersion = request.consent.version,
                    source = source,
                    locale = locale,
                ),
            )

            if (request.consent.marketing) {
                recordConsentHandler.handle(
                    RecordConsentCommand(
                        workspaceId = workspaceId,
                        subjectReference = subject,
                        consentType = ConsentType.CONSENT,
                        purpose = MARKETING_PURPOSE,
                        policyVersion = request.consent.version,
                        source = source,
                        locale = locale,
                    ),
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_LOCALE = "en"
        private const val EARLY_ACCESS_PURPOSE = "waitlist.early_access"
        private const val MARKETING_PURPOSE = "marketing.emails"

        fun anonymousSubjectHash(email: NormalizedEmail): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(email.value.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
