# Scheduler URL-state standard

**Status:** Accepted
**Date:** 2026-07-10
**Deciders:** Frontend Platform

## Overview

The scheduler now treats the URL as the single durable source of truth for calendar surface,
visible date, filters, and post-detail modal selection. This standard exists so refresh, browser
history, shared links, and direct navigation all restore the same scheduler state without relying
on transient in-memory UI flags.

The canonical scheduler route family is:

- `/scheduler/calendar/week`
- `/scheduler/calendar/month`
- `/scheduler/list`

The legacy `/scheduler/calendar/day` route is compatibility-only input. It must be normalized to the
canonical week route on first render.

## Changes

The scheduler URL contract now owns these state fields:

| Concern       | Canonical form                                      | Notes                                                                                  |
|---------------|-----------------------------------------------------|----------------------------------------------------------------------------------------|
| Surface       | path (`/calendar/week`, `/calendar/month`, `/list`) | Day is not a durable surface.                                                          |
| Focused date  | `date=YYYY-MM-DD`                                   | Omit only when canonical logic determines the default visible date.                    |
| Timezone      | `timezone=Area/City`                                | Replace history on change.                                                             |
| Status filter | `status=all\|queued\|published\|cancelled`          | Omit `all` from the canonical query.                                                   |
| Search        | `q=trimmed text`                                    | Trim whitespace before writing.                                                        |
| Channels      | repeated `channels[]`                               | Read legacy `channels`, always emit `channels[]`.                                      |
| Post detail   | `postId=<publication-id>`                           | Present only while the selected publication is valid in the current scheduler context. |

History semantics are intentionally split:

- **Push** for user navigation milestones: surface changes, date changes, and opening post detail.
- **Replace** for refinements and cleanup: timezone changes, status/search/channel filter updates,
  canonical normalization, and stale `postId` removal.

This means the Back button should traverse meaningful scheduler states instead of every small filter
cleanup.

## Usage

### Canonical route examples

```text
/scheduler/calendar/week?date=2026-06-20
/scheduler/calendar/month?date=2026-06-20&channels[]=sa-linkedin-001
/scheduler/list?status=queued&postId=post-42
```

### Compatibility rules

If the app receives either of these inputs:

```text
/scheduler
/scheduler/calendar/day?date=2026-06-20&channels=sa-linkedin-001
```

it should normalize them to canonical scheduler URLs such as:

```text
/scheduler/calendar/week
/scheduler/calendar/week?date=2026-06-20&channels[]=sa-linkedin-001
```

### Modal deep links

Opening a post from the scheduler should push `postId` into the URL. A refresh or copied URL should
re-open the modal once scheduler data resolves the matching publication.

When a new scheduler context proves the selected post is no longer valid, the app must:

1. close the modal,
2. remove `postId`, and
3. use **replace** semantics so stale selections do not pollute browser history.

## Troubleshooting

### Refresh lands on login instead of restoring scheduler state

The scheduler route is protected. In backend-free or HAR-backed E2E runs, the auth refresh mock must
remain active across reloads or direct protected-route navigation. If the refresh layer drops, the
router guard will redirect to `/login` before scheduler UI can hydrate.

### Shared URLs emit `channels` instead of `channels[]`

Reading legacy `channels` is supported only for compatibility. Any new navigation or
canonicalization
should rewrite the query to repeated `channels[]` keys.

### Back/forward does not restore a just-closed modal

Modal restoration depends on the close flow preserving a prior `postId` history entry. If the close
path uses replace semantics, Back will return to the previous non-modal scheduler state instead of
reopening detail.

### A post-detail link opens but then closes immediately

This usually means the current date/filter/timezone/channel context does not include that
publication. The scheduler should only keep `postId` while the active fetch confirms the selected
publication is still visible.

## References

- `docs/README.md`
- `apps/web/app/src/composables/useCalendarUrl.ts`
- `apps/web/app/src/views/SchedulerView.vue`
- `apps/web/app/src/router/index.ts`
- `apps/web/app/e2e/specs/scheduler-url-addressable.spec.ts`
- `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts`
