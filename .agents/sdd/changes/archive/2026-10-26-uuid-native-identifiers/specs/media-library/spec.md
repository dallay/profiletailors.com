# Delta para media-library

## Requisitos añadidos

### Requisito: UUID canónico para IDs internos de assets

Si el inventario confirma que el ID de asset es un identificador interno de entidad/aggregate, el sistema MUST representarlo como UUID y serializarlo en DB y API/wire como UUID canónico sin prefijo textual. Los IDs opacos de almacenamiento y los IDs externos MUST conservar su clasificación y no convertirse por semejanza de formato.

#### Escenario: asset identificado con UUID

- GIVEN un asset cuyo ID interno es un UUID válido
- WHEN se crea, consulta o adjunta el asset
- THEN DB, API y clientes MUST intercambiar el UUID canónico sin prefijo
- AND la relación con workspace y publicación MUST referir al mismo asset

#### Escenario: filtro y autorización por workspace

- GIVEN assets de workspaces distintos con IDs internos UUID
- WHEN un usuario consulta o filtra assets en su workspace activo
- THEN MUST recibir solo assets autorizados de ese workspace
- AND un ID perteneciente a otro workspace MUST conservar la semántica de no encontrado

#### Escenario: clave opaca no se convierte

- GIVEN un storage key o identificador externo que no es ID interno de entidad
- WHEN se persiste o transmite junto al asset
- THEN MUST conservar su semántica independiente del UUID del asset

### Requisito: Transición segura de datos y consumidores de assets

La migración de IDs de assets y sus referencias MUST evitar pérdida, colisiones y relaciones huérfanas. La estrategia de compatibilidad, formato de entrada por etapa, despliegue, manejo parcial y rollback queda **Undecided** hasta que diseño seleccione rollout con evidencia del inventario. Cada slice de persistencia y API MUST tener pruebas de integración con serialización y referencias reales.

#### Escenario: migración sin pérdida

- GIVEN assets históricos, referencias de publicación y asociaciones de workspace inventariadas
- WHEN se aplica la migración elegida
- THEN cada asset y referencia MUST conservar la identidad correcta
- AND MUST detectarse colisiones, huérfanos o filas inválidas antes de avanzar

#### Escenario: compatibilidad definida por rollout

- GIVEN clientes o datos que aún usan la representación anterior
- WHEN el diseño define las etapas de transición
- THEN MUST especificar formatos aceptados por etapa, coordinación de clientes y criterio de retirada
- AND MUST definir rollback seguro para datos parcialmente migrados

#### Escenario: pruebas de integración por slice

- GIVEN un slice de DB, API o consumidor de media-library modificado
- WHEN se valida el slice
- THEN una prueba de integración MUST comprobar conversión, serialización, FK/referencias y filtros aplicables
- AND los límites de API MUST verificar autorización y aislamiento de workspace
