# Invitation security fixes — plan

Ruta: Delegated direct (senior-dev + security-engineer). Alcance: 3 confirmed medium/low del audit deep run-1.

## Tareas

- [x] 1. pt.invitation-accept-throttle-tokenkey — throttle key por IP+principal, no por hash del guess. TDD: test que dos guesses distintos comparten bucket. Archivos: InvitationAcceptanceController.kt, InvitationAcceptanceControllerTest.kt. Quitar dependencia invitationTokenCandidateKey si queda sin uso.
- [x] 2. pt.invitation-bearer-query-7d — reducir TTL default 7d a 2d, documentar bearer en query como riesgo, scrub rawToken de payload persistido y logs. Archivos: PlatformAdminBootstrapConfiguration.kt, InvitationEmail.kt, SendInvitationEmailConsumer.kt, tests.
- [x] 3. pt.admin-disable-no-self-guard — rechazar self-disable y self-revoke, exigir confirmación last-operator. Archivos: AdminUserController.kt, UserControlHandlers.kt, tests.

## Evidencia

- Audit: ~/security-audit-skill/profiletailors.com/run-1/artifacts/05_findings/findings.json (PASS ambos validadores)
- Rama: main c29f6b52, worktree limpio salvo plan/
- Verificación por fix: test enfocado + backend-check narrow + Detekt sin nuevos findings
