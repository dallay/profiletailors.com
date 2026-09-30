# Spring Security References

This reference bundle belongs to the `spring-boot-security` skill. Use `SKILL.md` as its entry
point and follow `.agents/AGENTS.md`, ADR-0002, and the current Spring Boot infrastructure patterns
for implementation decisions.

## Reference topics

- `token-management.md`: access and refresh token lifecycle.
- `oauth2-integration.md`: external identity providers.
- `authorization-patterns.md`: authorization models and policy checks.
- `security-hardening.md`: application security review.
- `testing-jwt-security.md` and `testing.md`: Kotlin, Kotest, MockK, and reactive HTTP testing.
- `troubleshooting.md`: configuration and runtime diagnosis.
- `migration-spring-security-6x.md`: explicit servlet-to-WebFlux migration context.

For Profile Tailors, the active baseline is Spring Security WebFlux, coroutines, and R2DBC-backed
adapters. Servlet, JPA, JDBC, and Mockito examples are historical or cross-platform reference
material only; they are not implementation defaults. Prefer the reactive and Kotlin examples when
a reference includes both.
