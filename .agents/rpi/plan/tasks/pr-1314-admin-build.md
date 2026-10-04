# PR 1314 — Fallo de build del admin

## Ruta

Delegated direct; investigar incompatibilidad `vue-tsc` / TypeScript y corregir solo el workspace admin.

## Tareas

- [x] RPI-001 Reproducir `pnpm --filter @profiletailors/admin build` en el worktree actual; falla con `ERR_PACKAGE_PATH_NOT_EXPORTED` en `vue-tsc@3.3.11` con TypeScript 7.0.2.
- [x] RPI-002 Confirmar combinación compatible de TypeScript y `vue-tsc` con manifiestos/lockfile.
- [x] RPI-003 Aplicar TypeScript `~6.0.3` al admin y actualizar lockfile, preservando el borrado staged ajeno.
- [x] RPI-004 Ejecutar build, pruebas, lint y revisar diff.

## Aceptación

- Build del admin pasa localmente.
- Las otras superficies mantienen sus versiones.
- Sin cambios ajenos ni bypasses.

## Evidencia

- Antes del cambio, `pnpm --filter @profiletailors/admin build` reproduce `ERR_PACKAGE_PATH_NOT_EXPORTED` dentro de `vue-tsc@3.3.11` al buscar `typescript/lib/tsc` desde TypeScript 7.0.2.
- Cambio: `apps/web/admin/package.json` limita TypeScript a `~6.0.3`; `pnpm-lock.yaml` actualizado. Marketing conserva TypeScript 7.
- `pnpm --filter @profiletailors/admin build` — PASS (`vue-tsc --build` y `vite build`).
- `pnpm --filter @profiletailors/admin test:run` — PASS, 19 archivos / 137 tests.
- `pnpm --filter @profiletailors/admin lint` — PASS, 64 archivos.
- `git diff --check` — PASS.
- El borrado staged preexistente `plan/tasks/sentry-integration.md` no se alteró.
- Commits creados: `fix(admin): use TypeScript 6 with vue-tsc` y `docs(rpi): record admin build fix delivery`.
- Push a `origin/deps` — completado.

## Estado

Ready — commits empujados a `origin/deps`.
