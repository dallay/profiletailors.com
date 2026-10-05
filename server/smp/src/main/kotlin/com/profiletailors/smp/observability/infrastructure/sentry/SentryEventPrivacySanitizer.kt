package com.profiletailors.smp.observability.infrastructure.sentry

import io.sentry.SentryEvent
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import java.net.URI
import java.net.URISyntaxException

internal object SentryEventPrivacySanitizer {
    private const val MAX_EVENT_TEXT_LENGTH = 512
    private const val MAX_TAG_VALUE_LENGTH = 128
    private const val REDACTED_VALUE = "[redacted]"

    private val allowedTagNames = setOf(
        "principalid",
        "workspaceid",
        "publicationid",
        "jobid",
        "invitationid",
    )
    private val safeIdentifier = Regex("^[A-Za-z0-9:_-]{1,$MAX_TAG_VALUE_LENGTH}$")
    private val emailPattern = Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val bearerPattern = Regex("""\bBearer\s+[^\s,;]+""", RegexOption.IGNORE_CASE)
    private val jwtPattern = Regex("""\beyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b""")
    private val credentialValuePattern = Regex(
        """(?ix)
            \b(
                (?:password|passwd|passphrase|access[\s_-]?token|refresh[\s_-]?token|id[\s_-]?token|
                   token|api[\s_-]?key|client[\s_-]?secret|encryption[\s_-]?key|secret|authorization|
                   cookie|set[\s_-]?cookie|pt[\s_-]?refresh|code|oauth[\s_-]?code)
                \s*[:=]\s*
            )["']?[^"'\s,;&]+["']?
        """.trimIndent(),
        RegexOption.IGNORE_CASE,
    )
    private val ipv4Pattern = Regex(
        """\b(?:(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\b""",
    )
    private val ipv4LiteralPattern = Regex(
        """^(?:(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)$""",
    )
    private val ipv6CandidatePattern = Regex("(?<![0-9a-f:])[0-9a-f:]{2,}(?![0-9a-f:])", RegexOption.IGNORE_CASE)
    private val trailingUrlPunctuation = Regex("[),.!?;:]+$")

    fun sanitize(event: SentryEvent): SentryEvent {
        event.getRequest()?.sanitize()
        event.setUser(null)
        event.setBreadcrumbs(null)
        event.getContexts().clear()
        event.setExtras(null)
        event.setUnknown(null)
        event.setServerName(null)
        event.setTags(sanitizeTags(event.getTags()))
        event.setTransaction(event.getTransaction()?.let(::sanitizeText))
        sanitizeMessage(event)
        event.getExceptions().orEmpty().forEach(::sanitizeException)
        return event
    }

    private fun Request.sanitize() {
        setUrl(getUrl()?.let(::sanitizeText))
        setQueryString(null)
        setFragment(null)
        setData(null)
        setCookies(null)
        setHeaders(null)
        setEnvs(null)
        setOthers(null)
        setUnknown(null)
    }

    private fun sanitizeTags(tags: Map<String, String>?): Map<String, String>? = tags.orEmpty()
        .filter { (name, value) ->
            normalize(name) in allowedTagNames &&
                value.length <= MAX_TAG_VALUE_LENGTH &&
                safeIdentifier.matches(value)
        }
        .takeIf { it.isNotEmpty() }

    private fun sanitizeMessage(event: SentryEvent) {
        event.getMessage()?.let { message ->
            message.setFormatted(message.getFormatted()?.let(::sanitizeText))
            message.setMessage(message.getMessage()?.let(::sanitizeText))
            message.setParams(null)
            message.setUnknown(null)
        }
    }

    private fun sanitizeException(exception: SentryException) {
        exception.setType(exception.getType()?.let(::sanitizeText))
        exception.setModule(exception.getModule()?.let(::sanitizeText))
        exception.setValue(exception.getValue()?.let(::sanitizeText))
        exception.setUnknown(null)
        exception.getMechanism()?.let { mechanism ->
            mechanism.setDescription(mechanism.getDescription()?.let(::sanitizeText))
            mechanism.setData(null)
            mechanism.setMeta(null)
        }
    }

    private fun sanitizeText(value: String): String = urlPattern.replace(value) { match -> sanitizeUrl(match.value) }
        .replace(bearerPattern, REDACTED_VALUE)
        .replace(jwtPattern, REDACTED_VALUE)
        .replace(ipv4Pattern, REDACTED_VALUE)
        .replace(ipv6CandidatePattern) { candidate ->
            if (isIpv6Literal(candidate.value)) REDACTED_VALUE else candidate.value
        }
        .replace(credentialValuePattern) { match -> "${match.groupValues[1]}$REDACTED_VALUE" }
        .replace(emailPattern, REDACTED_VALUE)
        .take(MAX_EVENT_TEXT_LENGTH)

    private fun sanitizeUrl(value: String): String {
        val punctuation = trailingUrlPunctuation.find(value)?.value.orEmpty()
        val address = value.removeSuffix(punctuation)

        return try {
            val uri = URI(address)
            if (!uri.isAbsolute) {
                address.substringBefore('?').substringBefore('#') + punctuation
            } else {
                val safeHost = if (uri.host != null && isIpLiteral(uri.host)) "redacted.invalid" else uri.host
                val safePort = if (safeHost == "redacted.invalid") -1 else uri.port
                URI(uri.scheme, null, safeHost, safePort, uri.path, null, null).toASCIIString() + punctuation
            }
        } catch (_: URISyntaxException) {
            address.substringBefore('?').substringBefore('#') + punctuation
        }
    }

    private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

    private fun isIpLiteral(host: String): Boolean =
        ipv4LiteralPattern.matches(host) || host.removePrefix("[").removeSuffix("]").contains(':')

    private fun isIpv6Literal(value: String): Boolean = try {
        URI("http://[${value.substringBefore('%')}]/").host != null
    } catch (_: URISyntaxException) {
        false
    }
}
