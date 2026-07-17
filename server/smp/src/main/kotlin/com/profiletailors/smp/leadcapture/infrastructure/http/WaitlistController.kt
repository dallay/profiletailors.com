package com.profiletailors.smp.leadcapture.infrastructure.http

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.waitlist.application.JoinWaitlistCommand
import com.profiletailors.leadcapture.waitlist.application.JoinWaitlistHandler
import com.profiletailors.leadcapture.waitlist.domain.WaitlistClosedException
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/waitlists")
class WaitlistController(private val joinWaitlist: JoinWaitlistHandler) {

    @PostMapping("/{waitlistKey}/entries")
    fun join(@PathVariable waitlistKey: String, @RequestBody request: JoinWaitlistRequest): ResponseEntity<Any> = try {
        joinWaitlist.handle(request.toCommand(waitlistKey))
        ResponseEntity.status(HttpStatus.ACCEPTED).body(JoinWaitlistResponse())
    } catch (_: WaitlistNotFoundException) {
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(WaitlistErrorResponse(WAITLIST_NOT_FOUND_ERROR))
    } catch (_: WaitlistClosedException) {
        ResponseEntity.status(HttpStatus.CONFLICT).body(WaitlistErrorResponse(WAITLIST_CLOSED_ERROR))
    } catch (error: IllegalArgumentException) {
        error.toPublicErrorCode()
            ?.let { code -> ResponseEntity.badRequest().body(WaitlistErrorResponse(code)) }
            ?: throw error
    }

    private fun JoinWaitlistRequest.toCommand(waitlistKey: String): JoinWaitlistCommand = JoinWaitlistCommand(
        waitlistKey = WaitlistKey(waitlistKey),
        email = EmailAddress(requireNotNull(email) { INVALID_EMAIL_ERROR }),
        source = CaptureSource(requireNotNull(source) { INVALID_SOURCE_ERROR }),
        formId = formId,
        locale = locale?.let(::CaptureLocale),
        metadata = metadata.toLeadMetadata(),
        consent = WaitlistConsent(
            earlyAccess = requireNotNull(consent?.earlyAccess) { CONSENT_REQUIRED_ERROR },
            marketing = consent.marketing ?: false,
            version = consent.version ?: DEFAULT_CONSENT_VERSION,
        ),
    )

    private fun Map<String, String>?.toLeadMetadata(): LeadMetadata = LeadMetadata(
        utmSource = this?.get("utm_source"),
        utmMedium = this?.get("utm_medium"),
        utmCampaign = this?.get("utm_campaign"),
        utmContent = this?.get("utm_content"),
        utmTerm = this?.get("utm_term"),
        referrer = this?.get("referrer"),
        pagePath = this?.get("page_path"),
        userAgentFamily = this?.get("user_agent_family"),
        consentVersion = this?.get("consent_version"),
    )

    private fun IllegalArgumentException.toPublicErrorCode(): String? = when {
        message == CONSENT_REQUIRED_ERROR -> CONSENT_REQUIRED_ERROR
        message == INVALID_SOURCE_ERROR -> INVALID_SOURCE_ERROR
        message?.startsWith("Email address") == true -> INVALID_EMAIL_ERROR
        message?.startsWith("Capture source") == true -> INVALID_SOURCE_ERROR
        message?.startsWith("Capture locale") == true -> INVALID_LOCALE_ERROR
        message?.startsWith("Consent version") == true -> CONSENT_REQUIRED_ERROR
        message?.startsWith("Early access consent") == true -> CONSENT_REQUIRED_ERROR
        else -> null
    }

    companion object {
        private const val WAITLIST_NOT_FOUND_ERROR = "waitlist_not_found"
        private const val WAITLIST_CLOSED_ERROR = "waitlist_closed"
        private const val CONSENT_REQUIRED_ERROR = "consent_required"
        private const val INVALID_EMAIL_ERROR = "invalid_email"
        private const val INVALID_SOURCE_ERROR = "invalid_source"
        private const val INVALID_LOCALE_ERROR = "invalid_locale"
        private const val DEFAULT_CONSENT_VERSION = "2026-07-17"
    }
}

data class JoinWaitlistRequest(
    val email: String? = null,
    val source: String? = null,
    val formId: String? = null,
    val locale: String? = null,
    val consent: JoinWaitlistConsentRequest? = null,
    val metadata: Map<String, String>? = null,
)

data class JoinWaitlistConsentRequest(
    val earlyAccess: Boolean? = null,
    val marketing: Boolean? = null,
    val version: String? = null,
)

data class JoinWaitlistResponse(val status: String = "accepted", val message: String = "You're on the waitlist")

data class WaitlistErrorResponse(val error: String)
