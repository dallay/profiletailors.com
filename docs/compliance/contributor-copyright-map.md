# Contributor and Copyright Map

> **Classification:** Internal — Legal and Compliance
> **Status:** Active
> **Last updated:** 2026-07-31

## Overview

This document maps every entity that holds copyright in the `profiletailors.com` repository and
records the corresponding CLA status. It is the authoritative reference for evaluating whether
dual-licensing, re-licensing, or sublicensing is legally feasible at any point in time.

**Rule:** Any contributor whose code remains in the repository and who has NOT signed the CLA
blocks dual-licensing. Before any commercial licence is issued, all such contributors must either
sign the CLA or have their contributions removed or rewritten.

---

## Copyright Holders

| GitHub handle | Legal name / entity   | Role               | First commit        | CLA signed | CLA signature date | Notes                             |
|---------------|-----------------------|--------------------|---------------------|------------|--------------------|-----------------------------------|
| `yacosta738`  | Dallay (sole founder) | Author, maintainer | Repository creation | Yes        | 2026-05-18         | Signed via CLA Assistant on PR #2 |

### Notes

- `yacosta738` is the sole contributor at the time of writing.
- Dallay is the legal entity referenced in `CLA.md` as the Project maintainer.
- No employer work-for-hire assignments have been identified; all work appears to be
  independent authorship.

---

## CLA Coverage

| Metric                              | Value |
|-------------------------------------|-------|
| Total contributors with merged code | 1     |
| Contributors with signed CLA        | 1     |
| Coverage                            | 100 % |
| Dual-licensing blocker              | None  |

The CLA grants Dallay a perpetual, worldwide, non-exclusive, royalty-free, irrevocable licence to
reproduce, prepare derivative works, publicly display, publicly perform, sublicense, and distribute
contributions under any licence, including proprietary licences. This is sufficient to support a
future dual-licensing programme without further contributor action for current contributions.

---

## How to Keep This Document Current

1. **On every merged PR:** Check `signatures/cla.json` for new entries.
2. **On any new signatory:** Add a row to the table above with the data from `cla.json`.
3. **Before any commercial licence issuance:** Run the gap check described below.

### Gap Check

Before issuing a commercial (non-AGPL) licence:

1. Compare every Git author in `git log --format='%ae %an' | sort | uniq` against this table.
2. For any author not present: determine whether their contribution remains in the codebase
   (`git log --all --follow -- <file>`).
3. If unreachable contributions exist: the contributor must sign the CLA or the contribution
   must be removed.

---

## Copyright Notices

All source files are licensed under AGPL-3.0. The canonical copyright notice is:

```
Copyright (C) 2024-present Dallay
SPDX-License-Identifier: AGPL-3.0-only
```

SPDX headers are **recommended** in new files per ADR-0012.

---

## References

- [CLA.md](../../CLA.md)
- [signatures/cla.json](../../signatures/cla.json)
- [ADR-0012: AGPL-3.0 Commercial Strategy](../architecture/adr/0012-agpl-commercial-strategy.md)
