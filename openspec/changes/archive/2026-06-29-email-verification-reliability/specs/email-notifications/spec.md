# Delta for Email Notifications

## ADDED Requirements

### Requirement: Verification Consumers Are Active at Runtime

The system MUST activate verification-email consumers in every SMP runtime that serves registration
and resend flows.

Runtime bootstrapping MUST subscribe the verification email consumer before user-facing auth traffic
is handled so registration and resend requests do not succeed while verification dispatch is
inactive.

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
