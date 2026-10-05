# Design: Batch 1 — Remove Dead TooGenericExceptionCaught Suppression

## Technical Approach

Delete-only: remove 1 dead `@Suppress("TooGenericExceptionCaught")` line in `media/application`
(`MediaHandlers.kt:243`). It silences nothing because `config/detekt/detekt.yml:202-215` excludes
`**/application/**` from that rule. `StaleAssetReconciler.kt:96` is intentionally retained as
recorded debt: `server/smp/detekt-baseline.xml:88` embeds the annotation text in the
`LongMethod:processBlob` ID, so its deletion resurfaced LongMethod as a new finding during apply. No
catch-block, signature, or logic edits. No spec behavior changes.

## Architecture Decisions

| Option                                                                                                 | Tradeoff                                                                                             | Decision                                                                                |
|--------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Delete only the `MediaHandlers.kt` annotation; defer `StaleAssetReconciler.kt` and keep files in place | Zero risk, zero behavior change; leaves a minor documentation/maintainability issue as recorded debt | **Chosen** — matches proposal scope, respects `domain <- application <- infrastructure` |
| Move handlers / restructure layers                                                                     | Unrelated churn, violates minimal-scope rule                                                         | Rejected                                                                                |
| Touch `detekt.yml` / baseline manually                                                                 | Out of scope, baseline is a ratchet (shrink only via tool run)                                       | Rejected — never hand-edit baseline                                                     |

## Data Flow

Not applicable — no runtime change. Annotation removal only affects static analysis.

## File Changes

| File                                                                                             | Action                             | Description                                                                                                                                                                                                              |
|--------------------------------------------------------------------------------------------------|------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt:243`       | Modify (delete 1 line)             | Remove `@Suppress("TooGenericExceptionCaught")` above `handle(LegacyUploadAssetCommand)`                                                                                                                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/StaleAssetReconciler.kt:96` | Deferred (retained byte-identical) | Removal resurfaces `LongMethod:processBlob` because `detekt-baseline.xml:88` embeds the annotation text in the ID; any potential removal belongs to a separately scoped future batch with tool-run baseline regeneration |

## Baseline Debt

`detekt-baseline.xml:88` couples the `StaleAssetReconciler.kt:96` annotation text into the
`LongMethod:processBlob` baseline ID. If a future batch chooses to remove the annotation, it MUST
budget a tool-run baseline regeneration (shrink via Detekt run, never hand-edit); removal is not
required by this design. Verified 2026-09-13: deletion → `backend-lint` FAILED with resurfaced
LongMethod; restore byte-identical → PASS.

Non-goals: tenancy x2, UNUSED_PARAMETER, SQL constants, publishing structural suppressions;
`shared/`, `detekt.yml`, `detekt-baseline.xml`, production logic; `package.json`/`pnpm-lock.yaml`
untouched.

## Interfaces / Contracts

None — no new types, APIs, or signatures.

## Testing Strategy

| Layer                | What to Test               | Approach                                                                       |
|----------------------|----------------------------|--------------------------------------------------------------------------------|
| Static               | Zero new detekt findings   | `just backend-lint` PASS; baseline only shrinks or stays equal                 |
| Arch                 | Hexagonal direction intact | `just backend-check` PASS (`HexagonalArchTest`, `ComponentScanArchTest` green) |
| Unit/Integration/E2E | None required              | No behavior change; no BDD scenario for pure annotation deletion               |

Confirmed debt: `server/smp/detekt-baseline.xml:88` embeds the annotation text in the
`LongMethod:processBlob` ID. Deletion resurfaced it as a new finding during apply (`backend-lint`
FAILED); the line was restored byte-identical per the pre-approved fallback (→ PASS). Any potential
removal is deferred to a separately scoped future batch with tool-run baseline regeneration, never a
hand-edit.

## Migration / Rollout

No migration required. Rollback: re-add the 1 deleted line (`git diff` shows exactly 1 line).

## Open Questions

None — scope closed, spec uncontradicted.
