# Tareas: URL shortener más allá de Core V1

## Pronóstico de carga de revisión

| Campo | Valor |
|---|---|
| Líneas | 900–1.600 |
| Riesgo >400 | Alto |
| Cadena PR | Sí: PR1 decisiones/outbox; PR2 API/dominios/abuso; PR3 analytics/QR/operación/carga |
| Entrega / cadena | pending / pending |

### Unidades

| Unidad | Objetivo | Dependencia/condición |
|---|---|---|
| 1 | Decisiones y eventos durables | PR1; click policy bloquea producción. |
| 2 | API, dominios y abuso | PR2 depende de PR1. |
| 3 | Analytics, QR, operación/capacidad | PR3 depende de decisiones aprobadas; externos condicionales. |

## DAG / fases

### Fase 0 — Alinear tareas con decisiones aprobadas

- [ ] 0.1 Implementar cola local acotada `tryOffer()`, publisher batch en background, métrica de overflow y reconciliación por edge logs; preservar best-effort hasta broker y at-least-once downstream. `specs/analytics/spec.md`, `specs/redirect-data-plane/spec.md`, `design.md` §7.
- [ ] 0.2 Implementar Cloudflare Custom Hostnames con activación condicionada a hostname/TLS/DNS; privacidad, retenciones, HMAC y borrado; endpoints analytics `/summary`, `/timeseries`, `/breakdown` y granularidades; QR local SVG/PNG con ZXing 3.5.4 como referencia, sujeto a revisión de licencia/dependencia. `design.md` §7, §9 y specs relevantes.
- [ ] 0.3 Completar y revisar una matriz de trazabilidad de cada requisito funcional de la lista 2.39 incluida en el prompt normativo frente a los escenarios de los seis specs y sus pruebas de aceptación. Registrar por requisito la referencia exacta al escenario y la evidencia prevista; marcar gaps reales o contradicciones para resolución. Criterio: matriz revisable con cobertura explícita y sin gaps sin tratar.

### Fase 1 — Persistencia y redirect (depende de Fase 0)

- [ ] 1.1 Añadir migraciones Liquibase para dominio predeterminado/outbox/dedupe `eventId`; probar atomicidad, rollback y unicidad en Postgres. `server/smp/src/main/resources/db/changelog/shortlinks/`; `operations-observability/spec.md` §18–30.
- [ ] 1.2 Implementar puertos/worker, reintentos y consumidor idempotente; probar at-least-once y que broker caído no retrase redirect. Unitarias e integración. `analytics/spec.md` §9–27.
- [ ] 1.3 Verificar cache TTL/expiración, negativos confirmados por primaria, invalidación ≤5s/inmediata cuarentena, fallback Redis/DB y ausencia de fetch destino. Pruebas shortlinks. `redirect-data-plane/spec.md` §9–35.

### Fase 2 — Gestión, dominios y abuso (depende de Fase 1)

- [ ] 2.1 Completar contrato `/api/v1`: UUIDv7, URLs/códigos/alias, idempotencia 24h, `If-Match`/412, RFC9457; cubrir unitarias, HTTP y BDD. `management-api/spec.md` §9–53.
- [ ] 2.2 Implementar límites configurables/`Retry-After`, controles abuso y cuarentena; probar tenant, 403 e invalidación inmediata. `abuse-security/spec.md` §9–35; BDD.
- [ ] 2.3 Implementar estados DNS→TLS→ACTIVE fail-closed tras decisión de proveedor; con infraestructura ausente verificar no activación, no simular integración. `domain-management/spec.md` §9–21.

### Fase 3 — Analytics, QR y capacidad (depende de 0.2 y Fase 1)

- [ ] 3.1 Implementar analytics tenant-scoped, dedupe, latencia objetivo ≤60s y minimización/purga IP según decisión; pruebas API/BDD. `analytics/spec.md` §9–33.
- [ ] 3.2 Generar QR decodificable con URL corta (nunca destino); validar formato/MIME decidido. `analytics/spec.md` §34–41.
- [ ] 3.3 Añadir métricas sin etiquetas de alta cardinalidad; ejecutar load test reproducible para 100M enlaces, 50k/s sostenido, burst 250k/s y 1k creaciones/s; reportar evidencia, no afirmar SLO sin prueba. `operations-observability/spec.md` §9–25.

### Fase 4 — Integración y cierre

- [ ] 4.1 Cubrir escenarios de los seis specs y cada fila 2.39; correr `just backend-check`, `just backend-bdd-fast`, Postgres/build según alcance. Externos no disponibles quedan condicionales.
- [ ] 4.2 Actualizar API docs/runbooks/ADR si aplica; contrastar contra Core V1 `3178a883`, sin reimplementar baseline.
