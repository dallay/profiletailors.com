# Proposal: Liquibase Seed Security

## Intent
Prevent development seed accounts and committed password hashes from reaching production Liquibase runs, while preserving local development convenience through runtime-generated credentials.

## Scope
- Separate the production and development changelog entry points.
- Remove committed password credential seed data.
- Add automated regression and CI checks.
- Restore local dev login convenience via profile-gated runtime credential generation.

## Success Criteria
- The production master never includes development seeds.
- The dev profile explicitly selects a dev-only changelog.
- No BCrypt hash or password credential seed exists in committed Liquibase data.
- CI rejects future credential seed material.
- Local development preserves `dev@profiletailors.com` login without versioned hashes.
