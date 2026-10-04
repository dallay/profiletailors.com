# PR 1314 — Fallo de Vitest del dashboard

## Ruta

Delegated direct; investigación de causa raíz y corrección limitada al dashboard.

## Tareas

- [x] RPI-001 Reproducir el fallo de `apps/web/app` con el comando CI y capturar primer error causal.
- [x] RPI-002 Identificar configuración/dependencia de Vite/Vitest y el cambio mínimo que resuelve compilación de tipos Vue: TypeScript 7.0.2 ya no ofrece el API clásico `sys` requerido por Vue 3.5 compiler-sfc.
- [x] RPI-003 Mantener TypeScript 6 en el dashboard y actualizar lockfile; no tocar el cambio staged ajeno `plan/tasks/sentry-integration.md`.
- [x] RPI-004 Suite dashboard, type-check y build verificados; diff revisado.

## Aceptación

- El fallo causal del job de CI está identificado y reproducido localmente.
- El suite dashboard pasa localmente.
- Sin supresiones, debilitamiento de reglas ni cambios ajenos.

## Evidencia

- Antes de la corrección, `pnpm test:coverage` reprodujo 41 archivos fallidos y 37 tests fallidos; el error raíz fue que Vue compiler-sfc intenta leer `typescript.sys`, removido en TypeScript 7.
- Cambio: `apps/web/app/package.json` limita TypeScript a `~6.0.3`; `pnpm-lock.yaml` actualizado. Las otras superficies conservan TypeScript 7.
- Después: `pnpm test:coverage` — PASS, 169 archivos / 1920 tests.
- `pnpm type-check` — PASS (`vue-tsc --build`).
- `pnpm build-only` — PASS; advertencia preexistente/configurada de chunk >500 kB y Sentry DSN no configurado.
- `pnpm lint` — terminó sin errores, reportó 2 warnings existentes en `AppShell.vue` y `SchedulerTimelineBody.vue`.
- `git diff --check` — PASS.
- No se modificó el archivo staged ajeno `plan/tasks/sentry-integration.md`; permanece como cambio staged ajeno.
- Commit creado: `fix(app): use TypeScript 6 for Vue type resolution`.
- Push a `origin/deps` — pendiente de completar.

## Estado

Working — commit creado; push pendiente.
