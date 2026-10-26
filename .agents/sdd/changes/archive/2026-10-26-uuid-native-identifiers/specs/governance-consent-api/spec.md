# Delta: API tenant de consentimiento y UUID

## Requisitos modificados

### Identidad y aislamiento de workspace

Los identificadores internos de principal, workspace y consentimiento MUST representarse como UUID
canónico sin prefijo. Los nombres de roles, permisos, propósitos y versiones de política siguen
siendo claves semánticas. Los valores nominales de ejemplos históricos como `ws-123` no constituyen
entradas válidas del contrato UUID.

El controlador MUST construir un ámbito `WORKSPACE` desde el contexto autorizado. MUST NOT aceptar
un ámbito `WAITLIST` proporcionado por el cliente ni interpretar un workspace nulo como permiso
para consultar consentimiento público. Se conservan los endpoints, permisos, códigos de estado
e idempotencia del contrato existente y `application/vnd.api.v1+json`.

## Escenarios añadidos

### Consulta tenant excluye consentimiento WAITLIST

- GIVEN un consentimiento público `WAITLIST` y un workspace autorizado, incluso con UUID idéntico
  al de la lista.
- WHEN el usuario consulta consentimiento mediante los endpoints tenant.
- THEN la respuesta MUST NOT incluir el consentimiento público.

### Retirada tenant no modifica consentimiento WAITLIST

- GIVEN consentimiento público activo para un sujeto, propósito y versión, sin consentimiento
  equivalente en el workspace autorizado.
- WHEN el usuario solicita retirada mediante el endpoint tenant.
- THEN se conserva la respuesta existente de ausencia de consentimiento (`404`) y el registro
  público y sus eventos MUST permanecer intactos.

### La captura pública no obtiene privilegios tenant

- GIVEN una captura pública válida en una lista existente.
- WHEN se registra su evidencia de consentimiento.
- THEN el ámbito MUST ser `WAITLIST` con el UUID real de la lista, sin crear un workspace ni
  conceder autenticación, membresía o permisos de workspace.
