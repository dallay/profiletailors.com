# Design: Email Verification Reliability

## Technical Approach

Use the existing shared event bus as the single runtime wiring path for verification-email dispatch, then make `/api/auth/me` the SPA’s authoritative source for `emailStatus`. The app shell will derive a single `isEmailVerified` state from the auth store, render a persistent unverified banner, and route resend actions through the existing resend endpoint. Media upload gating will mirror publish/social gating by enforcing verification in media commands before asset creation or binary upload.

## Architecture Decisions

| Decision | Alternatives considered | Rationale |
|---|---|---|
| Import shared `EventConfiguration` into SMP boot/config instead of creating another event publisher bean | Rebuild custom registration in identity; manually invoke consumer from handlers | Keeps `@Subscribe` as the only subscription mechanism and avoids a second event-wiring path. Also prevents drift between registration and resend flows. |
| Keep one `EventPublisher<DomainEvent>` bean and remove ambiguity by reusing `EventEmitter` as the publisher | Add qualifiers/primaries across multiple publisher beans | Lowest-risk fix: wiring fails today because SMP defines `domainEventPublisher()` but never loads the subscriber auto-registration. Reusing the shared emitter preserves existing handler contracts. |
| Extend `CurrentUserProfile` with `emailStatus` and have SPA prefer profile over token status after bootstrap | Keep token-derived status only; add separate status endpoint | `/api/auth/me` already exists as auth-state truth. Adding one field avoids a second round-trip and fixes current drift in `mapProfileToUser()`. |
| Enforce media gating in backend commands/controllers, with UI disabling as secondary guidance | Frontend-only gating | Product rule is authoritative business policy. Backend must deny unverified users even if UI is bypassed. |

## Data Flow

```text
Register/Resend Command
  → EventPublisher<DomainEvent>
  → EventConfiguration subscribes SendVerificationEmailConsumer
  → EmailSender sends verification email

SPA bootstrap/login
  → tokens received
  → GET /api/auth/me
  → auth store user.emailStatus updated from profile
  → AppShell banner + feature guards react

Media upload
  → PUT/POST media endpoint
  → requireEmailVerification(feature=UPLOAD_MEDIA)
  → allow or 403 EMAIL_VERIFICATION_REQUIRED
```

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt` or identity boot config | Modify | Import shared event subscription configuration so `SendVerificationEmailConsumer` is registered at runtime. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityEventConfiguration.kt` | Modify | Remove duplicate publisher strategy if needed so SMP exposes a single authoritative `EventPublisher<DomainEvent>`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/CurrentUserProfile.kt` | Modify | Add `emailStatus`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/GetCurrentUserProfileService.kt` | Modify | Resolve `emailStatus` from `PrincipalIdentityLookup`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/*` | Modify | Apply verification gate to asset creation/upload commands used by both CAS and legacy upload flows. |
| `apps/web/app/src/lib/auth-api.ts` | Modify | Extend `CurrentUserProfile` contract and add resend helper if absent. |
| `apps/web/app/src/stores/auth.ts` | Modify | Replace token-preserving `mapProfileToUser()` logic with profile-authoritative `emailStatus`; expose `isEmailVerified`/resend state. |
| `apps/web/app/src/components/layout/AppShell.vue` | Modify | Render global unverified banner with resend + “check inbox/verified refresh” guidance. |
| `apps/web/app/src/views/MediaLibraryView.vue` and `apps/web/app/src/stores/media.ts` | Modify | Disable upload triggers on unverified accounts and surface backend 403 messaging. |

## Interfaces / Contracts

```ts
interface CurrentUserProfile {
  principalId: string
  email: string | null
  username: string | null
  displayIdentity: string
  emailStatus: 'PENDING' | 'VERIFIED' | 'BOUNCED' | null
}
```

`Resend verification` trigger points:
- App-shell banner primary action for authenticated users with `PENDING` or `BOUNCED`
- Existing registration/post-login unverified states
- Optional retry after a 403 `EMAIL_VERIFICATION_REQUIRED` from publish/connect/upload

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Event subscriber registration, profile mapping, auth-store emailStatus update | Kotlin/JUnit for config + service tests; Vitest for store helpers |
| Integration | Register/resend emits email dispatch; `/api/auth/me` returns `emailStatus`; media endpoints return 403 for unverified | Spring Boot/WebFlux tests with mock email sender and authenticated principals |
| E2E | Banner visibility, resend action, publish/connect/upload blocked until verified | Existing Playwright auth/media/scheduler flows with `EMAIL_VERIFICATION_REQUIRED` assertions |

## Migration / Rollout

No migration required. Roll out behind normal deploy; behavior change is contract-compatible except for the additive `emailStatus` field.

## Open Questions

- [ ] Should `BOUNCED` copy differ from `PENDING` in the banner, or can both reuse one message for this change?
- [ ] Should resend be rate-limited in the UI countdown, or rely only on backend protections for now?
