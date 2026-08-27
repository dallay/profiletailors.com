# Design: Registration Policy Modes

## Technical Approach

Keep the policy contract in the identity application layer and the mode semantics in a pure
identity domain enum. The infrastructure adapter reads the typed configuration and delegates mode
evaluation. Registration and public-capability use cases consume the same policy contract so the
advertised state and enforced state cannot drift.

## Architecture Decisions

| Decision | Choice and rationale |
|---|---|
| Policy representation | Use one `RegistrationMode` enum with `OPEN`, `INVITE_ONLY`, and `CLOSED`; separate boolean flags cannot represent mutually exclusive state. |
| Evaluation boundary | `RegistrationPolicy` returns a typed `RegistrationDecision`; the registration handler evaluates with no validated invitation context, while a future invitation-aware slice can provide one after server-side validation. |
| Restricted direct registration | `INVITE_ONLY` returns an invitation-required error and `CLOSED` retains the existing registration-disabled error; both paths stop before normalization, persistence, events, or sessions. |
| Public capability | Keep `registrationEnabled` as an allow-listed boolean and set it only when policy evaluation allows public registration; do not expose operational mode unnecessarily. |
| Runtime configuration | Replace `SMP_REGISTRATION_ENABLED` with `SMP_REGISTRATION_MODE` and default to `CLOSED` so missing configuration cannot open signup. |

## Data Flow

```text
SMP_REGISTRATION_MODE -> RegistrationConfigurationProperties -> PropertyBackedRegistrationPolicy
                                                               |
GET /api/capabilities/public -> GetPublicCapabilitiesHandler -> RegistrationPolicy.evaluate(false)
POST /api/auth/register -> RegisterUserHandler -> RegistrationPolicy.evaluate(false)
                                      | ALLOWED -> existing atomic registration flow
                                      | INVITATION_REQUIRED -> safe problem response
                                      | CLOSED -> safe problem response
```

## Affected Files

| Area | Impact |
|---|---|
| `server/smp/.../identity/domain` | Add registration mode and decision semantics. |
| `server/smp/.../identity/application` | Replace the boolean availability port with a policy port and map restricted decisions. |
| `server/smp/.../identity/infrastructure` | Bind the mode and provide the configuration-backed policy adapter. |
| `server/smp/src/test` | Cover mode semantics, handler decisions, configuration, HTTP problem mapping, and BDD behavior. |
| `server/smp/src/main/resources/application.yaml` | Bind `SMP_REGISTRATION_MODE` with a `CLOSED` default. |
| `.env.example`, `infra/apps/smp/{production,swarm}` | Document and pass the mode variable. |
| `openspec/specs/registration/spec.md` | Record the current registration policy contract. |

## Testing Strategy

1. Add failing domain tests for all mode decisions.
2. Add failing application tests for open, invite-only, and closed registration paths.
3. Add failing configuration and Problem Details tests.
4. Add BDD scenarios for invite-only denial and preserved open behavior.
5. Run focused backend tests, BDD fast, backend check, and the relevant configuration validation.

## Rollout and Rollback

The new default is closed. Operators intending to keep public signup enabled must set
`SMP_REGISTRATION_MODE=OPEN`; invite-only operation is `SMP_REGISTRATION_MODE=INVITE_ONLY` once the
invitation-aware registration slice is available. Rollback restores the previous boolean contract
only together with the corresponding code revision; no data migration is required.
