# Platform admin user administration

## Scope

The internal admin surface exposes user identity, account state, verification state, registration time, workspace count, memberships, and platform roles. It does not expose email editing, deletion, ownership transfer, impersonation, or manual verification.

## Read API

- `GET /api/admin/users`
- `GET /api/admin/users/{principalId}`
- `GET /api/admin/users/{principalId}/workspaces`

User list search is exact normalized-email matching. The `status` filter accepts `ACTIVE` or `DISABLED` and represents account state. Read endpoints require `platform.users.read`; workspace membership reads additionally require `platform.users.workspaces.read`.

## Control API

The following endpoints require `platform.users.manage` and an `Idempotency-Key` header:

- `POST /api/admin/users/{principalId}/disable`
- `POST /api/admin/users/{principalId}/enable`
- `POST /api/admin/users/{principalId}/sessions/revoke`

Disable changes the Identity-owned principal state to `DISABLED` and revokes active refresh sessions. Enable changes the state to `ACTIVE`. Session revoke changes no account state and returns the number of sessions revoked. Repeated requests with the same operator, operation, target, and key replay the stored response. Reusing a key for another operation or target returns `409 IDEMPOTENCY_KEY_REUSED`.

All requests use the existing `Accept: application/vnd.api.v1+json` media type and established ProblemDetail responses. Missing authentication is `401`; missing permission is `403`; an unknown user is `404 USER_NOT_FOUND`.

## Authentication behavior

Disabled users cannot log in or refresh. Existing bearer access tokens are not blacklisted and expire normally. Previously revoked refresh sessions remain unusable after enable.

## Audit and metrics

Successful controls emit `USER_DISABLED`, `USER_ENABLED`, or `USER_SESSIONS_REVOKED` with `SUCCEEDED` after the operation completes. Authorized failures emit `FAILED`; rejected control attempts with operator context emit `REJECTED`. Audit metadata is redacted by the existing platform-admin audit repository.

The bounded counter `profiletailors.admin.user_control.requests` uses only `operation` and `outcome` labels. It never labels email addresses, tokens, passwords, user agents, IPs, or principal identifiers.

## Migration and rollback

Liquibase adds `principals.account_state`, defaulting existing and new principals to `ACTIVE`, with a non-null constraint and an `ACTIVE`/`DISABLED` check. It also creates the platform-admin control idempotency store. Roll back UI and routes before removing the state column; retaining the column is safer if deployed state exists.
