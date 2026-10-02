# Security Policy

## Scope

This policy covers:

- `apps/web/marketing/` — Astro marketing site and waitlist
- `apps/web/app/` — Vue dashboard SPA
- `apps/web/admin/` — Vue platform-admin SPA
- `server/smp/` — Spring Boot backend
- `shared/` — Shared Kotlin modules
- Infrastructure configurations under `infra/`

Out of scope: third-party social media platforms and external services not operated by Profile Tailors.

## Reporting Security Vulnerabilities

**Do not open a public GitHub issue for security vulnerabilities.**

Email: **[security@profiletailors.com](mailto:security@profiletailors.com)**

Include:

- Description of the vulnerability
- Steps to reproduce
- Potential impact assessment
- Suggested fix (optional)

### Response Timeline

| Severity         | Initial Response | Target Resolution |
|-----------------|-----------------|-------------------|
| Critical (CVSS 9–10) | 24 hours   | 72 hours          |
| High (CVSS 7–8.9)     | 48 hours   | 7 days            |
| Medium (CVSS 4–6.9)   | 48 hours   | 14 days           |
| Low (CVSS 0–3.9)      | 5 days     | Next release      |

## Security Measures

### Automated Scanning

The repository uses a layered DevSecOps scanning model:

| Scanner  | Coverage                              | Merge Gate |
|----------|--------------------------------------|------------|
| Gitleaks | Secrets detection, entire repository  | Yes        |
| Semgrep  | Backend and frontend SAST             | Yes        |
| CodeQL   | Backend and frontend code analysis   | Yes        |
| Trivy    | Dependencies and IaC (HIGH/CRITICAL) | Yes        |
| Biome    | Frontend JS/TS/Vue security lint    | Yes        |
| Detekt   | Kotlin static analysis              | Yes        |

See [docs/security/scanning-stack.md](docs/security/scanning-stack.md) for the full scanner responsibility map and workflow details.

### Branch Protection

- Direct pushes to `main` are blocked
- All changes require a pull request with passing CI checks
- Branch status: **protected**

### Vulnerability Management

- Dependabot is enabled for dependency vulnerability alerts
- Secret scanning is enabled with push protection
- Private vulnerability reporting is enabled

## Security Updates

Critical patches are released as soon as possible. Regular updates follow the release cycle. All releases are documented in the [Changelog](apps/web/marketing/CHANGELOG.md).

## Disclosure Policy

We follow coordinated disclosure. Request reasonable time to address issues before public disclosure. Reporters are credited in security advisories unless anonymity is preferred.

## Supported Versions

| Version | Status                          |
|---------|--------------------------------|
| Current | Actively maintained             |
| 0.0.x   | Pre-release — use latest build  |

## Contributing Secure Code

Contributors must follow secure coding practices:

- Never commit secrets or credentials
- Use environment variables for sensitive configuration
- Follow input validation and output encoding guidelines
- Run local security checks before submitting PRs:

```bash
# Frontend security lint
cd apps/web && pnpm dlx @biomejs/biome ci .

# Secret detection
gitleaks dir . --config .gitleaks.toml
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## Attribution

Thank you to the security researchers who help improve Profile Tailors.

---

**Security inquiries:** [security@profiletailors.com](mailto:security@profiletailors.com)
