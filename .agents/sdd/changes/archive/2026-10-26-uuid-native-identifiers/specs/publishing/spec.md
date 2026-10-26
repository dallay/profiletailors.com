# Delta para publishing

## Requisitos añadidos

### Requisito: Identificadores internos UUID canónicos

Los identificadores internos de publicaciones, cuentas sociales y demás entidades/aggregates confirmados por el inventario MUST representarse como UUID nativo, y MUST serializarse en DB y API/wire como UUID canónico sin prefijo textual. Referencias y claves foráneas MUST identificar la misma entidad tras la conversión. Este requisito no incluye IDs de proveedores, tokens ni claves opacas, que MUST conservar su semántica y formato propios.

#### Escenario: conversión y canonicalización

- GIVEN un identificador interno válido en el formato de entrada permitido por la etapa de rollout
- WHEN el sistema lo convierte y lo serializa
- THEN MUST producir un único UUID canónico sin prefijo en DB y API/wire

#### Escenario: formato inválido

- GIVEN un identificador interno que no satisface el formato UUID aceptado
- WHEN un endpoint o consumidor lo procesa
- THEN MUST devolver o propagar un error de validación claro
- AND MUST NOT tratarlo como otra clase de identificador ni efectuar una mutación

#### Escenario: referencias y workspace

- GIVEN publicaciones y cuentas sociales relacionadas dentro de un workspace
- WHEN se consultan o filtran por sus identificadores internos
- THEN referencias y FK MUST apuntar a las entidades correctas
- AND filtros y autorización MUST mantener el aislamiento del workspace

#### Escenario: API, clientes y superficies de salida

- GIVEN una respuesta, URL, log, mensaje o evento que incluya un identificador interno
- WHEN se publica o consume ese dato
- THEN la representación MUST seguir el contrato UUID canónico acordado
- AND APIs, clientes y consumidores MUST estar alineados con el mismo contrato

#### Escenario: identificador externo u opaco

- GIVEN un ID de proveedor, token, clave de idempotencia u otra clave opaca
- WHEN el sistema lo procesa junto a un ID interno
- THEN MUST preservar la clasificación y formato definidos para ese valor
- AND MUST NOT convertirlo a UUID interno por semejanza textual

### Requisito: Migración íntegra y reversible de identificadores

La transición MUST migrar los datos internos y referencias sin pérdida, colisiones ni asociaciones cruzadas. El diseño MUST seleccionar rollout, compatibilidad de clientes y consumidores, verificaciones, criterios de avance, tratamiento de migración parcial y rollback; ninguna estrategia se presume en esta especificación. MUST resolverse la transición de datos antes de activar lecturas/escrituras incompatibles.

#### Escenario: integridad de datos migrados

- GIVEN datos históricos y relaciones existentes inventariados
- WHEN se ejecuta la migración elegida
- THEN MUST conservarse cada entidad y relación con su UUID correcto
- AND MUST detectarse colisiones, registros huérfanos o conversiones no válidas antes de avanzar

#### Escenario: rollout y rollback

- GIVEN una etapa de despliegue con datos o consumidores parcialmente migrados
- WHEN se activa el paso siguiente o se requiere rollback
- THEN el diseño MUST definir criterios observables de avance y reversión
- AND el rollback MUST preservar los datos y evitar pérdida, colisión o interpretación ambigua

#### Escenario: decisión de compatibilidad

- GIVEN clientes o consumidores que aún intercambian el formato anterior
- WHEN el diseño elige la estrategia de rollout
- THEN MUST especificar formatos de lectura/escritura por etapa, coordinación de clientes y momento de retirada
- AND MUST documentar incompatibilidades deliberadas antes de su despliegue

#### Escenario: verificación por slices

- GIVEN cada slice de persistencia, aplicación, API o consumidor incluido en el inventario
- WHEN se integra el slice
- THEN MUST ejecutarse pruebas de integración que verifiquen serialización real y referencias afectadas
- AND los slices de API/workspace MUST comprobar autenticación, filtros y aislamiento
