# Cierre QA de Back Office

## Ruta

Delegated direct mediante RPI. Objetivo: preparar cobertura QA ejecutable y evidencia auditable para cerrar GitHub #656 / Linear DALLAY-560 sin confundir el cierre de las issues hijas con la prueba de la journey operativa completa.

## Criterios de aceptación

- [ ] El plan cubre la journey desde waitlist hasta invitación, estado de delivery, aceptación, activación, membresía del workspace y primer login.
- [ ] Se cubren los caminos negativos y operativos: expiración, reenvío, revocación, idempotencia, email duplicado, fallo de delivery, auditoría y límites de permisos.
- [x] Se reutiliza la cobertura existente de Cucumber, Vitest y Playwright donde ya es autoridad; no se añade cobertura sintética duplicada sin demostrar un gap.
- [x] El comportamiento frontend crítico tiene cobertura E2E ejecutable en el lane mockeado o un prerrequisito de entorno explícito.
- [x] El comportamiento backend observable externamente ya tiene escenarios Cucumber con media type, autenticación y reset según las convenciones del repositorio.
- [x] Se ejecutan y registran los tests enfocados, lint/type-checks y gates relevantes de backend/admin.
- [x] La evidencia QA queda persistida en el reporte OpenSpec aplicable y la recomendación de cierre separa evidencia local, CI y desplegada.

## Matriz de cobertura actual

| Criterio | Autoridad existente | Estado | Gap o evidencia pendiente |
| --- | --- | --- | --- |
| Waitlist, invitación y estados | `platform-admin.feature`, `platformadmin/invitations-direct.feature`, `apps/web/admin/src/views/WaitlistView.spec.ts`, `WaitlistEntryView.spec.ts`, `e2e/specs/waitlist-bulk-invite.spec.ts` | Parcial | Falta una comprobación E2E del acceso protegido y de la navegación por permiso; el lane mockeado no modela la invitación directa ni delivery. |
| Aceptación, activación, workspace y primer login | `local-auth.feature`, especialmente el escenario de registro invite-only válido | Parcial | Existe cobertura backend, pero falta evidencia local reciente de ejecución y falta prueba desplegada/manual de la journey completa. |
| Expiración y revocación | `local-auth.feature`; `platform-admin.feature`; `invitations-direct.feature` | Cubierto en código de prueba | Ejecutar y registrar la suite; confirmar evidencia de aceptación no mutante. |
| Reenvío e idempotencia | `invitations-direct.feature`; `DirectInvitationBddSteps.kt`; `notifications-admin.feature` | Cubierto en código de prueba | Ejecutar y registrar escenarios de reenvío, versión y retry idempotente. |
| Email duplicado | `invitations-direct.feature`; `DirectInvitationsView.spec.ts` | Cubierto en código de prueba | Ejecutar y registrar el 409 y el manejo UI. |
| Fallo de delivery y estado operativo | `notifications-admin.feature`; `DirectInvitationBddSteps.kt`; `NotificationsView.spec.ts` | Cubierto en código de prueba | Ejecutar y registrar fallo, retry, redacción y permisos; falta evidencia de proveedor/deploy. |
| Auditoría | `platform-admin.feature`, `PlatformAdminBddSteps.kt`, `GovernanceView.spec.ts` | Parcial | Confirmar escenarios de eventos y registrar query de auditoría; falta evidencia operatoria desplegada. |
| Límites de permisos | `platform-admin.feature`, `invitations-direct.feature`, `notifications-admin.feature`, `auth.store.test.ts`, `waitlist-bulk-invite.spec.ts` | Cubierto | Añadir la navegación protegida E2E que falta y ejecutar. |

## Slices verticales

- [x] RPI-001 Inventariar rutas Back Office, fixtures, features Cucumber, specs Vitest, reportes OpenSpec y la journey P0 de activación pendiente.
- [x] RPI-002 Definir las slices verticales mínimas y mapear cada criterio a un test ejecutable o a un prerrequisito manual/de despliegue explícito.
- [x] RPI-003 Añadir o completar escenarios backend de aceptación para la journey invitación/activación y los fallos operativos que no tengan cobertura.
- [x] RPI-004 Añadir o completar cobertura Playwright del lane mockeado para navegación protegida y workflows operativos que no estén cubiertos por Vitest.
- [ ] RPI-005 Ejecutar tests enfocados de backend/admin y corregir solamente defectos o problemas de estabilidad expuestos por QA.
- [x] RPI-006 Persistir resultados en el reporte OpenSpec aplicable y emitir recomendación separada para el contenedor #656 y el hito funcional bloqueado por DALLAY-556 / #652.

## Estado

- Tests y cambios de código: slice E2E añadida; no se modificó producción.
- Evidencia local: admin Vitest, type-check, lint, Playwright mockeado 15/15 y BDD backend 291/291 escenarios pasaron.
- Evidencia CI: no inferida; pendiente de un run remoto.
- Evidencia desplegada/manual: pendiente; no se sustituye por cobertura de mocks ni por BDD local.
- Reporte OpenSpec: actualizado en `openspec/changes/private-beta-launch-readiness/qa-report.md` con resultados exactos y límites de evidencia.
- Bloqueador conocido: el cierre funcional continúa bloqueado por DALLAY-556 / GitHub #652, aunque las issues hijas DALLAY-561 a DALLAY-576 estén Done.

## Próximo paso

Añadir primero la slice E2E de navegación protegida y permisos, ejecutar RED, implementar solo lo necesario en fixtures/tests, y luego correr los checks enfocados de admin y backend.
