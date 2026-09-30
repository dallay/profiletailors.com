# AGPL-3.0 Source-Offer Runbook

> **Classification:** Internal — Legal and Compliance
> **Status:** Operational readiness: incomplete; legal review required
> **Last Updated:** 2026-09-30

## Overview

The GNU Affero General Public License v3.0 (AGPL-3.0) Section 13 includes a source-offer condition
for users interacting remotely with a modified version of the program. This runbook records the
engineering controls intended to support compliance; it does not establish that every deployment
currently satisfies the licence or resolve legal interpretation.

> **[LEGAL-REVIEW REQUIRED]** The processes described here represent the engineering implementation
> of AGPL-3.0 Section 13. They must be reviewed by qualified legal counsel before commercial
> distribution begins or external investment is accepted.

---

## Section 13 — Engineering Interpretation

The runbook uses the full text of `LICENSE` as its governing reference. Section 13 describes a
prominent offer of Corresponding Source to users interacting remotely with a modified version of the
Program. This is an operational summary only; counsel must confirm how the provision applies to each
service, modification, and offer mechanism.

Engineering controls should identify the exact version and modifications in deployment and ensure
that the source offer points to the corresponding source. Do not infer that every network user is
covered in every scenario or that publishing the repository's default branch alone satisfies the
licence.

---

## Current Compliance Posture

| Requirement | Evidence currently recorded | Status |
| --- | --- | --- |
| Public corresponding source | Public repository: `github.com/dallay/profiletailors.com` | Repository available; actual correspondence is version-specific |
| Prominent offer to affected remote users | Marketing footer, dashboard sidebar, and admin sidebar now link to build-specific Git commits; automated tests cover all three surfaces | Implemented in source; marketing offer remains visible independently of Terms publication; production deployment evidence and legal sufficiency remain unverified |
| Deployed version identification | Release workflows build from the release tag and inject release metadata as `GIT_SHA` | Build metadata is tied to each frontend release; exact relationship to backend deployment and current live production remains unverified |
| Corresponding source for running version | Commit link is generated from frontend build metadata | Valid only if deployed artifact uses the corresponding public revision and all required source/modifications are present; release evidence and legal review pending |

---

## Deployment Tagging Requirements

For deployments subject to an applicable source-offer obligation, the release process should record
an immutable, publicly reachable source revision. A `deploy` tag is one possible implementation, not
a legal requirement. If adopted, the tag format may be:

```
deploy/<environment>/<ISO-8601-date>-<short-sha>
```

Examples:

```
deploy/production/2026-07-31-a1b2c3d
deploy/staging/2026-07-30-e4f5g6h
```

### CI automation (recommended target)

Deployment automation should:

1. Build the artefact from a source revision whose SHA is recorded.
2. Preserve or publish the corresponding source using a counsel-reviewed method.
3. Inject the source revision into deployment metadata and the operator's release record.
4. Expose the deployed revision through an appropriate operational surface where feasible.

---

## Source Offer in the User Interface

Do not treat repository discoverability as evidence of a prominent offer to each affected remote
user. Until the UI and deployment-specific source mapping are implemented and verified, record this
control as incomplete. Once implemented, the visible source offer MUST:

- Link to a counsel-reviewed public source location for the corresponding deployed revision, such as
  `https://github.com/dallay/profiletailors.com/tree/<DEPLOYED_TAG>` or `/commit/<SHA>`.
- Be visible on every page of both the marketing site and the dashboard.
- Use text such as "Source code" or "View source" — conspicuous but not disruptive.

**Marketing site component:** `apps/web/marketing/src/components/Footer.astro`.

**Dashboard component:** `apps/web/app/src/layouts/AppShell.vue`.

---

## Source Capture Process

The release process should preserve the exact revision and modifications used for each deployed
artefact. A public repository reference may be sufficient only when counsel confirms the source is
complete and corresponds to that deployment, and affected users can access it through the required
offer. Do not assume a default-branch URL, reachable tag, or source archive automatically satisfies
all licence conditions.

If a build includes private patches or generated material needed to form the Corresponding Source,
identify and publish the required source using a counsel-reviewed process. One possible source
snapshot mechanism is:

```bash
git archive --format=tar.gz --prefix=profiletailors-<TAG>/ <TAG> \
  > profiletailors-<TAG>-source.tar.gz
```

---

## Release Checklist

Before marking source-offer readiness complete, deployment automation and the operator checklist
must verify each item below. Current status is incomplete:

- [ ] Record the exact source revision used to build each deployed artefact.
- [ ] Verify corresponding source, including applicable modifications, is publicly reachable.
- [ ] Provide affected remote users a prominent notice linking to the exact corresponding source.
- [ ] Expose the deployed revision in deployment metadata and preserve it in the release record.
- [ ] Verify source access without authentication and retain evidence with the release record.
- [ ] Obtain counsel review of the selected offer method and notice wording.

The existence of a public repository, a release tag, or an Actuator build value alone does not
complete this checklist.

---

## Third-Party Dependency Source Obligations

The Gradle task named `generateLicenseReport` contains a failure hook that searches generated JSON
for configured GPL-2.0 text. That hook is not established as a complete licence-policy control: the
current report includes `GNU GENERAL PUBLIC LICENSE, Version 2 + Classpath Exception` for
`jakarta.annotation:jakarta.annotation-api` and `jakarta.validation:jakarta.validation-api`, while
the configured blocked strings differ in case and wording. The frontend checker likewise uses a
substring-based name list. `just licence-check` is intended to capture frontend and backend inventories for review; neither
inventory nor the existing hook is a legal compatibility conclusion. Review each
dependency's exact licence, exceptions, use and distribution context before adopting a blocking policy.
The report does not by itself fulfil any source obligations that apply to bundled materials.

---

## References

- AGPL-3.0 text: <https://www.gnu.org/licenses/agpl-3.0.html> (Section 13)
- [ADR-0012: AGPL-3.0 Commercial Strategy](../architecture/adr/0012-agpl-commercial-strategy.md)
- [Contributor and Copyright Map](contributor-copyright-map.md)
- [Legal Document Register](legal-document-register.md)
