# Propuesta: Age Eligibility Enforcement & Child-Data Safeguards

## Intento

DALLAY-492. P0, release-blocker. Profile Tailors necesita mantenerse fuera de regímenes de servicio dirigido a menores. Vamos a enforce que solo personas de 18+ puedan registrarse, con controles residuales documentados para cuentas sospechosas. Esto no es sobre privacidad — es sobre elegibilidad contractual y evitar clasificación como servicio infantil.

## Scope

### In Scope
- Checkbox de confirmación de edad (18+) en el formulario de registro — sin Date of Birth
- Checkbox de aceptación de Términos y Política de Privacidad en registro
- Validación backend: `RegisterUserHandler` rechaza si falta age eligibility o terms acceptance
- Grabación de consentimiento usando `RecordConsentHandler` existente (tipo `CONTRACT_ACCEPTANCE`)
- Versión de política (Terms v1.0.0, Privacy v1.0.0) referenciada en consent records
- `workspaceId` para consent records: usar el workspace recién creado durante `provisionDefaultWorkspace`
- Mensajes de error claros sin dark patterns
- Proceso documentado de residual controls para cuentas bajo sospecha de ser menores

### Out of Scope
- Date of Birth collection o verificación de edad real
- Verificación documental (ID checks, parental consent)
- Diferenciación por mercado / país (se aplica globalmente por ahora)
- Age gating en páginas de marketing (landing page no requiere age gate)
- Dashboard notices, account settings, o flujos post-registro
- Re-verificación periódica de edad

## Capabilities

### New Capabilities
- `age-eligibility`: Age eligibility confirmation durante self-service registration, con consent records y residual controls documentados

### Modified Capabilities
- `legal-pages`: Los términos ahora referencian una `eligibleAge` de 18 años. Requirement `terms-001` ya contempla `eligible age` — confirmar que el spec lo refleje con el valor exacto.
- `identity` (nuevo spec si no existe): El flujo de `RegisterUser` incorpora age eligibility check y consent recording como paso obligatorio

## Approach

**Checkbox-only strategy** — el usuario confirma dos cosas:
1. "Soy mayor de 18 años" (age eligibility)
2. "Acepto los Términos de Servicio y la Política de Privacidad" (terms acceptance)

Backend: `RegisterUserCommand` recibe `acceptedTermsVersion` y `confirmedAgeEligibility: Boolean`. `RegisterUserHandler.validateRegistration()` rechaza si `confirmedAgeEligibility != true` o falta `acceptedTermsVersion`. El consent recording se hace **dentro de la misma transacción**, después de `provisionDefaultWorkspace()` pero antes de publicar el evento `UserRegistered`. Usamos `SubjectReference.workspace(workspaceId)` porque el consent es a nivel del workspace recién creado.

Frontend: `AuthView.vue` añade dos checkboxes en el form de registro. `registerSchema` en `schemas.ts` se extiende con `acceptedTerms` y `confirmedAgeEligibility`. El `RegisterPayload` pasa `acceptedTermsVersion` y `confirmedAgeEligibility` al backend.

Policy versioning: los legal pages specs están en `v1.0.0` (del spec `legal-pages`). La `policyVersion` en el consent record referenciará la versión de términos que el usuario aceptó.

Residual controls: proceso documentado en `docs/compliance/underage-account-procedure.md` para report, investigation, suspension, y appeal.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/identity/application/LocalAuthApi.kt` | Modified | `RegisterUserCommand` nuevos campos |
| `server/smp/identity/application/LocalAuthHandlers.kt` | Modified | `RegisterUserHandler` valida eligibilidad + registra consent |
| `server/smp/identity/http/LocalAuthController.kt` | Modified | `RegisterUserRequest` nuevos campos + validación |
| `apps/web/app/src/shared/lib/validation/schemas.ts` | Modified | `registerSchema` con checkbox fields |
| `apps/web/app/src/modules/auth/infrastructure/auth-api.ts` | Modified | `RegisterPayload` type, `register()` params |
| `apps/web/app/src/modules/auth/presentation/AuthView.vue` | Modified | Checkboxes en template + form logic |
| `apps/web/app/src/modules/auth/infrastructure/auth.store.ts` | Modified | `registerWithPassword` nuevos params |
| `docs/compliance/underage-account-procedure.md` | New | Residual controls documentados |
| `openspec/specs/age-eligibility/spec.md` | New | Spec para esta capability |

## Riesgos

| Riesgo | Probabilidad | Mitigación |
|--------|-------------|------------|
| Usuarios menores mienten en el checkbox | Alta | Esto es aceptado legalmente — estamos cumpliendo con safest harbor. El residual procedure documenta qué hacer si se descubre. |
| `workspaceId` no disponible durante consent recording | Baja | Se genera dentro de `provisionDefaultWorkspace()` en la misma transacción. Solo necesitamos capturar el workspace creado. |
| Frontend manda `confirmedAgeEligibility: false` por manipulación | Baja | Backend valida y rechaza — nunca confiar en el cliente. |

## Rollback Plan

- Frontend: revertir cambios en `AuthView.vue`, `schemas.ts`, `auth-api.ts`, `auth.store.ts` (todo aislado en el form de registro)
- Backend: revertir cambios en `RegisterUserCommand`, `RegisterUserHandler`, `LocalAuthController` — son additive changes que no rompen nada existente
- Consent records: no requieren migración — el `RecordConsentHandler` ya existe y la tabla `consent_records` soporta los datos
- Residual docs: no requieren rollback; son documentación

## Dependencias

- DALLAY-491 (Consent Records) — **merged**, `RecordConsentHandler` disponible
- DALLAY-488 (Legal Pages) — **archived**, Terms v1.0.0 existe
- `legal-pages` spec define `eligibleAge` en terms-001 — confirmar que esté en 18

## Success Criteria

- [ ] Usuario no puede registrarse sin marcar age eligibility checkbox
- [ ] Usuario no puede registrarse sin marcar terms acceptance checkbox
- [ ] `consent_records` tabla tiene un registro `CONTRACT_ACCEPTANCE` por cada registro exitoso
- [ ] `consent_records` usa `workspaceId` del workspace creado en la transacción
- [ ] `consent_records` usa `policyVersion` de los términos aceptados
- [ ] Backend test: registro con checkbox `false` devuelve 422
- [ ] Backend test: registro exitoso crea consent record idempotente
- [ ] Frontend test: form validation bloquea submit sin checkboxes
- [ ] Documentación `docs/compliance/underage-account-procedure.md` existe
