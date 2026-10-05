# Specs — clasificación aplicada

Documento de la revisión. Las decisiones se aplicaron tras la autorización
del usuario; este archivo no es contrato de producto.

## Estado final del catálogo (`.agents/sdd/specs/`)

De 61 carpetas originales a **41 contratos de negocio** retenidos. Los
contratos de operación/gobernanza/infra están ahora bajo
`.agents/knowledge/`.

| Spec | Tipo | Destino propuesto |
|---|---|---|
| admin-authorization | no-negocio (permisos backoffice) | `.agents/knowledge/backoffice/` |
| age-eligibility | negocio (registro) | mantener |
| backoffice-admin-shell | no-negocio (shell admin) | `.agents/knowledge/backoffice/` |
| calendar-publication-sse | negocio (UI SPA) | mantener |
| channel-events-sse | negocio (UI SPA) | mantener |
| channel-list-api | negocio (API SPA) | mantener |
| community-inbox | negocio (feature) | mantener |
| composer-media-picker | negocio (UI) | mantener |
| composer-preview | negocio (UI) | mantener |
| dashboard-analytics | negocio (UI) | mantener |
| dashboard-content-pipeline | negocio (UI) | mantener |
| dashboard-engagement | negocio (UI) | mantener |
| dashboard-growth-score | negocio (UI) | mantener |
| dashboard-insights | negocio (UI) | mantener |
| dashboard-overview | negocio (UI) | mantener |
| dashboard-scheduling | negocio (UI) | mantener |
| email-notifications | no-negocio (infra emails) | `.agents/knowledge/infra/` |
| email-verification | negocio (registro/UX) | mantener |
| email-verification-ui | negocio (UI) | mantener |
| governance-consent-api | negocio (consentimiento) | mantener |
| iam | no-negocio (plataforma interna) | `.agents/knowledge/platform/` |
| idea-canvas | negocio (feature) | mantener |
| idea-composer | negocio (feature) | mantener |
| invitations | negocio (autorización) | mantener |
| lead-capture-waitlist | negocio (público) | mantener |
| legal-pages | negocio (legal/público) | mantener |
| login-experience | negocio (UI) | mantener |
| marketing-a11y-seo | negocio (marketing público) | mantener |
| mcp-server | negocio (API MCP a clientes externos) | mantener |
| mcp-tool-audit | no-negocio (governance) | `.agents/knowledge/platform/` |
| mcp-tool-authorization | no-negocio (governance) | `.agents/knowledge/platform/` |
| mcp-tool-registration | no-negocio (governance) | `.agents/knowledge/platform/` |
| media-attribution | negocio (UI/legal) | mantener |
| media-library | negocio (UI/API) | mantener |
| media-provider-unsplash | negocio (integración) | mantener |
| media-takedown | negocio (legal/governance) | mantener |
| oauth-callback-ui | negocio (UI) | mantener |
| oauth-initiation-api | negocio (API) | mantener |
| oauth-mcp-client-registration | negocio (API clientes externos) | mantener |
| password-recovery-ui | negocio (UI) | mantener |
| platform-admin-audit | no-negocio (backoffice) | `.agents/knowledge/backoffice/` |
| platform-configuration | no-negocio (backoffice) | `.agents/knowledge/backoffice/` |
| platform-notifications | no-negocio (backoffice) | `.agents/knowledge/backoffice/` |
| privacy-compliance | contrato privacidad | mantener |
| privacy-data-aggregation | contrato privacidad | mantener |
| privacy-dsar | contrato privacidad | mantener |
| private-beta-launch-readiness | no-negocio (gate proceso) | `.agents/knowledge/release-readiness/` |
| public-application-capabilities | negocio (API pública) | mantener |
| publishing | negocio (dominio) | mantener |
| registration | negocio (UX/API) | mantener |
| scheduler-url-state-standard | no-negocio (estándar URL técnico) | `.agents/knowledge/standards/` |
| social-content-sync | negocio (integración) | mantener |
| user-administration | no-negocio (backoffice) | `.agents/knowledge/backoffice/` |
| users | no-negocio (backoffice) | `.agents/knowledge/backoffice/` |
| visual-calendar | negocio (UI) | mantener |
| waitlist | no-negocio (backoffice) | `.agents/knowledge/backoffice/` |
| workspace-scoped-oauth | negocio (autorización multi-tenant) | mantener |

## Resumen

- Mantener: **40** specs (contratos de negocio, producto, dominio, API pública,
  UI de cliente o privacidad/legal).
- Mover a `.agents/knowledge/`: **16** specs.
  - backoffice/ (8): admin-authorization, backoffice-admin-shell,
    platform-admin-audit, platform-configuration, platform-notifications,
    user-administration, users, waitlist
  - platform/ (4): iam, mcp-tool-audit, mcp-tool-authorization,
    mcp-tool-registration
  - infra/ (1): email-notifications
  - standards/ (1): scheduler-url-state-standard
  - release-readiness/ (1): private-beta-launch-readiness
  - (más las 13 ya trasladadas en `.agents/knowledge/` y `.agents/knowledge/skill-capabilities/`)

## Notas

- `legal-pages` y `waitlist` ya estaban en mis auditorías anteriores con
  divergencias (publicación bloqueada vs APPROVED; email substring vs igualdad
  normalizada). Se mantienen como contratos de negocio y se reconcilian más
  adelante.
- `private-beta-launch-readiness` describe un gate de proceso, no contrato de
  producto. Lo propongo en `release-readiness/` como referencia.
- `scheduler-url-state-standard` es un estándar técnico URL, no contrato de
  comportamiento. Lo propongo en `standards/`.
- `platform-notifications` describe operación admin de notificaciones; la
  capability de envío ya vive en `email-notifications` (también propuesto a
  mover).
