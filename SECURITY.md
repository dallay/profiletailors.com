# Security Policy

## Reporting Security Vulnerabilities

We take security vulnerabilities seriously. If you discover a security issue, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

### How to Report

Email: **<security@profiletailors.com>**

Include the following in your report:

- Description of the vulnerability
- Steps to reproduce the issue
- Potential impact assessment
- Any suggested fixes (optional)

### Response Timeline

We aim to acknowledge reports within **48 hours** and provide a timeline for remediation:

| Severity | Initial Response | Target Resolution |
|----------|------------------|-------------------|
| Critical (CVSS 9-10) | 24 hours | 72 hours |
| High (CVSS 7-8.9) | 48 hours | 7 days |
| Medium (CVSS 4-6.9) | 48 hours | 14 days |
| Low (CVSS 0-3.9) | 5 days | Next release |

### Scope

This policy covers:

- `apps/web/marketing/` — The marketing site and waitlist form
- `server/smp/` — Backend service (when deployed)
- Shared infrastructure and dependencies

Out of scope:

- Social media platforms integrated via API
- Third-party services not operated by Profile Tailors

### Security Updates

- Critical patches are released as soon as possible
- Regular security updates are included in our release cycle
- All releases are documented in the [Changelog](apps/web/marketing/CHANGELOG.md)

### Disclosure Policy

- We follow a **coordinated disclosure** process
- We request that researchers give us reasonable time to address issues before public disclosure
- We will credit reporters in the security advisory (unless you prefer to remain anonymous)

### Supported Versions

| Version | Status |
|---------|--------|
| 0.0.x | Pre-release — development version |

For pre-release software, we recommend using the latest available version.

### Security Training

Contributors must complete security awareness training before contributing to production code. See our [Contributing Guide](CONTRIBUTING.md) for details.

### Attribution

Thank you to the following security researchers who have helped improve our security:

- (Open — submit a report to be added)

---

**Last updated:** May 2026

For general security inquiries: **<security@profiletailors.com>**
