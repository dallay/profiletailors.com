## Exploration: styled verification email

### Current State

The backend already has environment-aware verification URLs: `IdentityEventConfiguration` binds
`app.email.public-app-url` into `EmailProperties.publicAppUrl`, and `SendVerificationEmailConsumer`
passes that value into `EmailTemplates.verificationEmail`. `EmailTemplates` normalizes trailing
slashes and builds the frontend route `/verify-email?token=...`; existing tests cover custom origins
and ensure the old `/api/auth/verify-email` URL is absent.

The gap is email content and delivery format. `EmailTemplates.verificationEmail` currently returns a
single plain-text `String`. The `EmailSender` port accepts only `body: String`, `ResendEmailSender`
sends only `.text(body)`, and `SmtpEmailSender` uses `SimpleMailMessage.setText(body)`, so there is
no HTML part or multipart/alternative delivery. Existing tests assert plain-text behavior only;
there are no SMTP tests and Resend tests assert `CreateEmailOptions.text` only.

### Affected Areas

-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/EmailTemplates.kt` —
current verification template is plain text; should become the source of both branded HTML and
plain-text fallback while preserving URL construction.

- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/EmailSender.kt` — current
  port cannot express HTML plus plain text; likely needs an email content value type or optional
  HTML field.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/ResendEmailSender.kt` —
currently sends only `.text(body)`; should send text and HTML when available.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/SmtpEmailSender.kt` —
currently uses `SimpleMailMessage`, which is text-only; HTML/multipart requires `MimeMessage`/
`MimeMessageHelper`.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/MockEmailSender.kt` —
should log enough information to verify HTML/text content in dev without breaking fallback behavior.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/SendVerificationEmailConsumer.kt` —
currently receives one string from the template and sends one body; should request/use the new
message representation while preserving `emailProperties.publicAppUrl`.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/EmailTemplatesTest.kt` —
should first gain failing tests for HTML output, design tokens/copy, text fallback, and custom
`publicAppUrl` preservation.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/ResendEmailSenderTest.kt` —
should first gain failing tests proving Resend receives both `text` and `html` content.

- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/email/` — add focused
  SMTP coverage if feasible because no current SMTP sender tests exist.

### Approaches

1. **Introduce a multipart email content value type** — Add a small application-level message/body
   type such as `EmailContent(text: String, html: String? = null)` or `EmailMessage`, have templates
   return it, and update senders to preserve text-only compatibility.
    - Pros: Explicit domain/application contract; supports future styled emails; keeps plain-text
      fallback mandatory; clean tests for both parts.
    - Cons: Touches the port and all implementations/call sites; requires careful compatibility with
      existing tests.
    - Effort: Medium

2. **Add an optional `htmlBody` parameter to `EmailSender.send`** — Change
   `send(to, subject, body, htmlBody = null)` and let `body` remain the text fallback.
    - Pros: Smaller diff; keeps existing call shape mostly intact; straightforward Resend mapping.
    - Cons: Less expressive than a value type; parameter ordering/nullable semantics can become
      unclear as email needs grow.
    - Effort: Low/Medium

3. **Embed HTML in the existing body string** — Return HTML from `EmailTemplates.verificationEmail`
   and send it as the current body.
    - Pros: Minimal code churn.
    - Cons: Breaks plain-text fallback, cannot produce proper multipart emails, weak SMTP/Resend
      behavior, and contradicts the stated need to restore HTML + plain text multipart.
    - Effort: Low

### Recommendation

Use Approach 1 if this change is meant to restore durable behavior from the previous SDD claim:
introduce an explicit multipart email content type, make `EmailTemplates.verificationEmail` return
both `.text` and `.html`, and update Resend/SMTP/Mock implementations to handle the richer content.
Keep URL generation centralized in `EmailTemplates` and continue passing
`emailProperties.publicAppUrl` from `SendVerificationEmailConsumer`; do not reintroduce hardcoded
API URLs.

Implement with TDD in this order: first update/add `EmailTemplatesTest` assertions for branded HTML
and preserved text fallback; then add `ResendEmailSenderTest` coverage for `.text` and `.html`; then
add or update SMTP tests around `MimeMessageHelper` behavior if the test utilities allow it; only
then change production code. The HTML should follow `.agents/DESIGN.md`: monochrome/dark-first,
Space Grotesk/Space Mono fallbacks, restrained borders/surfaces, white primary CTA, clear
expiry/help copy, and accessible fallback link text.

### Risks

- Email client CSS support is limited; inline styles are safer than external CSS or complex
  selectors.
- Changing the `EmailSender` port affects all implementations and test doubles, including
  `IdentityEventConfigurationTest` recording sender.
- SMTP multipart support requires moving from `SimpleMailMessage` to `MimeMessage`; misconfiguration
  could regress SMTP delivery if not tested.
- The verification token is currently interpolated directly into the URL; existing code documents
  caller-side URL encoding, so this change should not silently alter token encoding unless
  separately specified.
- Previous memory claims HTML multipart was done, but current code proves it is absent; rely on
  current source and tests, not memory.

### Ready for Proposal

Yes — the gap is clear: preserve the existing configurable frontend verification URL behavior and
restore styled HTML plus plain-text fallback through an explicit multipart email contract. The
orchestrator should proceed to proposal/spec/design, emphasizing TDD and focused backend tests only,
not broad CI during exploration.
