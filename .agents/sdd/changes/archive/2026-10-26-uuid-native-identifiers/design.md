# Diseño: Identificadores internos UUID nativos

## Decisiones confirmadas de implementación

El usuario confirmó que se conserva `application/vnd.api.v1+json` y que los IDs internos cambian de `prefijo-UUID` a UUID sin prefijo dentro de v1. También confirmó incluir todos los IDs internos aunque hoy tengan forma de código, como `role-owner`, `perm-001` y los PKs internos de compliance. Los campos semánticos separados (`role_key`, `permission_key` y códigos funcionales) conservan su valor; seeds, consumidores y referencias de esos PKs deben migrarse juntos. Esta decisión sustituye las opciones marcadas `Undecided` más abajo; no se añade una versión de API ni una representación dual permanente.

El trabajo actual comprende la migración local del código, el esquema, las pruebas y la documentación. El usuario autorizó crear una rama, commit, push y PR solo después de que pasen todas las pruebas y builds requeridos. Esta autorización no permite ejecutar SQL productivo ni desplegar. El usuario ejecutará después el SQL revisado y verificará sus precondiciones y poscondiciones. El despliegue UUID-only queda bloqueado hasta completar la validación de datos y acordar una secuencia operativa que impida que aplicaciones antiguas escriban identificadores prefijados durante el cambio.

La partición de PR se decidirá después; no bloquea la implementación por cortes revisables en el worktree actual. `.gitignore` queda fuera de alcance por decisión del usuario y no se modifica.

## Enfoque técnico

### Ámbito de consentimiento público aprobado — 2026-10-26

El usuario eligió explícitamente `Ámbito WAITLIST` separado del workspace tenant. El valor
`waitlist:<key>` no representa un workspace y deja de almacenarse en esa referencia.

- Governance modela dos ámbitos mutuamente excluyentes: `WORKSPACE`, identificado por el UUID
  real de workspace, y `WAITLIST`, identificado por el UUID real de la lista. No se crea un tenant
  sintético ni se usa la clave semántica de la lista como un UUID.
- El puerto de captura transporta la identidad de la lista ya resuelta junto con la clave semántica.
  No se introduce una dependencia de dominio governance hacia entidades internas de leadcapture.
- La persistencia conserva referencias separadas `workspace_id` y `waitlist_id`, ambas UUID
  opcionales y con restricción de exactamente una referencia presente. La identidad activa del
  consentimiento incluye ámbito, sujeto, propósito y versión de política.
- Históricos `waitlist:<key>` se resuelven contra la lista existente antes de convertir tipos.
  Una clave ausente o ambigua bloquea la migración con rollback; no se inventa una identidad.
- Los eventos conservan el mismo ámbito y la retirada/consulta se restringe a ese ámbito.
  Los endpoints tenant existentes solo consultan `WORKSPACE`; no exponen consentimientos públicos.
- Se requieren regresiones de idempotencia, concurrencia, historial y retirada, aislamiento entre
  listas y workspaces, y ensayo SQL con históricos y guards negativos.

Esta decisión desbloquea implementación local; no autoriza el corte productivo ni elimina la
obligación de backup/restore, revisión legal y verificación de operaciones.

Un único valor UUID por identificador interno, con representación canónica UUID sin prefijo en PostgreSQL, Kotlin, API y clientes. Mantener los prefijos no es una solución transitoria admisible de forma permanente. La estrategia de despliegue queda condicionada a una comprobación operativa previa: corte coordinado solo si el owner de producción confirma que backend, clientes y consumidores pueden cambiar juntos y que la migración se puede ejecutar dentro de una ventana aprobada; no se presupone disponibilidad de downtime. Si no se confirma, usar expand-migrate-contract con una ventana temporal explícita y fecha/criterios para retirar compatibilidad. En ambos casos, evitar dual representation duradera.

El inventario completo de identificadores, columnas, referencias, generadores, parsers, owners/callers y consumidores es un gate previo a tareas de implementación. El diseño se apoya en los changelogs Liquibase existentes, que usan `varchar(64)` incluso para IDs UUID y contienen mezcla de IDs internos raw/prefijados; deben revisarse por columna y no por tipo SQL solamente.

## Decisiones

| Opción | Trade-off | Decisión |
|---|---|---|
| Prefijos conservados permanentemente | Legibles en logs, pero serialización duplicada y parseos incompatibles | Rechazada; superseder ADR-0005. Logs estructurados deben identificar campos/contexto; Kotlin usa tipos nominales distintos por concepto para prevenir confusión. |
| Corte coordinado | Un solo cambio coherente y sin compatibilidad duplicada; riesgo de coordinación y ventana de cambio | Preferido si operaciones confirma deployment coordinado sin inventar downtime. Validar con responsables antes de tasks. |
| Expand-migrate-contract | Admite rolling y clientes antiguos; requiere ventana temporal, métricas y retirada | Fallback obligatorio si no se confirma corte coordinado; compatibilidad acotada, con fecha/exit criteria y ninguna dual representation permanente. |
| Mantener `varchar` para IDs internos | Menor cambio de tipo SQL, pero no impone UUID ni optimiza integridad | No recomendado para estado final: columnas internas UUID nativas; preservar tipos de texto para campos externos/opaque. |

`Accept: application/vnd.api.v1+json` no versiona por sí solo la forma de los IDs. El usuario resolvió
la política de este cambio: conservar v1 y entregar UUID sin prefijo, con migración y actualización
coordinadas de consumidores. No se infiere compatibilidad con clientes externos antiguos ni se
habilita coexistencia de escritores legacy sobre el esquema convertido.

## Flujo y alcance de datos

Generadores/tipos de dominio → application/use cases → adapters HTTP/persistencia → UUID canónico en wire y PostgreSQL. Los mappers convierten en la frontera una sola vez. IDs de proveedor, `provider_connection_ref`, `provider_account_id`, credential/token, idempotency keys y otras claves opacas quedan fuera.

Liquibase muestra owners y referencias en `workspaces`, `workspace_ownerships`, `workspace_memberships`, `principals`, `social_connections`, `social_accounts`, `publications`, `publication_assets` y tablas posteriores. Referencias indirectas incluyen publicación-assets, jobs/delivery, ideas, social content, roles/grants/entitlements, consent/audit y otras tablas. Cada FK/índice y tabla sin FK debe confirmarse en inventario completo antes de declarar cobertura. El workspace `ws-` llega a `OwnerId.from`, que usa `UUID.fromString`; corregir el flujo en origen y probar recorrido integrado, no añadir solo parser tolerante. Incluir generadores prefijados de Workspace/Membership y publication/asset/social-account.

## Slices implementables (cada uno requiere precondiciones y rollback)

1. **Inventario + decisión de rollout** — Pre: acceso a schema/datos y owners de API/producción/clientes. Cambios: matriz exhaustiva con formato real, PK/FK/índice, caller/parser/generador, wire consumer y clasificación in/out; contar filas inválidas/colisiones y elegir estrategia. Checks: consultas de perfilado, validación de cardinalidad/FK, revisión por owners. Rollback: no hay cambios runtime; detener si hay valores no convertibles o rollout no aprobado.
2. **Contrato/ADR + superficie de aplicación** — Pre: slice 1 cerrado y compatibilidad API resuelta. Cambios: ADR nuevo supersede ADR-0005; tipos/generadores/parsers y DTOs/mappers Kotlin/API/frontend; corregir workspace→shortlinks. Checks: unitarias de formatos/tipos, serialización, contrato, callers completos. Rollback: revert de release antes de migración de datos; si contrato ya expuesto, solo mediante ruta compatible aprobada, no reintroducir dualidad permanente.
3. **Migración PostgreSQL** — Pre: mapa de columnas completo, backup/restore probado y secuencia aprobada. Cambios: Liquibase aditivo o coordinado según estrategia; normalizar IDs y cada FK a UUID de forma transaccional/por lotes según volumen; preservar PK, unicidad, índices, FK y relaciones; recrearlos/validarlos después de conversión. Revisar tablas owner refs y columnas de auditoría/eventos sin FK. Checks: conteos antes/después, UUID parseable, cero huérfanos/duplicados, FKs e índices validados, prueba sobre copia representativa y Postgres tests. Rollback: antes de contract, restaurar backup o conversión inversa solo si no hubo escrituras incompatibles; después de nuevas escrituras UUID-only no prometer rollback automático, detener y hacer forward-fix/restauración coordinada.
4. **Corte/retirada + verificación** — Pre: migración y release candidato validados, compatibilidad temporal aprobada si aplica. Cambios: activar UUID-only, retirar parsers/generadores legacy y alias de compatibilidad al satisfacer criterios. Checks: Postgres integration, BDD relevante, API/client contract, pruebas frontend, métricas de rechazos/parseo/FK y smoke. Rollback: condicionado a ventana/backup y ausencia de escrituras nuevas incompatibles; si no se cumple, rollback de aplicación solo si soporta esquema ya migrado o forward-fix.

## Observabilidad y pruebas

Registrar fallos de parseo por campo/endpoint, rechazos de formato, errores FK, filas de migración y uso de clientes legacy; no incluir credenciales ni depender de prefijos para diagnóstico. Umbrales/ventana de compatibilidad los define operaciones tras baseline. Añadir pruebas Postgres de migración sobre datos raw/prefijados y referencias, BDD del flujo shortlinks/auth/workspace y rutas de publicación aplicables, y Vitest/type-check/build/E2E de cada cliente afectado. Añadir fixtures mixtos y validar aislamiento de workspace; nunca convertir provider IDs o claves opacas.

## Archivos y artefactos previsibles

`server/smp/src/main/resources/db/changelog/**`; modelos/domain IDs, generadores, parsers, mappers/repositorios/controllers y pruebas en `server/smp/src/{main,test}`; clientes en `apps/web/app` y contratos/tests asociados; `docs/architecture/adr/{nuevo}.md`, índice ADR y documentación API/OpenSpec de shortlinks, publishing y media. Lista exacta se deriva del inventario, no se cierra por grep parcial.

## Preguntas / gates pendientes

- [ ] Operaciones confirma si producción permite corte coordinado y qué ventana está aprobada; no consta evidencia de downtime permitido.
- [x] Política API confirmada por el usuario: UUID raw dentro de `application/vnd.api.v1+json`, sin nueva versión. La actualización y validación operativa de clientes desplegados sigue separada.
- [ ] Completar y aprobar inventario de todas las tablas, tipos internos y consumidores antes de crear tasks.
