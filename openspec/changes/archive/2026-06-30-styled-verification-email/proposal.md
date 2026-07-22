# Proposal: Styled Verification Email

## Intent

Restore branded HTML verification emails for Profile Tailors while keeping a complete plain-text
fallback and the existing environment-aware frontend verification URL (`app.email.public-app-url` →
`/verify-email?token=...`).

## Scope

### In Scope

- Add a backend email content contract that supports text plus optional HTML.
- Render verification email as conservative inline-styled HTML aligned with `.agents/DESIGN.md`.
- Preserve plain-text content, 24-hour expiry copy, and configurable public app URL behavior.
- Update Resend, SMTP, and mock sender handling with focused TDD coverage.

### Out of Scope

- Frontend verification flow changes, except references needed by email links.
- New email template dependency or external CSS pipeline.
- Changing token generation, encoding, expiry, resend, or verification API behavior.

## Capabilities

### New Capabilities

None

### Modified Capabilities

- `email-notifications`: Verification templates and sender adapters must support multipart-style
  text + HTML delivery while retaining text-only fallback semantics.

## Approach

Introduce a small application-level email content value type, e.g. `EmailContent(text, html?)`. Make
`EmailTemplates.verificationEmail` return both plain text and inline-styled HTML. Update
`EmailSender` and all implementations so Resend sends both `text` and `html`, SMTP uses MIME
multipart when HTML exists, and mock/dev logging remains useful. Keep URL construction centralized
in `EmailTemplates` and continue using `EmailProperties.publicAppUrl` from
`SendVerificationEmailConsumer`.

## Affected Areas

| Area                                                                                                               | Impact   | Description                                                    |
|--------------------------------------------------------------------------------------------------------------------|----------|----------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/EmailSender.kt`                            | Modified | Port accepts richer email content.                             |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/EmailTemplates.kt`                | Modified | Produces text fallback and styled HTML verification email.     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/*EmailSender.kt`                  | Modified | Resend/SMTP/mock send or expose both content parts.            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/SendVerificationEmailConsumer.kt` | Modified | Sends the new content type without changing URL configuration. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/`                                 | Modified | TDD coverage for template, Resend, and SMTP behavior.          |

## Risks

| Risk                                     | Likelihood | Mitigation                                                                   |
|------------------------------------------|------------|------------------------------------------------------------------------------|
| Email client CSS incompatibility         | Med        | Use table/simple blocks and inline styles only.                              |
| Sender port change ripples through tests | Med        | Update all implementations and test doubles in one focused slice.            |
| SMTP regression moving from text to MIME | Med        | Add focused MIME assertions before implementation.                           |
| URL behavior regresses                   | Low        | Preserve existing URL tests and add HTML/text assertions for custom origins. |

## Rollback Plan

Revert the sender port/content type and sender/template changes, restoring text-only
`EmailSender.sendEmail(to, subject, body)` and `SimpleMailMessage` behavior. Keep existing public
app URL tests as the safety net.

## Dependencies

- Existing Spring Mail/Mime support and Resend SDK capabilities.
- No new dependency planned.

## Success Criteria

- [ ] Verification emails include branded HTML aligned with `.agents/DESIGN.md`.
- [ ] Plain-text fallback remains complete and independently usable.
- [ ] Resend and SMTP send both text and HTML when HTML exists.
- [ ] Existing configurable `/verify-email?token=...` frontend URL behavior remains covered.
- [ ] Implementation follows TDD with focused backend tests first.
