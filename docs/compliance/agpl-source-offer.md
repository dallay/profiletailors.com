# AGPL-3.0 Source-Offer Runbook

> **Classification:** Internal — Legal and Compliance
> **Status:** Active
> **Last updated:** 2026-08-31

## Overview

The GNU Affero General Public License v3.0 (AGPL-3.0) Section 13 requires that when the software
is run as a network service, users who interact with it over a network MUST be offered access to
the corresponding source code. This runbook documents how Profile Tailors fulfils that obligation
for every deployment environment.

> **[LEGAL-REVIEW REQUIRED]** The processes described here represent the engineering implementation
> of AGPL-3.0 Section 13. They must be reviewed by qualified legal counsel before commercial
> distribution begins or external investment is accepted.

---

## Section 13 — Obligation Summary

> "If you modify the Program, your modified version must prominently offer all users interacting
> with it remotely through a computer network... an opportunity to receive the Corresponding Source
> of your version."

**What this means for Profile Tailors:**

1. Any user who can reach the deployed application over a network is entitled to the source code.
2. The offer must be **prominent** (not buried in a help page).
3. The source offered must correspond to the **exact version running** — not just `main`.
4. The obligation extends to modifications; a deployment of an unmodified tagged release where the
   public repository remains accessible largely satisfies this, but see the gap analysis below.

---

## Current Compliance Posture

| Requirement                           | Current state                                                                    | Gap                                   |
| ------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------- |
| Source is publicly available          | Yes — `github.com/dallay/profiletailors.com`                                     | None                                  |
| Offer is prominent in UI              | **No** — no footer link yet                                                      | Add "Source" link to footer           |
| Deployed version is identifiable      | **Partial** — release tags exist but no automated SHA injection                  | Automate `DEPLOYED_SHA` env var in CI |
| Source corresponds to running version | **Partial** — public repo + tags are reachable; no per-deployment source archive | Automate source archive on deploy     |

---

## Deployment Tagging Requirements

Every deployment to any environment MUST create a reachable git tag. The tag format is:

```
deploy/<environment>/<ISO-8601-date>-<short-sha>
```

Examples:

```
deploy/production/2026-07-31-a1b2c3d
deploy/staging/2026-07-30-e4f5g6h
```

### CI automation (target state)

The release pipeline MUST:

1. Build the artefact from a tagged commit or a commit whose SHA is recorded.
2. Create the deployment tag and push it to the public repository.
3. Inject the tag or SHA as an environment variable (`DEPLOYED_SHA`, `DEPLOYED_TAG`) into the
   running container or serverless function.
4. Expose `DEPLOYED_SHA` via the `/actuator/info` endpoint (already configured via Spring Boot
   Actuator).

---

## Source Offer in the User Interface

Until the footer link is implemented, the source offer is made implicitly via the public GitHub
repository. Once the footer is implemented, it MUST:

- Link to `https://github.com/dallay/profiletailors.com/tree/<DEPLOYED_TAG>` (or `/commit/<SHA>`
  if a tag is unavailable).
- Be visible on every page of both the marketing site and the dashboard.
- Use text such as "Source code" or "View source" — conspicuous but not disruptive.

**Marketing site component:** `apps/web/marketing/src/components/layout/Footer.astro` (or
equivalent layout file).

**Dashboard component:** The global layout component in `apps/web/app/src/`.

---

## Source Archive Process

For deployments where the public repository is the source offer, no additional archive is required
provided:

1. The deployed commit exists in the public repository (i.e., is not in a private branch or a
   squashed commit with no public ancestor).
2. The tag or SHA referenced in the UI footer is reachable via the public repository.

If a private fork or a build with local patches is ever deployed, a source archive MUST be
generated and hosted at a publicly accessible URL. Use the following procedure:

```bash
git archive --format=tar.gz --prefix=profiletailors-<TAG>/ <TAG> \
  > profiletailors-<TAG>-source.tar.gz
# Upload to a stable, publicly accessible location
# (e.g., GitHub Release asset or an S3 public bucket)
```

---

## Release Checklist

Add the following steps to the release runbook and any CI deploy workflow:

- [ ] The commit being deployed has a reachable tag in the public repository.
- [ ] The deployment tag (`deploy/<env>/<date>-<sha>`) has been pushed.
- [ ] `DEPLOYED_SHA` / `DEPLOYED_TAG` is injected into the running service.
- [ ] The UI footer contains a link to the source at the deployed tag/SHA.
- [ ] The Spring Boot Actuator `/actuator/info` endpoint exposes the build version.

---

## Third-Party Dependency Source Obligations

The Gradle licence report (`just licence-check`) lists all dependency licences. For any dependency
that is AGPL-3.0 or GPL-3.0, that dependency's own source obligations are handled by the
dependency's upstream project; Profile Tailors does not redistribute their source. For any
dependency whose source Profile Tailors bundles (e.g., vendored JS), the corresponding source must
be included in or linked from the licence report.

---

## References

- AGPL-3.0 text: <https://www.gnu.org/licenses/agpl-3.0.html> (Section 13)
- [ADR-0012: AGPL-3.0 Commercial Strategy](../architecture/adr/0012-agpl-commercial-strategy.md)
- [Contributor and Copyright Map](contributor-copyright-map.md)
- [Legal Document Register](legal-document-register.md)
