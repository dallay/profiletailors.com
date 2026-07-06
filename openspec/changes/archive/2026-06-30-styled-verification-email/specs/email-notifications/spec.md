# Delta for Email Notifications

## MODIFIED Requirements

### Requirement: Email Template System

The system MUST provide email templates for notification content.

The system MUST provide verification email content with a semantically complete plain-text body and a styled HTML body aligned with Profile Tailors design language. The system MUST include the same verification URL in both bodies. The system MUST preserve verification instructions, 24-hour expiry copy, and graceful fallback behavior when template rendering fails.
(Previously: verification email templates allowed optional HTML or multipart output but only guaranteed plain text completeness.)

#### Scenario: Verification email template rendered

- GIVEN a verification email needs to be sent
- WHEN the template is rendered
- THEN the system MUST include a frontend verification URL with token in both text and HTML bodies
- AND both bodies MUST include user instructions and a 24-hour expiration notice
- AND the plain text content MUST be sufficient on its own

#### Scenario: Styled HTML verification template rendered

- GIVEN a verification email needs to be sent
- WHEN the HTML body is rendered
- THEN the system MUST produce conservative inline-styled HTML aligned with Profile Tailors branding
- AND the HTML MUST NOT require external CSS, scripts, or a frontend change

#### Scenario: Template rendering failure handled

- GIVEN a template fails to render
- WHEN the template engine processes the template
- THEN the system MUST catch the rendering exception
- AND the system MUST log the failure
- AND the system MUST use a fallback plain text message
- AND the system MUST still attempt to send the email

#### Scenario: Template variables validated

- GIVEN a template requires variables
- WHEN the template is rendered
- THEN the system MUST verify all required variables are provided
- AND the system MUST reject rendering if variables are missing
- AND the system MUST log missing variable details

### Requirement: Email Sending Adapter Pattern

The system MUST use adapter pattern for email sending infrastructure.

The system MUST define an email sender port in the application layer that accepts subject, recipient, and email content containing required text plus optional HTML. Sending adapters that support HTML MUST deliver both HTML and text when HTML is present. Mock and development adapters MUST expose enough text and HTML content to debug delivery and support assertions. Adapters MUST remain swappable without changing application code.
(Previously: the sender port accepted a single body and did not require adapters to deliver or expose HTML plus text.)

#### Scenario: Email sender port defined

- GIVEN the application needs to send emails
- WHEN the port interface is defined
- THEN the system MUST define `EmailSender` in the application layer
- AND the interface MUST accept email content with required text and optional HTML
- AND the interface MUST return success/failure status

#### Scenario: SMTP adapter implements port

- GIVEN SMTP is configured and HTML content exists
- WHEN the SMTP adapter sends the email
- THEN the system MUST send both text and HTML parts
- AND the adapter MUST remain configurable via properties

#### Scenario: Mock adapter for testing

- GIVEN tests or development need email visibility without SMTP
- WHEN the mock adapter is used
- THEN the system MUST capture or log recipient, subject, text body, and HTML body when present
- AND the mock MUST NOT actually send emails

#### Scenario: Adapter swapping via configuration

- GIVEN different environments need different email providers
- WHEN the configuration changes
- THEN the system MUST swap email sender implementation
- AND application code MUST NOT change
- AND the configured adapter MUST preserve text fallback semantics

### Requirement: Environment-Aware Verification Link Generation

The system MUST generate verification email links from the configured public application URL.

The system MUST separate the public app URL used in emails from backend API base URL concerns. Verification email links MUST use `app.email.public-app-url` plus `/verify-email?token=...` in both plain-text and HTML bodies. This change MUST NOT require new frontend behavior.
(Previously: links used the configured public app URL and frontend route, but the exact route/query contract was not repeated for both text and HTML bodies.)

#### Scenario: Verification link uses configured public app URL

- GIVEN a verification email is generated in an environment
- WHEN the verification link is rendered
- THEN the link MUST start with that environment's configured `app.email.public-app-url`
- AND the path and query MUST be `/verify-email?token=...`

#### Scenario: Verification link avoids hardcoded production API URL

- GIVEN the system runs outside production
- WHEN a verification email is generated
- THEN the link MUST NOT use a hardcoded production API host
- AND the link MUST remain valid for that environment

#### Scenario: Same URL in HTML and text

- GIVEN a verification token and configured public app URL
- WHEN verification email content is rendered
- THEN the text body and HTML body MUST contain the same verification URL
- AND the URL MUST target the existing frontend verification route
