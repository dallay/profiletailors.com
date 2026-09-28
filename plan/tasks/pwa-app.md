# Tareas PWA foundation — app (slice 1, corregido)

Ruta: Delegated direct en Plan Mode (fast track, sin SDD formal)
Estado: Spec corregida tras revisión P0 — pendiente plan táctico
Alcance: Installable PWA with resilient offline shell. Sin API privada cacheada, sin push/sync, sin bottom-tab (slice 2)

## Tareas

- [x] 1. Configurar vite-plugin-pwa generateSW + estrategias Workbox en vite.config.ts — DONE 2026-09-25 (type-check PASS, build genera dist/sw.js + workbox-cb62cf87.js, precache 32 entries)
- [x] 2. Pulir manifest.webmanifest (id, scope, short_name Tailors, shortcuts) + headers + viewport-fit — DONE (screenshots pospuestos: sin browsers en entorno, manifest sin screenshots válido)
- [x] 3. Auth bootstrap unreachable + guard /offline — DONE TDD RED→GREEN (20 tests pass)
- [x] 4. Registro PWA + useOnlineStatus/usePwaInstall + UpdatePrompt + OfflineView + ruta /offline — DONE (type-check PASS)
- [x] 5. Tests + build final — DONE parcial: vitest 20 pass, build PASS, e2e creado pero NO ejecutado (sin browsers Playwright en entorno; correr en CI)

## Evidencia

- PR: <https://github.com/dallay/profiletailors.com/pull/1178> (feat/pwa-foundation → main)
- Diseño: tmp/plans/2026-09-25-pwa-offline-design.md
- Base verificada: manifest existe, sin SW, main.ts sin registro, _redirects SPA fallback, auth HttpOnly + Bearer
- Aprobación usuario: 2026-09-25 (alcance full offline-first, vía fast track, diseño generateSW aprobado)

## Pendientes (slice 2)

- [ ] 3. Actualizar index.html (viewport-fit, mobile-web-app-capable, theme)
- [ ] 4. Crear registro PWA + composables useOnlineStatus/usePwaInstall + UpdatePrompt
- [ ] 5. Crear OfflineView + ruta /offline standalone + ajuste guard offline
- [ ] 6. Crear BottomTabBar móvil + safe-areas en AppShell/Header
- [ ] 7. Tests vitest + e2e offline + Lighthouse PWA
- [ ] 8. Verificación final: build, sw.js generado, instalabilidad

## Estado

Working — esperando revisión de spec y plan táctico antes de escribir código
