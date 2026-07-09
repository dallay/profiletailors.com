# Production Secrets Reference

**Last Updated:** 2026-07-09  
**Status:** Active

## Overview

This document catalogs all secrets required to run Profile Tailors in production environments.
It defines what each secret is, how to generate it, rotation procedures, and access control
guidelines.

**Platform-agnostic:** This guide does not prescribe where secrets are stored (GitHub Secrets,
Vercel, Cloudflare Workers, Dokploy, Kubernetes Secrets, HashiCorp Vault, etc.). Deployment
guides for specific platforms should reference this document as the canonical list.

## Security principles

1. **Never commit secrets to git** — `.env` is gitignored.
2. **Use strong, randomly generated values** — avoid default or example values.
3. **Rotate secrets periodically** — especially after team changes or suspected exposure.
4. **Limit access by role** — only infrastructure admins and deployment automation should have
   production secret access.
5. **Encrypt secrets at rest** — use your platform's native secret management (encrypted by default).

## Secret categories

Secrets are grouped by risk level and rotation frequency:

| Category | Risk | Rotation frequency |
|----------|------|-------------------|
| Database credentials | **CRITICAL** | Per security incident, team change, or annually |
| OAuth client secrets | **HIGH** | Per security incident or when OAuth app is regenerated |
| Encryption keys | **CRITICAL** | Per security incident, or when cipher is upgraded |
| JWT signing secrets | **HIGH** | Per security incident or quarterly |
| Third-party API keys | **MEDIUM** | Per security incident or when key is compromised |

## Required secrets

### Database

#### `SMP_DB_USERNAME`

- **Type:** String
- **Description:** PostgreSQL username for runtime database connections (R2DBC + Liquibase).
- **Risk:** CRITICAL
- **Generation:** Platform-specific (managed Postgres, self-hosted user creation).
- **Rotation:** Create new user, grant same permissions, update secret, verify, revoke old user.
- **Access:** Infrastructure admins, deployment automation.

#### `SMP_DB_PASSWORD`

- **Type:** String (min 32 characters recommended)
- **Description:** PostgreSQL password for `SMP_DB_USERNAME`.
- **Risk:** CRITICAL
- **Generation:**
  ```bash
  openssl rand -base64 32
  ```
- **Rotation:** Update password in Postgres, update secret, restart app, verify connectivity.
- **Access:** Infrastructure admins, deployment automation.

#### `SMP_POSTGRES_PASSWORD`

- **Type:** String
- **Description:** Alias for `SMP_DB_PASSWORD`, used by Docker Compose compatibility layer.
- **Risk:** CRITICAL
- **Generation:** Same as `SMP_DB_PASSWORD`.
- **Rotation:** Must match `SMP_DB_PASSWORD` rotation.
- **Access:** Infrastructure admins, deployment automation.

### Authentication & Authorization

#### `SMP_LOCAL_JWT_SECRET`

- **Type:** Base64-encoded string (≥256 bits / 32 bytes)
- **Description:** HS256 signing key for local JWT tokens (dev/staging, no external IdP).
- **Risk:** HIGH
- **Generation:**
  ```bash
  openssl rand -base64 32
  ```
- **Rotation:** Generate new key, update secret, restart app. Old tokens become invalid immediately.
  Coordinate with users if long-lived sessions exist.
- **Access:** Infrastructure admins, deployment automation.
- **Production note:** Leave empty in production if using external OAuth/OIDC provider. If used,
  rotate quarterly or after security incident.

#### `SMP_LOCAL_JWT_DEV_FALLBACK`

- **Type:** Base64-encoded string (≥256 bits / 32 bytes)
- **Description:** Fallback JWT signing key when `SMP_LOCAL_JWT_SECRET` is blank (dev-only).
- **Risk:** HIGH (if used in production)
- **Generation:**
  ```bash
  openssl rand -base64 32
  ```
- **Rotation:** Same as `SMP_LOCAL_JWT_SECRET`.
- **Access:** Infrastructure admins, deployment automation.
- **Production note:** **MUST be empty in production** to force explicit `SMP_LOCAL_JWT_SECRET`
  configuration. Application will refuse to start if both are blank.

### LinkedIn Integration (OAuth)

#### `SMP_LINKEDIN_CLIENT_ID`

- **Type:** String (LinkedIn App ID)
- **Description:** OAuth 2.0 client ID from LinkedIn Developer Portal.
- **Risk:** MEDIUM (public in authorization flow)
- **Generation:** Obtained from [LinkedIn Developers](https://www.linkedin.com/developers/).
- **Rotation:** Regenerate client credentials in LinkedIn app settings, update both ID and secret,
  restart app. Existing user connections may need re-authorization.
- **Access:** Infrastructure admins, deployment automation.

#### `SMP_LINKEDIN_CLIENT_SECRET`

- **Type:** String (LinkedIn App Secret)
- **Description:** OAuth 2.0 client secret from LinkedIn Developer Portal.
- **Risk:** HIGH
- **Generation:** Obtained from [LinkedIn Developers](https://www.linkedin.com/developers/).
- **Rotation:** Regenerate in LinkedIn app settings (invalidates old secret immediately), update
  secret, restart app. Existing refresh tokens may become invalid.
- **Access:** Infrastructure admins, deployment automation.

### Credential Encryption

#### `PUBLISHING_CREDENTIALS_KEY`

- **Type:** Base64-encoded 32-byte key (AES-256)
- **Description:** Master encryption key for OAuth access/refresh tokens stored in database.
  Used by `EncryptedCredentialsService` to encrypt/decrypt LinkedIn OAuth tokens at rest.
- **Risk:** **CRITICAL** — compromise exposes all user OAuth tokens.
- **Generation:**
  ```bash
  openssl rand -base64 32
  ```
- **Rotation:**
  1. **High-impact operation** — requires re-encryption of all stored credentials.
  2. Generate new key.
  3. Deploy migration that decrypts with old key, re-encrypts with new key.
  4. Update secret.
  5. Restart app.
  6. Verify all users can publish (tokens are decryptable).
  7. **Fallback:** If rotation fails, users must reconnect LinkedIn accounts.
- **Access:** Infrastructure admins only. Not readable by developers or support.
- **Related issue:** [#176 - PUBLISHING_CREDENTIALS_KEY has no validation](https://github.com/dallay/profiletailors.com/issues/176)

### CORS & Networking

#### `SMP_CORS_ALLOWED_ORIGINS`

- **Type:** Comma-separated URLs
- **Description:** Allowed origins for CORS preflight requests.
- **Risk:** LOW (misconfiguration risk, not a secret)
- **Example:** `https://app.profiletailors.com,https://staging.profiletailors.com`
- **Generation:** Define based on deployed frontend URLs.
- **Rotation:** Update when frontend domains change, restart app.
- **Access:** Infrastructure admins, deployment automation.

### Optional / Environment-specific

#### `SMP_POSTGRES_TEST_PASSWORD`

- **Type:** String
- **Description:** Password for Testcontainers-backed integration tests (CI only).
- **Risk:** LOW (ephemeral container, not production data)
- **Generation:**
  ```bash
  openssl rand -base64 16
  ```
- **Rotation:** No rotation required (ephemeral). Change if CI environment is compromised.
- **Access:** CI/CD pipeline only.

#### `GRAFANA_ADMIN_PASSWORD`

- **Type:** String
- **Description:** Grafana admin password (local monitoring only, not production).
- **Risk:** MEDIUM (local dev/staging only)
- **Generation:**
  ```bash
  openssl rand -base64 16
  ```
- **Rotation:** Change if local Grafana is exposed or compromised.
- **Access:** Infrastructure admins, local developers (dev/staging only).

#### `AHREFS_ANALYTICS_KEY`

- **Type:** String (public client key)
- **Description:** Ahrefs Analytics client-side tracking key for marketing site.
- **Risk:** LOW (public, client-side)
- **Generation:** Obtained from Ahrefs dashboard.
- **Rotation:** Regenerate in Ahrefs if key is abused or rate-limited.
- **Access:** Frontend developers, deployment automation.

## Secret validation checklist

Before deploying to production, verify:

- [ ] All `CHANGE_ME_gK2fcFZg5cgVu9U` placeholders replaced with real values.
- [ ] No default/example credentials in use.
- [ ] `SMP_LOCAL_JWT_DEV_FALLBACK` is **empty** (production must use explicit `SMP_LOCAL_JWT_SECRET`).
- [ ] `PUBLISHING_CREDENTIALS_KEY` is exactly 32 bytes (Base64-encoded, 44 chars).
- [ ] `SMP_DB_PASSWORD` is strong (≥32 chars, randomly generated).
- [ ] `SMP_LINKEDIN_CLIENT_SECRET` matches active LinkedIn app configuration.
- [ ] `SMP_CORS_ALLOWED_ORIGINS` includes only production frontend URLs.

## Access control guidelines

| Secret | Who should access | When |
|--------|------------------|------|
| `SMP_DB_PASSWORD` | Infrastructure admins | Deployment, incident response |
| `PUBLISHING_CREDENTIALS_KEY` | Infrastructure admins only | Deployment, key rotation |
| `SMP_LINKEDIN_CLIENT_SECRET` | Infrastructure admins | Deployment, OAuth app changes |
| `SMP_LOCAL_JWT_SECRET` | Infrastructure admins | Deployment, security incident |
| `GRAFANA_ADMIN_PASSWORD` | Developers (dev/staging) | Local monitoring setup |
| `AHREFS_ANALYTICS_KEY` | Frontend developers | Marketing site deployment |

**Principle of least privilege:** Developers should access secrets through platform-provided CLI
tools (e.g., `vercel env pull`, `wrangler secret list`) scoped to their environment (dev/staging).
Production secrets should only be accessible to infrastructure admins and deployment automation.

## Incident response

If a secret is compromised or suspected to be compromised:

1. **Immediately rotate the compromised secret** (see rotation procedures above).
2. **Audit access logs** (platform-specific: GitHub audit log, Cloudflare audit log, etc.).
3. **Notify affected users** if user data or OAuth tokens are at risk.
4. **Document the incident** in a postmortem (template: `docs/postmortems/YYYY-MM-DD-secret-leak.md`).
5. **Review access control policies** to prevent future exposure.

## Deployment integration

Each deployment platform should document how to provision these secrets:

- **Vercel:** `vercel env add <SECRET_NAME> production`
- **Cloudflare Workers:** `wrangler secret put <SECRET_NAME>`
- **Dokploy / Docker Compose:** Environment variables in `.env` or Docker secrets
- **Kubernetes:** `kubectl create secret generic profiletailors-secrets --from-literal=...`
- **GitHub Actions:** Repository secrets (`Settings > Secrets > Actions`)

Platform-specific guides should link back to this document for the canonical secret list.

## References

- [Getting Started Guide](./getting-started.md) — local `.env` setup
- [Issue #176 - PUBLISHING_CREDENTIALS_KEY validation](https://github.com/dallay/profiletailors.com/issues/176)
- [Issue #177 - Audit Liquibase seed migrations for hardcoded credential hashes](https://github.com/dallay/profiletailors.com/issues/177)
- [Actuator Security](./monitoring/actuator-security.md) — production monitoring posture
