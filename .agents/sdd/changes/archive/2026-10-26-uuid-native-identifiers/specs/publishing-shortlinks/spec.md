# Delta para publishing-shortlinks

## Requisitos añadidos

### Requisito: Identificadores internos UUID en shortlinks

Los flujos de shortlinks MUST aceptar y transmitir el identificador interno del propietario como UUID canónico sin prefijo textual. Los identificadores inválidos MUST producir un error de validación claro y no una excepción de parseo opaca. El diseño MUST resolver la causa raíz del paso de workspace `ws-...` al flujo de shortlinks; un hotfix aislado no satisface este requisito.

#### Escenario: propietario UUID canónico

- GIVEN una solicitud autenticada con un propietario interno UUID válido
- WHEN se crea o consulta un shortlink
- THEN el flujo MUST conservar el mismo UUID canónico en los límites internos

#### Escenario: propietario con prefijo o formato inválido

- GIVEN un identificador de propietario interno con prefijo o sintaxis UUID inválida
- WHEN el flujo de shortlinks lo valida
- THEN MUST rechazar la solicitud con un error de formato identificable
- AND MUST NOT crear ni consultar un shortlink

#### Escenario: corrección de la causa raíz

- GIVEN un workspace que llega desde el contexto de autenticación al flujo de shortlinks
- WHEN se ejecuta el flujo integrado
- THEN el workspace MUST llegar como UUID interno canónico
- AND una prueba de integración MUST cubrir el recorrido completo, no solo el parser

### Requisito: Compatibilidad de shortlinks durante la transición

El contrato transitorio de lectura y escritura MUST seguir la estrategia de rollout decidida en diseño. **Undecided:** el formato aceptado por cada versión y la secuencia de despliegue quedan abiertos hasta elegir estrategia; el diseño MUST resolverlos antes de implementar.

#### Escenario: rollout elegido

- GIVEN clientes o datos de shortlinks que usan formatos distintos durante la transición
- WHEN se define el plan de rollout
- THEN el diseño MUST especificar qué formatos acepta cada etapa y cómo se verifica su compatibilidad
- AND MUST definir rollback sin pérdida ni ambigüedad de propietario
