# Contributing to Profile Tailors

Thank you for considering a contribution to **Profile Tailors**! 🎉

This document explains how to contribute effectively and what we expect
from contributors.

---

## Before You Start

### Sign the CLA

All contributors must sign our
[Contributor License Agreement](./CLA.md) before we can accept any
pull request.

Signing is automatic — when you open your first PR, the **CLA
Assistant** bot will comment and ask you to sign. It takes 30 seconds
and you only do it once.

To sign, leave this exact comment on your pull request:

```
I have read the CLA Document and I hereby sign the CLA
```

Your signature is recorded and linked to your GitHub account. From your
second PR onward, the check passes automatically.

**We cannot merge PRs from unsigned contributors.** No exceptions.

---

## Ways to Contribute

- 🐛 **Bug reports** — open an issue with reproduction steps
- 💡 **Feature requests** — open a discussion first before building
- 📖 **Documentation** — fixes, clarifications, translations
- 🔧 **Code** — bug fixes, features approved in discussions

---

## Development Setup

```bash
# Clone the repo
git clone https://github.com/dallay/profiletailors.com.git
cd profiletailors.com

# Marketing site
cd apps/web/marketing
pnpm install
pnpm dev
```

Requirements: Node >= 22.12.0, pnpm.

---

## Pull Request Process

1. **Open an issue or discussion first** for non-trivial changes.
   Don't spend days on a PR that won't be accepted.
2. Fork the repo and create a branch from `main`.
3. Write or update tests for your change when applicable.
4. Make sure the build passes locally before opening the PR.
5. **CLA** — if this is your first contribution to the repository,
   the CLA Assistant will post a comment asking you to sign. Leave this
   exact comment on the PR to complete signing:

   ```
   I have read the CLA Document and I hereby sign the CLA
   ```

   From your second PR onward, this check passes automatically.
6. Fill out the PR template completely.

---

## Commit Convention

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(scope): short description
fix(scope): short description
docs(scope): short description
chore(scope): short description
```

Examples:

```
feat(marketing): add waitlist form validation
fix(nav): correct mobile menu z-index
docs(contributing): clarify CLA process
```

---

## Code Style

- TypeScript strict mode — no `any` unless justified with a comment
- Follow existing file and folder conventions
- Keep components small and focused
- Bilingual content goes in the locale object, never hardcoded inline

---

## License

By contributing, you agree that your contributions will be licensed
under the [GNU Affero General Public License v3.0](./LICENSE).

You also agree to the terms of our [Contributor License Agreement](./CLA.md),
which gives us the ability to offer the Project under additional
licensing terms (including commercial licenses) while you retain full
ownership of your contributions.

---

## Questions?

Open a [GitHub Discussion](https://github.com/dallay/profiletailors.com/discussions)
or email us at **dev@profiletailors.com**.
