# Proposal — Redesign Settings IA + Jerarquía (Rama X)

## Why

El critique del 2026-09-07 (snapshot `.impeccable/critique/2026-09-07T05-45-01Z__src-modules-settings-presentation-settingsview-vue.md`, score **25/40**) identificó que la página `/settings` tiene tres problemas estructurales: jerarquía de página invertida (h1 a 11px), ausencia de mapa de entrada / IA plana (5 cards hermanas), y switcher de idioma tratado como acción primaria en la esquina superior derecha del hero.

Este change arregla esos tres issues con cambios acotados a `/settings`, sin tocar el header, el sidebar global, los stores, ni el theme toggle. La rama X fue confirmada por el usuario: idioma se mueve a una card "Preferences" propia; theme y language pill en el sidebar account menu quedan intactos; header se mantiene chrome-limpo (AppHeader.test.ts:41-50 sigue pasando).

## What changes

- Hero de `/settings`: `<h1>` se eleva a `text-display-lg` (Space Grotesk, 36px / 1.1 line-height / -0.02em, peso 300). El eyebrow mono arriba pasa a ser `nav.settings`. El pill `overviewBadge` se mantiene como tercer elemento.
- Subtítulo reescrito a la voz PT (≤60 chars): EN `Connect a channel, name your workspace, change the surface.`, ES `Conecta un canal, nombra tu workspace, ajusta la superficie.`.
- `<aside data-testid="settings-preferences-panel">` del hero eliminado.
- Nueva `<Card data-testid="settings-preferences-panel">` insertada entre el grid Channels/Workspace y `PrivacySection`. Contiene eyebrow mono `settings.preferencesEyebrow` y el segmented control de idioma (EN/ES), preservando `data-testid="settings-language-en"` y `settings-language-es`.

## What does NOT change

- `apps/web/app/src/layouts/AppHeader.vue` y su spec `AppHeader.test.ts`.
- `apps/web/app/src/layouts/sidebar/SidebarAccountSection.vue` y su spec `SidebarAccountSection.test.ts`.
- `apps/web/app/src/shared/components/ThemeToggle.vue` y su spec `ThemeToggle.test.ts`.
- `apps/web/app/src/modules/settings/presentation/PrivacySection.vue` y `AccountClosureSection.vue`.
- Stores, router, services, otros módulos.
- `apps/web/app/e2e/specs/scheduler-settings.spec.ts` TC-21 (theme) y TC-22 (locale) — siguen apuntando al sidebar account menu.
- Theme toggle no se mueve a Settings en esta pasada. La card Preferences solo contiene idioma.

## Impact

- Affected specs (preservados): `AppHeader.test.ts`, `SidebarAccountSection.test.ts`, `ThemeToggle.test.ts`, `SettingsView.validation.spec.ts`, `PrivacySection.spec.ts`, `AccountClosureSection.spec.ts`, `scheduler-settings.spec.ts`.
- Affected specs (actualizados): `SettingsView.spec.ts` — selectores compuestos para el idioma.
- New specs: `SettingsView.hero.spec.ts`, `SettingsView.locale.spec.ts`, `SettingsView.preferences-card.spec.ts`.
- Affected locales: `apps/web/app/src/shared/i18n/locales/en/settings.ts`, `.../es/settings.ts`.
- Affected components: `apps/web/app/src/modules/settings/presentation/SettingsView.vue`.
- Cero cambios en router, stores, services, otros módulos.

## Out of scope (futuro)

- Mover theme toggle a Settings.
- Mover language switcher al sidebar.
- Reescribir copy de los títulos de sección (Channels, Workspace identity, Privacy & Data, Close Account) a la voz de acción. Esto es la Opción 3 del critique, separada.
- Endurecer la zona destructiva de Account Closure. Opción 2 del critique, separada.
- Arreglar el silencio de errores en `PrivacySection.vue`. Opción 2 del critique, separada.
- Descubribilidad del sidebar account menu como atajo a preferencias.
