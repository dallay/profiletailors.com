# Tasks: Styled Verification Email

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 250–380 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Deliver text+HTML verification email across every adapter | PR 1 | Single branch; focused backend tests included |

## Phase 1: Template Tests (RED)

- [x] 1.1 Update `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/EmailTemplatesTest.kt` to fail unless text and HTML contain identical `/verify-email?token=...` URLs, instructions, and 24-hour expiry copy.
- [x] 1.2 Add failing cases in `EmailTemplatesTest.kt` for custom `publicAppUrl`, trailing-slash normalization, inline-only branded HTML, and escaped dynamic username/URL values.
- [x] 1.3 Update the existing consumer/config test under `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/` to capture the richer message and prove configured `publicAppUrl` is preserved.

## Phase 2: Adapter Tests (RED)

- [x] 2.1 Update `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/ResendEmailSenderTest.kt` to fail unless the gateway receives both `.text` and `.html`, while preserving result mapping.
- [x] 2.2 Create `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/SmtpEmailSenderTest.kt` with failing assertions for multipart text+HTML MIME content and the existing text-only `SimpleMailMessage` path.
- [x] 2.3 Add focused assertions for `MockEmailSender` in its existing test file (or a new focused test) proving recipient, subject, text, and HTML presence are observable without delivery.

## Phase 3: Contract and Implementation (GREEN)

- [x] 3.1 Modify `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/EmailSender.kt` to add `EmailMessage(text, html?)` and accept it in the sender port.
- [x] 3.2 Modify `EmailTemplates.kt` to return complete text plus escaped, inline-styled HTML while retaining centralized public-app URL normalization.
- [x] 3.3 Update `SendVerificationEmailConsumer.kt` and all affected test doubles to pass `EmailMessage` without altering configuration behavior.
- [x] 3.4 Update `ResendEmailSender.kt` to populate text and optional HTML, and `MockEmailSender.kt` to expose/log both safely.
- [x] 3.5 Update `SmtpEmailSender.kt` to use UTF-8 multipart MIME when HTML exists and preserve `SimpleMailMessage` for text-only content.

## Phase 4: Refactor and Verification

- [x] 4.1 Refactor duplicated escaping/content helpers without changing passing focused tests or introducing dependencies.
- [x] 4.2 Run the focused backend email tests through the repository `just`/Gradle test filter and confirm template, consumer, Resend, SMTP, and mock scenarios pass.
- [x] 4.3 Run `just backend-test-fast`; record any unrelated pre-existing failure separately before proceeding to `sdd-verify`.

## Phase 5: Verification Remediation

- [x] 5.1 Add failing template tests for required-variable validation with logged missing variable details.
- [x] 5.2 Add failing template test for HTML rendering failure fallback that logs the failure and returns sendable plain-text content.
- [x] 5.3 Add focused `MockEmailSender` test proving recipient, subject, text, and HTML content are observable without delivery.
- [x] 5.4 Implement required-variable validation, HTML rendering fallback, and remove stale Resend HTML-future comment.
- [x] 5.5 Re-run focused email verification tests after remediation.
