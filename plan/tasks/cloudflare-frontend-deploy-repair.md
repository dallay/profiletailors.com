# Reparación de deploys Cloudflare de los frontends

## Ruta

Delegated direct: corregir un workflow compartido y validar la configuración de tres proyectos Cloudflare Pages sin cambiar código de producto.

## Objetivo

Conseguir que `app`, `admin` y `landing` publiquen sus releases en producción y que el workflow verifique el badge real de cada aplicación.

## Tareas

- [x] RPI-001 Confirmar el workflow actual, los proyectos Pages y sus estados de producción.
- [x] RPI-002 Corregir las verificaciones de versión y las rutas de health-check para las tres apps.
- [x] RPI-003 Corregir la configuración de producción de `app-profile-tailors`, `profiletailors-admin` y `profiletailors` sin tocar secretos ni dominios.
- [ ] RPI-004 Ejecutar los despliegues de los tres releases existentes o relanzar el flujo equivalente de forma segura.

  El push del workflow terminó correctamente, pero los jobs quedaron `skipped` porque no creó releases nuevas; se necesita un dispatch controlado para los tags existentes (`app@v0.3.12`, `admin@v0.0.11`, `landing@v0.2.16`). El workflow ahora usa una versión fijada de Wrangler independiente de las dependencias de cada tag y verifica los assets JavaScript de las SPAs.
- [ ] RPI-005 Verificar cada URL de producción y revisar el diff final.

## Criterios de aceptación

- Un release de cada frontend llega a un deployment de producción de su proyecto Pages.
- `app`, `admin` y `landing` muestran su versión y SHA correspondientes.
- El workflow no construye badges imposibles como `vv0.2.16`.
- `profiletailors.com`, `app.profiletailors.com` y el dominio del admin sirven el deployment correcto.
- No se eliminan deployments, no se modifican secretos y no se cambia código de producto.

## Evidencia inicial

- `landing@v0.2.16` se subió correctamente como deployment ad hoc `847e8698`, pero el workflow falló solo en la verificación.
- `profiletailors.com` todavía sirve `v0.2.13 (938b3dc)`.
- Los proyectos Pages reportan `production_deployments_enabled: false`.
- Los pushes posteriores no crearon releases y sus jobs de deploy fueron `skipped`.

## Estado

Working — el usuario autorizó subir el workflow corregido a `main`; después del push se revisará el run remoto. La configuración `production_deployments_enabled` permanece en `false` para mantener el comportamiento release-driven y evitar auto-deploys por merges a `main`.

## Evidencia actual

- `profiletailors`, `app-profile-tailors` y `profiletailors-admin` tienen `production_deployments_enabled: false`.
- No se cambiaron secretos, dominios ni deployments existentes.
- `.github/workflows/release-please.yml` verifica `${EXPECTED_VERSION} (${EXPECTED_SHORT_SHA})` en las tres apps.
- Marketing verifica `/terms/`, que contiene el badge real.
- `wrangler pages deploy --branch=main` coincide con `production_branch: main` y permite que el deployment sea de producción.

## Siguiente paso

Subir el cambio del workflow y revisar si el push genera un run con los tres jobs de deploy. No forzar un release-please push que pueda modificar versiones o changelogs.
