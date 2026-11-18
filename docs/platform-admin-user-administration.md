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
- `POST /api/admin/users/{principalId}/identity-link`

Disable changes the Identity-owned principal state to `DISABLED` and revokes active refresh sessions. Enable changes the state to `ACTIVE`. Session revoke changes no account state and returns the number of sessions revoked. Repeated requests with the same operator, operation, target, and key replay the stored response. Reusing a key for another operation or target returns `409 IDEMPOTENCY_KEY_REUSED`.

### Provision an external identity link

Use this endpoint only after an operator has manually provisioned the target local principal and confirmed the person's Keycloak identity. The submitted `issuer` and `subject` must come from a trusted, validated Keycloak identity; do not infer either value from email, an invitation, or unvalidated request data. Never place tokens, credentials, or session data in the request or an idempotency key.

```http
POST /api/admin/users/{principalId}/identity-link
Accept: application/vnd.api.v1+json
Content-Type: application/json
Idempotency-Key: <unique-operation-key>

{
  "issuer": "https://keycloak.ahome.quest/realms/pt",
  "subject": "<validated-keycloak-subject>"
}
```

The caller must have local `platform.users.manage` permission and a validated `pt-admin` session with `acr=2` and fresh MFA. The request also requires the BFF CSRF token in `X-CSRF-TOKEN`. A successful creation returns `201 Created` with the target `principalId`. The endpoint links only the explicitly supplied pair to the existing principal. It does not create principals, select by email, transfer an existing link, or change `principals.subject` or `principals.provider`. Multiple external links may reference one local principal; each `(issuer, subject)` pair is unique.

Use a new idempotency key for each intended operation. A repeated identical request with the same key may replay the stored response under the existing user-control idempotency behavior. Reusing a key with a changed issuer or subject returns `409`. A pair that is already linked returns the same non-disclosing `409` conflict regardless of its owner. Other responses use the established Problem Details contract: malformed input or issuer mismatch `400`, missing authentication `401`, missing permission or step-up `403`, missing target principal `404`, and pair/idempotency conflict `409`.

The operation records an administrative audit event for authenticated attempts. The event includes the operator and target principal IDs, issuer, subject, action, result, safe reason category, and occurrence time. It does not record credentials, bearer tokens, session identifiers, or raw authentication artifacts. Verify the audit outcome after each operation. Do not retry an uncertain request with a different key until its outcome and existing link have been checked.

All requests use the existing `Accept: application/vnd.api.v1+json` media type and established ProblemDetail responses. Missing authentication is `401`; missing permission is `403`; an unknown user is `404 USER_NOT_FOUND`.

## Authentication behavior

Disabled users cannot log in or refresh. Existing bearer access tokens are not blacklisted and expire normally. Previously revoked refresh sessions remain unusable after enable.

## Audit and metrics

Successful controls emit `USER_DISABLED`, `USER_ENABLED`, or `USER_SESSIONS_REVOKED` with `SUCCEEDED` after the operation completes. Authorized failures emit `FAILED`; rejected control attempts with operator context emit `REJECTED`. Audit metadata is redacted by the existing platform-admin audit repository.

The bounded counter `profiletailors.admin.user_control.requests` uses only `operation` and `outcome` labels. It never labels email addresses, tokens, passwords, user agents, IPs, or principal identifiers.

## Migration and rollback

Liquibase adds `principals.account_state`, defaulting existing and new principals to `ACTIVE`, with a non-null constraint and an `ACTIVE`/`DISABLED` check. It also creates the platform-admin control idempotency store. Roll back UI and routes before removing the state column; retaining the column is safer if deployed state exists.
