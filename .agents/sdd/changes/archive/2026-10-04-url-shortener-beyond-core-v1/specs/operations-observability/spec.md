# Especificación: operations-observability

## Propósito

Fija objetivos operativos verificables; son objetivos, no afirmaciones de capacidad ya alcanzada.

## Requisitos

### Requirement: Exponer observabilidad de cardinalidad acotada

Las métricas MUST permitir observar disponibilidad, latencia, errores, cachés y límites sin etiquetas de cardinalidad alta, como URL, código corto, usuario o IP.

#### Scenario: Dimensiones de métricas
- GIVEN métricas de redirect y gestión
- WHEN se emiten
- THEN sus dimensiones tienen cardinalidad acotada y no contienen identificadores por solicitud

### Requirement: Observar pérdida y reconciliación de eventos

Las métricas MUST exponer eventos rechazados por cola local llena y avance de reconciliación desde logs durables del edge, con cardinalidad acotada y sin identificadores por enlace o solicitud. La analítica MUST informar `dataThrough` y `reconciledThrough` para distinguir datos procesados de datos reconciliados.

#### Scenario: Cola local llena
- GIVEN que `tryOffer()` no admite un evento por cola llena
- WHEN se registra el resultado operacional
- THEN una métrica observable refleja la pérdida sin etiquetas de alta cardinalidad

#### Scenario: Reconciliación edge
- GIVEN eventos de click recuperados desde logs durables del edge
- WHEN la reconciliación progresa
- THEN el avance puede observarse y los resultados exponen `reconciledThrough`

### Requirement: Definir y demostrar objetivos de servicio

El SLO objetivo de disponibilidad de redirect MUST ser 99.99%; objetivos de latencia redirect p50/p95/p99 son 20/50/150 ms y gestión p95 es 300 ms. La capacidad objetivo es 100 M enlaces activos, 50k redirects/s sostenidos, burst 250k redirects/s y 1k creaciones/s. El sistema MUST NOT afirmar que alcanza esos objetivos sin pruebas de carga representativas. Cambios de estado MUST registrarse mediante transactional outbox.

#### Scenario: Evidencia de capacidad
- GIVEN una declaración de que se alcanzan objetivos de capacidad
- WHEN se evalúa la evidencia
- THEN existe prueba de carga representativa para cada objetivo declarado

#### Scenario: Outbox transaccional
- GIVEN una mutación de estado persistida
- WHEN se confirma la transacción
- THEN el evento correspondiente queda registrado en outbox de forma transaccional
