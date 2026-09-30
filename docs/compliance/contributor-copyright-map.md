# Contributor and Copyright Map

> **Classification:** Internal — Legal and Compliance
> **Status:** Evidence inventory; legal ownership not independently verified
> **Last Updated:** 2026-09-30

## Overview

This document inventories non-bot Git author identities visible in the inspected repository history and
CLA Assistant records. Git metadata and CLA records do not by themselves establish each person's
legal identity, employer rights, copyright ownership, or complete grant coverage. Qualified counsel
must validate ownership and grant coverage before any dual-licensing, re-licensing, or sublicensing
decision.

The current project decision is one AGPL-3.0 distribution. It does not rely on exclusive copyright
ownership or promise a proprietary relicensing path.

## Repository Contributor Evidence

The following human-looking author identities appear in the inspected `git log --all`. Similar names
or email addresses are not treated as confirmed aliases without verification. Counts are observations
from local refs, not a complete contribution ledger. The listed identities do not establish the full
set of copyright contributors: merged pull requests may use squash-merge or rebase metadata that
omits patch authors from local commit author fields. Inspect merged pull-request authors and changed
files alongside Git history before relying on this table for any legal conclusion.

| Git author identity | Observed commits | CLA Assistant record | Status |
| --- | ---: | --- | --- |
| `Yuniel Acosta Pérez <33158051+yacosta738@users.noreply.github.com>` | 1,472 | `yacosta738`, GitHub ID `33158051`, signed 2026-05-18 on PR #2 | Identity/grant coverage not legally verified |
| `yacosta738 <33158051+yacosta738@users.noreply.github.com>` | 36 | Same exact email/GitHub ID as above; display-name variation, not a legal identity determination | Identity/grant coverage not legally verified |
| `Yuniel Acosta <acosta@local>` | 9 | No direct identity match established | Verify author identity and applicable grant |
| `Yuniel Acosta <yacosta738@users.noreply.github.com>` | 4 | Possible relation to `yacosta738`; not established from Git metadata | Verify author identity and applicable grant |
| `Yuniel Acosta <acosta@profiletailors.com>` | 1 | No direct identity match established | Verify author identity and applicable grant |
| `Yuniel Acosta Pérez <33158051+yacosta738@users.noreply.github.com>` | 1,643 | `yacosta738`, GitHub ID `33158051`, signed 2026-05-18 on PR #2 | Identity/grant coverage not legally verified |
| `Yuniel Acosta Pérez <yacosta738@users.noreply.github.com>` | 4 | Possible relation to `yacosta738`; not established from Git metadata | Verify author identity and applicable grant |
| `Ryuk Null <286979232+ryuknull@users.noreply.github.com>` | 41 | `ryuknull`, GitHub ID `286979232`, signed 2026-09-15 on PR #1057 | Identity/grant coverage not legally verified |
| `Ryuk Null <nullomv@gmail.com>` | 6 | Possible relation to `ryuknull`; not established from Git metadata | Verify author identity and applicable grant |
| `ryuknull <286979232+ryuknull@users.noreply.github.com>` | 2 | `ryuknull`, GitHub ID `286979232`, signed 2026-09-15 on PR #1057 | Identity/grant coverage not legally verified |

The 1,472-commit author identity is an observation from the inspected history, not a legal title
assertion. The source history also contains automated authors such as Dependabot, Renovate, and
repository bots. Their presence does not independently identify the human author of underlying
changes. Review merged pull requests, patch authorship, generated materials, employer or contractor
agreements, and contributions absent from local Git refs before reaching a legal conclusion.

## CLA Assistant Records

`signatures/cla.json` records three GitHub handles and IDs. The file alone does not prove whether each signature covers every relevant contribution:

| GitHub handle | GitHub ID | Signature date | Pull request |
| --- | ---: | --- | ---: |
| `yacosta738` | `33158051` | 2026-05-18 | #2 |
| `ryuknull` | `286979232` | 2026-09-15 | #1057 |
| `yuniel-acosta` | `163105757` | 2026-09-27 | #1196 |

These records indicate signatures were captured by the configured CLA process. Verify the signed
text, signer identity and authority, grant scope, and contribution coverage before relying on them for
a legal conclusion. Do not assume similarly named Git or GitHub identities are the same person.

## Current Status

| Measure | Observed status |
| --- | --- |
| Non-bot Git author identities | Multiple names and email variants; human identity aliases need reconciliation |
| CLA Assistant records | 3 GitHub identities |
| Legal copyright holders conclusively established | No — legal review required |
| Complete historical contribution coverage established | No — review incomplete |
| Dual-licensing feasibility established | No — not needed for the current AGPL-only decision |

The CLA states that contributors retain ownership and grant Dallay specified permissions, including
sublicensing and distribution under other licences. This inventory does not determine whether the
CLA is enforceable, whether each signatory had authority, or whether all relevant contributions are
covered. No future dual-licensing conclusion is made.

## Copyright Notices

`LICENSE` identifies the repository's AGPL-3.0 licensing terms. Copyright notices in source files
and repository metadata should not be treated as a verified, exhaustive ownership register. Confirm
the correct rights holder and notice treatment with counsel before changing or relying on such notices.

SPDX identifiers may state an applicable licence; they do not establish copyright title.

## How to Keep This Document Current

1. On each merged pull request, review the CLA check and inspect `signatures/cla.json` for new records.
2. Reconcile new human author identities with existing aliases; do not infer that an identity is a
   separate person or legal rights holder from Git metadata alone.
3. Periodically compare repository authors, merged patch authors, CLA records, and relevant employer
   or contractor arrangements.
4. Escalate unresolved coverage or ownership questions to qualified counsel before any change from
   the current AGPL-only distribution.
