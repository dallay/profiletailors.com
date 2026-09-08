# Delta: DALLAY-568 — Add platform.invitations.create Permission

## ADDED Requirements

### Requirement: platform.invitations.create is a registered platform permission

`platform.invitations.create` is ADDED to the `PlatformPermission` enum and the permission
registry. It is required by `POST /api/admin/invitations/direct`. It is granted to `PLATFORM_OWNER`
and `PLATFORM_OPERATOR` roles only; `SUPPORT_AGENT` and `AUDITOR` do not hold this permission.

| Key | Description |
|-----|-------------|
| `platform.invitations.create` | Create direct invitations (not from waitlist) |

## MODIFIED Requirements

### Requirement: Permission registry (updated)

(Previously: 15 permissions — READ, RESEND, REVOKE for invitations, plus 12 others)

The permission registry now holds 16 permissions. `platform.invitations.create` is added as the
16th entry.

| Key | Description |
|-----|-------------|
| `platform.invitations.create` | Create direct invitations |
| `platform.invitations.read` | Read invitations |
| `platform.invitations.resend` | Resend existing invitations |
| `platform.invitations.revoke` | Revoke active invitations |

### Requirement: Role-permission mapping (updated)

(Previously: OWNER ✓ READ/RESEND/REVOKE; OPERATOR ✓ READ/RESEND/REVOKE)

`platform.invitations.create` is granted to `PLATFORM_OWNER` and `PLATFORM_OPERATOR` only.

| Permission | OWNER | OPERATOR | SUPPORT_AGENT | AUDITOR |
|------------|:-----:|:--------:|:-------------:|:--------:|
| `platform.invitations.create` | ✓ | ✓ | — | — |
| `platform.invitations.read` | ✓ | ✓ | — | — |
| `platform.invitations.resend` | ✓ | ✓ | — | — |
| `platform.invitations.revoke` | ✓ | ✓ | — | — |

## REMOVED Requirements

(None.)
