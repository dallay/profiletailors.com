# Design: Styled Verification Email

## Technical Approach

Add a minimal application-layer email message value object so the existing `EmailSender` port can
carry required plain text plus optional HTML without introducing template dependencies.
`EmailTemplates.verificationEmail` will keep URL construction centralized, continue trimming
`app.email.public-app-url`, and return both the current text fallback and conservative inline-styled
HTML using `.agents/DESIGN.md` tokens.

## Architecture Decisions

| Decision           | Choice                                                                                                                                                                     | Alternatives considered                                                     | Rationale                                                                                                                  |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| Email contract     | Add `EmailMessage(text: String, html: String? = null)` beside `EmailSender` in `identity/application`; change `send(to, subject, message)`                                 | Overload `send`, add separate `html` param, or make infrastructure-only DTO | Keeps hexagonal port expressive, avoids boolean/nullable parameter sprawl, and lets all adapters share fallback semantics. |
| Template ownership | Keep rendering in `EmailTemplates.verificationEmail(...)` and return `EmailMessage`                                                                                        | New templating service or dependency                                        | Current code already centralizes verification URL behavior; no dependency keeps the change small and testable.             |
| SMTP delivery      | Use `JavaMailSender.createMimeMessage()` with `MimeMessageHelper(message, MULTIPART_MODE_MIXED_RELATED, UTF_8)` when HTML exists; retain `SimpleMailMessage` for text-only | Always MIME or keep text-only SMTP                                          | HTML emails need MIME, but preserving text-only path reduces regression risk for non-HTML calls.                           |
| HTML rendering     | Kotlin raw string builder with inline styles, escaped user-facing dynamic text                                                                                             | React Email, Thymeleaf, external CSS                                        | Matches “no new dependency”; inline monochrome tokens survive email client CSS stripping better.                           |

## Data Flow

```text
UserRegistered event
  -> SendVerificationEmailConsumer
  -> EmailTemplates.verificationEmail(username, token, publicAppUrl)
  -> EmailSender port
       ├─ ResendEmailSender: CreateEmailOptions.text + html
       ├─ SmtpEmailSender: MIME text/html when html exists
       └─ MockEmailSender: log text and whether HTML exists
```

## File Changes

| File                                                                                                               | Action | Description                                                                                   |
|--------------------------------------------------------------------------------------------------------------------|--------|-----------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/EmailSender.kt`                            | Modify | Add `EmailMessage`; update port signature.                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/EmailTemplates.kt`                | Modify | Return text + inline HTML verification message; preserve URL normalization/default.           |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/SendVerificationEmailConsumer.kt` | Modify | Pass `EmailMessage` from template into port.                                                  |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/ResendEmailSender.kt`             | Modify | Set `.text(message.text)` and `.html(message.html)` when present.                             |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/SmtpEmailSender.kt`               | Modify | Send MIME multipart for HTML, fallback to `SimpleMailMessage` for text-only.                  |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/MockEmailSender.kt`               | Modify | Log text body and HTML presence/preview safely.                                               |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/EmailTemplatesTest.kt`            | Modify | Assert text fallback, HTML CTA, inline token colors, expiry copy, custom URL, slash trimming. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/ResendEmailSenderTest.kt`         | Modify | Assert Resend receives text and HTML.                                                         |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/SmtpEmailSenderTest.kt`           | Create | Assert MIME HTML path and text-only fallback.                                                 |

## Interfaces / Contracts

```kotlin
data class EmailMessage(
    val text: String,
    val html: String? = null,
)

fun interface EmailSender {
    suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult
}
```

`EmailTemplates.verificationEmail(username, token, publicAppUrl): EmailMessage` remains the only
verification URL builder. HTML dynamic fields (`username`, URL text/attributes) must be escaped
before interpolation.

## Testing Strategy

| Layer        | What to Test                                     | Approach                                                                                      |
|--------------|--------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Unit         | `EmailMessage` template content and URL behavior | Update `EmailTemplatesTest` first; assert text and HTML independently.                        |
| Adapter unit | Resend/SMTP/mock sender contract                 | Fake `ResendEmailGateway`; fake/mock `JavaMailSender`; verify MIME vs text fallback.          |
| Integration  | Event consumer uses configured `publicAppUrl`    | Update existing consumer/config tests or test double `EmailSender` to capture `EmailMessage`. |
| E2E          | Not applicable                                   | Backend-only email composition change; no frontend flow change.                               |

## Migration / Rollout

No data migration required. This is a breaking Kotlin port signature change, so update all
implementations and test doubles in one TDD slice. Operational risk is email rendering differences:
deploy behind existing sender selection, verify Resend sandbox/logs, and retain plain text fallback
for clients that block HTML.

## Open Questions

- [ ] None
