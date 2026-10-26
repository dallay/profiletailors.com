# Propuesta: Identificadores internos UUID nativos

## Intención

Eliminar la mezcla de UUID nativos y cadenas internas prefijadas (`pub-`, `ws-`, `pa-`, `wm-`, `user-`, `soacc-`, entre otras), que causa incompatibilidades al parsear, persistir y compartir IDs. Está confirmado que `OwnerId.from(raw)` usa `UUID.fromString(raw)`, mientras un workspace `ws-...` puede llegar prefijado al flujo de shortlinks. La incompatibilidad está confirmada por código; el mensaje de error exacto no se reprodujo. No se sustituirá la migración completa por un hotfix aislado.

## Alcance

### Incluido
- Inventariar y clasificar IDs internos por agregado/contexto, generación, parser, tabla/columna, API/eventos y consumidor; confirmar cada candidato con evidencia.
- Convertir también IDs internos con forma de código estable, como `role-owner`, `perm-001` y los PKs de compliance; actualizar sus seeds y consumidores. Mantener los campos semánticos separados (`role_key`, `permission_key` y códigos funcionales) sin alterarlos.
- Definir UUID nativo como representación de IDs internos e implementar la estrategia de transición de persistencia, datos existentes, contratos públicos y compatibilidad de clientes/consumidores.
- Actualizar generadores, modelos, mapeos, validaciones, consultas, fixtures y consumidores identificados; cubrir tablas y rutas de alto impacto mediante inventario del diseño.
- Definir rollout por etapas, observabilidad, criterios de avance, rollback y tratamiento de datos parcialmente migrados.
- Crear un ADR que superseda ADR-0005, actualizar su índice y reconciliar los contratos OpenSpec afectados.
- Incorporar `notifications`, creada fuera del baseline Liquibase, y sus referencias tipadas de
  auditoría/payloads; no confundirla con `notification_events`.
- Separar consentimiento público `WAITLIST` del workspace tenant, decisión explícita del usuario
  del 2026-10-26. Migrar históricos `waitlist:<key>` a la lista real y conservar aislamiento,
  unicidad, consulta, eventos y retirada según ADR-0030; no fabricar tenants para satisfacer UUID.

### Excluido
- IDs externos de proveedores, credenciales/tokens, claves de idempotencia y otros identificadores opacos, salvo evidencia de que son IDs internos.
- Cambios funcionales no necesarios para la migración y un hotfix de shortlinks como sustituto del alcance completo.

## Capacidades

### Nuevas capacidades
- Ninguna: el cambio modifica contratos existentes, no introduce una capacidad de producto nueva.

### Capacidades modificadas
- `publishing-shortlinks`: compatibilidad de IDs de propietario usados por los flujos de shortlinks.
- `publishing`: representación y consumo de identificadores de publicaciones y cuentas sociales donde sean contratos observables.
- `media-library`: representación y consumo de IDs de assets si el inventario confirma exposición contractual.
- `consent-scopes`: ámbitos `WORKSPACE` y `WAITLIST` del consentimiento existente, diferenciados
  sin cambiar finalidades, receipts del navegador ni autorización para activar captura productiva.
- Otras especificaciones existentes se añadirán en diseño solo si el inventario confirma cambios de requisitos observables; los detalles de almacenamiento y arquitectura se registrarán en el ADR, no como capacidades nuevas.

## Enfoque

Comenzar con un inventario trazable de productores, persistencia y consumidores, y clasificar cada formato como interno, externo u opaco. Comparar migración en sitio y transición compatible, incluyendo claves foráneas, índices, referencias históricas y clientes. Elegir el plan respaldado por evidencia antes de modificar datos; mantener lecturas/escrituras coherentes por etapa y definir verificaciones y reversión antes del rollout. El diseño debe incluir tablas y código de alto impacto, pruebas de regresión y actualización coordinada de consumidores.

## Áreas afectadas

| Área | Impacto | Descripción |
|---|---|---|
| `server/smp/` y migraciones Liquibase | Modificado | IDs, persistencia, parsers, generación y referencias |
| `apps/web/` y consumidores identificados | Modificado | Contratos y uso de IDs |
| `docs/architecture/adr/` | Modificado | ADR que supersede ADR-0005 e índice |
| `.agents/sdd/specs/` | Modificado | Contratos existentes afectados, según inventario |

## Riesgos

| Riesgo | Probabilidad | Mitigación |
|---|---|---|
| Referencias persistidas o consumidores quedan sin migrar | Alta | Inventario de productores/consumidores, reconciliación y pruebas de integridad |
| Ruptura de API o clientes durante transición | Alta | Política explícita de compatibilidad, etapas y criterios de rollout |
| Rollback no puede reconstruir valores anteriores | Media | Preservar mapeos/datos necesarios y ensayar reversión antes de avanzar |

## Plan de reversión

Detener el rollout ante fallos de integridad o compatibilidad. Ejecutar la reversión definida por etapa, restaurar la representación anterior usando el mapeo/datos retenidos y volver al lector/escritor compatible. No revertir migraciones destructivas ni eliminar datos de transición hasta validar restauración y referencias.

## Dependencias

- Inventario completo de esquema, contratos, consumidores y estado de datos antes de cerrar el diseño.
- ADR-0005 y su índice; política de compatibilidad de API y consumidores existentes.

## Criterios de éxito

- [ ] Cada ID candidato está clasificado y respaldado por evidencia; las exclusiones están justificadas.
- [ ] Persistencia, contratos públicos y consumidores tienen una estrategia coherente y verificable.
- [ ] Rollout y rollback cubren estados parciales, referencias y criterios de parada.
- [ ] El ADR supersede ADR-0005 sin reescribir su decisión histórica; las especificaciones afectadas quedan reconciliadas.
- [ ] Las pruebas demuestran integridad de referencias y compatibilidad definida; no queda un hotfix aislado como sustituto.
