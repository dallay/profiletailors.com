# Delta for Lead Capture Common

## Overview

This delta defines framework-free value objects shared across all lead-capture bounded contexts. These VOs MUST NOT contain domain logic, consent, or framework dependencies.

## Changes

### ADDED Requirements

#### Requirement: EmailAddress Value Object

`EmailAddress` MUST validate that the input is a non-blank, RFC-5321-compliant email address. It MUST preserve the original input. It MUST reject blank, null, and structurally invalid emails. It MUST NOT perform provider-specific canonicalization (e.g., Gmail dot/plus normalization).

#### Scenario: Valid email preserved

- GIVEN a user provides `"User.Example@domain.com"`
- WHEN `EmailAddress` is created
- THEN the original value MUST be preserved as-is
- AND the `toString` MUST return the original input

#### Scenario: Blank email rejected

- GIVEN a blank or whitespace-only string
- WHEN `EmailAddress` is created
- THEN it MUST throw a validation exception

#### Scenario: Invalid email rejected

- GIVEN a string without an `@` symbol or with invalid structure
- WHEN `EmailAddress` is created
- THEN it MUST throw a validation exception

#### Requirement: NormalizedEmail Value Object

`NormalizedEmail` MUST derive from `EmailAddress` by trimming and lowercasing the local part and domain. It MUST NOT perform provider-specific canonicalization (no Gmail dot removal, no plus-addressing stripping). It MUST be suitable for use as a deduplication key within a waitlist, not globally.

#### Scenario: Normalization is conservative

- GIVEN `EmailAddress("  User@example.com  ")`
- WHEN `NormalizedEmail` is derived
- THEN the result MUST be `"user@example.com"`
- AND the original `EmailAddress` MUST remain unchanged

#### Scenario: No Gmail canonicalization

- GIVEN `EmailAddress("u.s.e.r+tag@gmail.com")`
- WHEN `NormalizedEmail` is derived
- THEN the result MUST be `"u.s.e.r+tag@gmail.com"` (unchanged after trim+lowercase)

#### Requirement: CaptureSource Value Object

`CaptureSource` MUST identify where the lead was captured (e.g., `"marketing-homepage"`, `"landing-pricing"`). It MUST be a non-blank string. It MUST NOT be confused with a waitlist identifier — source describes origin, not the receiving list.

#### Scenario: Valid source accepted

- GIVEN a non-blank string `"marketing-homepage"`
- WHEN `CaptureSource` is created
- THEN it MUST be accepted

#### Scenario: Blank source rejected

- GIVEN a blank string
- WHEN `CaptureSource` is created
- THEN it MUST throw a validation exception

#### Requirement: CaptureLocale Value Object

`CaptureLocale` MUST represent the user's locale (e.g., `"en"`, `"es"`). It MUST accept only non-blank strings. It MAY be optional in the domain model.

#### Scenario: Valid locale accepted

- GIVEN `"en"`
- WHEN `CaptureLocale` is created
- THEN it MUST be accepted

#### Requirement: LeadMetadata Value Object

`LeadMetadata` MUST accept only whitelisted keys: `utm_source`, `utm_medium`, `utm_campaign`, `utm_content`, `utm_term`, `referrer`, `page_path`, `user_agent_family`, `consent_version`. Unlisted keys MUST be ignored or rejected. It MUST NOT contain PII beyond what the user agent family implies.

#### Scenario: Whitelisted keys accepted

- GIVEN a map with keys `utm_source`, `utm_medium`, `page_path`
- WHEN `LeadMetadata` is created
- THEN all three keys MUST be preserved

#### Scenario: Unlisted keys rejected

- GIVEN a map with keys `utm_source` and `internal_user_id`
- WHEN `LeadMetadata` is created
- THEN `internal_user_id` MUST be ignored or rejected
- AND `utm_source` MUST be preserved

#### Requirement: Framework Isolation

All types in `shared/lead-capture/common` MUST NOT import or depend on `org.springframework.*`, `io.r2dbc.*`, `com.profiletailors.smp.*`, or any server-side framework. This MUST be verified by ArchUnit or equivalent module-boundary tests.

#### Scenario: No Spring imports

- GIVEN the compiled classes of `shared/lead-capture/common`
- WHEN inspected for imports
- THEN no class MUST import any `org.springframework.*` package

#### Scenario: No R2DBC imports

- GIVEN the compiled classes of `shared/lead-capture/common`
- WHEN inspected for imports
- THEN no class MUST import any `io.r2dbc.*` package

#### Scenario: No server package dependency

- GIVEN the compiled classes of `shared/lead-capture/common`
- WHEN inspected for imports
- THEN no class MUST import any `com.profiletailors.smp.*` package
