# Email Notifications Specification

## Purpose

Define the email sending infrastructure for the platform. This specification establishes the SMTP
adapter pattern, event consumer for domain events, and email dispatch capabilities to support
asynchronous email delivery.

## Requirements

### Requirement: Email Sending Infrastructure

The system MUST provide email sending capabilities via SMTP adapter.

The system MUST support SMTP configuration via Spring Boot properties.
The system MUST provide a `SmtpEmailSender` adapter within identity infrastructure.
The system MUST support async email dispatch (non-blocking).
The system MUST handle SMTP connection failures gracefully.
The system MUST log email sending attempts and failures.

#### Scenario: Email sent via SMTP adapter

- GIVEN an email needs to be sent (e.g., verification email)
- WHEN the `SmtpEmailSender` is called
- THEN the system MUST connect to configured SMTP server
- AND the system MUST send the email with specified subject and body
- AND the system MUST return success status
- AND the system MUST log the send attempt

#### Scenario: SMTP connection failure handled

- GIVEN the SMTP server is unavailable
- WHEN the `SmtpEmailSender` attempts to send
- THEN the system MUST catch the connection exception
- AND the system MUST log the failure with error details
- AND the system MUST NOT throw exception to caller
- AND the system MUST return failure status

#### Scenario: SMTP configuration missing

- GIVEN SMTP properties are not configured
- WHEN the application starts
- THEN the system MUST use mock email sender for development
- AND the system MUST log emails to console instead of sending
- AND the system MUST NOT fail startup

#### Scenario: Email content validated before sending

- GIVEN an email request is received
- When the email content is validated
- THEN the system MUST verify recipient email format
- AND the system MUST verify subject is not empty
- AND the system MUST verify body is not empty
- AND the system MUST reject invalid content with appropriate error

### Requirement: Domain Event Consumer for Email Notifications

The system MUST consume domain events to trigger email notifications.

The system MUST implement `EventConsumer` interface from `shared/bus`.
The system MUST consume `UserRegistered` domain event.
The system MUST dispatch verification email on user registration.
The system MUST handle event consumption failures gracefully.
The system MUST support retry logic for transient failures.

#### Scenario: UserRegistered event triggers verification email

- GIVEN a `UserRegistered` domain event is published
- When the `SendVerificationEmailConsumer` receives the event
- THEN the consumer MUST extract user email from event
- AND the consumer MUST generate verification token
- AND the consumer MUST send verification email via SMTP
- AND the consumer MUST log the email dispatch

#### Scenario: Event consumer handles email sending failure

- GIVEN a `UserRegistered` event is received
- AND email sending fails (SMTP error)
- When the consumer processes the event
- THEN the consumer MUST log the failure
- AND the consumer MUST NOT throw exception
- AND the consumer SHOULD retry (future implementation)
- AND the system MUST allow manual resend via API

#### Scenario: Event consumer validates event data

- GIVEN a `UserRegistered` event is received
- When the consumer validates event payload
- THEN the consumer MUST verify email field exists
- AND the consumer MUST verify email format is valid
- AND the consumer MUST reject malformed events with logging

#### Scenario: Event consumer is idempotent

- GIVEN the same `UserRegistered` event is received multiple times
- When the consumer processes duplicate events
- THEN the consumer MUST NOT send multiple emails
- AND the consumer MUST use idempotency key (event ID or email+timestamp)
- AND the consumer MUST log duplicate event detection

### Requirement: Verification Consumers Are Active at Runtime

The system MUST activate verification-email consumers in every SMP runtime that serves registration and resend flows.

Runtime bootstrapping MUST subscribe the verification email consumer before user-facing auth traffic is handled so registration and resend requests do not succeed while verification dispatch is inactive.

#### Scenario: Registration runtime has verification consumer active

- GIVEN SMP starts successfully
- WHEN the runtime begins serving authentication traffic
- THEN the verification email consumer MUST already be subscribed
- AND `UserRegistered` events MUST be consumable without extra runtime setup

#### Scenario: Resend uses active consumer path

- GIVEN an unverified user requests resend after SMP startup
- WHEN the resend flow publishes its delivery trigger
- THEN the active runtime MUST consume that trigger
- AND the verification email MUST enter the normal dispatch path

### Requirement: Email Template System

The system MUST provide email templates for notification content.

The system MUST support plain text templates for all verification emails.
The system MAY provide HTML or multipart verification emails, but only if the plain text part remains present and semantically complete.
The system MUST provide verification email template.
The system MUST support template customization via configuration.
The system MUST handle template rendering failures gracefully.
(Previously: templates only guaranteed plain text output and did not define frontend-owned verification links or multipart expectations.)

#### Scenario: Verification email template rendered

- GIVEN a verification email needs to be sent
- WHEN the template is rendered
- THEN the system MUST include a frontend verification link with token
- AND the message MUST include user instructions and a 24-hour expiration notice
- AND the plain text content MUST be sufficient on its own

#### Scenario: Template rendering failure handled

- GIVEN a template fails to render (missing variables, etc.)
- When the template engine processes the template
- THEN the system MUST catch the rendering exception
- AND the system MUST log the failure
- AND the system MUST use fallback plain text message
- AND the system MUST still attempt to send the email

#### Scenario: Template variables validated

- GIVEN a template requires variables (email, token, etc.)
- When the template is rendered
- THEN the system MUST verify all required variables are provided
- AND the system MUST reject rendering if variables are missing
- AND the system MUST log missing variable details

### Requirement: Email Sending Adapter Pattern

The system MUST use adapter pattern for email sending infrastructure.

The system MUST define email sender port in application layer.
The system MUST implement SMTP adapter in infrastructure layer.
The system MUST support multiple email sender implementations (SMTP, mock, etc.).
The system MUST allow swapping email providers without changing application code.

#### Scenario: Email sender port defined

- GIVEN the application needs to send emails
- When the port interface is defined
- THEN the system MUST define `EmailSender` interface in application layer
- AND the interface MUST have `sendEmail(to, subject, body)` method
- AND the interface MUST return success/failure status

#### Scenario: SMTP adapter implements port

- GIVEN the SMTP adapter is implemented
- When the adapter is registered
- THEN the system MUST implement `EmailSender` interface
- AND the adapter MUST be in infrastructure layer
- AND the adapter MUST be configurable via properties

#### Scenario: Mock adapter for testing

- GIVEN tests need to verify email sending without SMTP
- When the mock adapter is used
- THEN the system MUST implement `EmailSender` interface
- AND the mock MUST capture sent emails for assertions
- AND the mock MUST NOT actually send emails

#### Scenario: Adapter swapping via configuration

- GIVEN different environments need different email providers
- When the configuration changes
- THEN the system MUST swap email sender implementation
- AND the application code MUST NOT change
- AND the system MUST use the configured adapter

### Requirement: Email Dispatch Asynchronicity

The system MUST dispatch emails asynchronously to avoid blocking registration flow.

The system MUST use Spring event publishing for async dispatch.
The system MUST NOT block the registration handler on email sending.
The system MUST handle async processing failures gracefully.
The system MUST provide visibility into email dispatch status.

#### Scenario: Registration completes without waiting for email

- GIVEN a user registers successfully
- When the registration handler completes
- THEN the system MUST return 201 response immediately
- AND the system MUST publish `UserRegistered` event
- AND the email sending MUST happen asynchronously
- AND the user MUST NOT wait for email delivery

#### Scenario: Async email failure does not affect registration

- GIVEN a registration completes and email event is published
- AND email sending fails asynchronously
- When the failure occurs
- THEN the system MUST log the failure
- AND the system MUST NOT affect the user's registration status
- AND the user MUST be able to resend verification via API

#### Scenario: Email dispatch status trackable

- GIVEN an email is dispatched asynchronously
- When the dispatch is processed
- THEN the system MUST log dispatch attempt with timestamp
- AND the system MUST log success or failure with details
- AND the system SHOULD provide metrics for monitoring (future)

### Requirement: Email Notification Error Handling

The system MUST handle email notification errors gracefully.

The system MUST not fail registration due to email sending issues.
The system MUST provide manual resend capability via API.
The system MUST log all email sending attempts and failures.
The system MUST support debugging email delivery issues.

#### Scenario: Email failure logged with context

- GIVEN an email sending attempt fails
- When the failure is processed
- THEN the system MUST log error with timestamp
- AND the system MUST log recipient email (masked for privacy)
- AND the system MUST log error type and message
- AND the system MUST log SMTP server response if available

#### Scenario: Manual resend available after failure

- GIVEN an email fails to send during registration
- When the user requests resend
- THEN the system MUST generate new verification token
- AND the system MUST attempt to send new email
- AND the system MUST return appropriate response

#### Scenario: Email delivery not guaranteed

- GIVEN an email is sent successfully via SMTP
- When the email is delivered
- THEN the system MUST NOT guarantee email reaches inbox
- AND the system MUST rely on SMTP server for delivery
- AND the user MUST contact support if email not received

### Requirement: Email Notification Configuration

The system MUST support configurable email notification settings.

The system MUST support SMTP server configuration via properties.
The system MUST support email sender address configuration.
The system MUST support email subject prefix configuration.
The system MUST support development mode with mock sender.

#### Scenario: SMTP configuration via properties

- GIVEN the application needs SMTP settings
- When the configuration is loaded
- THEN the system MUST read `spring.mail.host` property
- AND the system MUST read `spring.mail.port` property
- AND the system MUST read `spring.mail.username` property (optional)
- AND the system MUST read `spring.mail.password` property (optional)

#### Scenario: Development mode uses mock sender

- GIVEN the application runs in development profile
- When email sending is attempted
- THEN the system MUST use `MockEmailSender` instead of SMTP
- AND the system MUST log emails to console
- AND the system MUST NOT require SMTP server

#### Scenario: Email sender address configurable

- GIVEN the system needs to set sender email address
- When the configuration is loaded
- THEN the system MUST read `app.email.sender` property
- AND the system MUST use configured address as sender
- AND the system MUST fallback to default if not configured

### Requirement: Environment-Aware Verification Link Generation

The system MUST generate verification email links from a configurable public application URL.

The system MUST separate the public app URL used in emails from backend API base URL concerns.

#### Scenario: Verification link uses configured public app URL

- GIVEN a verification email is generated in an environment
- WHEN the verification link is rendered
- THEN the link MUST start with that environment's configured public app URL
- AND the path MUST target the frontend verification route

#### Scenario: Verification link avoids hardcoded production API URL

- GIVEN the system runs outside production
- WHEN a verification email is generated
- THEN the link MUST NOT use a hardcoded production API host
- AND the link MUST remain valid for that environment
