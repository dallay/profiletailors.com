# Platform Admin Bootstrap

## Overview

The initial platform owner is created through a private, interactive container command. The command
prints one invitation link to the terminal. It does not send the invitation by email. The recipient
sets a password and accepts the current Terms at the admin `/bootstrap` route. The existing email
verification email and `/api/auth/verify-email` endpoint remain unchanged.

The flow creates no workspace, membership, age-eligibility consent, or customer access. Bootstrap
Terms acceptance is recorded as ID-free `PLATFORM` consent. Initial invitation and owner-grant audit
events use the typed `SYSTEM_BOOTSTRAP` actor without a human principal UUID.

## Configuration and prerequisites

Set `SMP_ADMIN_PUBLIC_ORIGIN` to the public admin origin for the installation. For Profile Tailors,
the value is `https://admin.profiletailors.com`. A self-hosted installation must use its own origin.
Include the admin origin in `SMP_CORS_ALLOWED_ORIGINS`. Build the admin SPA with its installation's
`VITE_API_BASE_URL`.

Before using bootstrap, apply the accepted platform-consent, system-actor, and bootstrap-invitation
migrations. The consent and invitation changesets are gated by the explicit
`uuid-native-identifiers` Liquibase context. They require the database to satisfy the approved UUID
cutover preconditions. Do not enable that context during ordinary startup on a database that has not
completed the approved cutover. The default production `prod` context does not apply these gated
changesets.

## Usage

Run the command from the installation's production Compose project:

```bash
docker compose -f infra/apps/smp/production/compose.yaml run --rm --no-deps -it backend platform-admin-bootstrap
```

The command requires an interactive TTY before it opens the isolated database context. It prompts for
the email twice. If a pending invitation exists, type `REPLACE` at the confirmation prompt to replace
it. The command fails closed if a platform owner exists, owner status is unavailable, or the email
belongs to an account.

Copy the printed link directly to the intended recipient. The link contains a one-use token and
expires after 30 minutes. Do not put the link in a ticket, log, shell history, or email. The recipient
opens the link, reviews the current Terms version, creates a password, and accepts the Terms. The
backend sends the existing verification email. The recipient must verify the email before the system
grants `PLATFORM_OWNER`.

## Troubleshooting

- **The command reports that an interactive console is required.** Run it with `-it` and do not pipe
  standard input.
- **The command reports that bootstrap is unavailable.** Check the database migration state and
  active owner state. Do not change the token or database directly.
- **The browser reports a network or CORS error.** Check that `SMP_ADMIN_PUBLIC_ORIGIN`,
  `SMP_CORS_ALLOWED_ORIGINS`, and the admin build's `VITE_API_BASE_URL` match the installation.
- **The invitation link has expired or was used.** Run the private command again and confirm
  replacement if a pending invitation exists.

## References

- [ADR-0031: Explicit platform consent scope](../architecture/adr/0031-explicit-platform-consent-scope.md)
- [ADR-0032: Bootstrap system actor provenance](../architecture/adr/0032-bootstrap-system-actor-provenance.md)
- [ADR-0033: Atomic bootstrap owner grant](../architecture/adr/0033-atomic-bootstrap-owner-grant.md)
- [Initial platform admin bootstrap change](../../.agents/sdd/changes/initial-platform-admin-bootstrap/proposal.md)
