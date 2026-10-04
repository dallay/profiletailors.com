# Binding case-insensitive de providers sociales

## Ruta

Direct inline con TDD. La causa raíz está confirmada: el frontend usa identificadores lowercase
(`threads`) y Spring intenta convertirlos directamente al enum uppercase `SocialProvider`,
provocando `400` antes de llegar al caso de uso.

## Objetivo

Hacer que los endpoints provider-aware acepten los identificadores canónicos lowercase usados por el
dashboard (`linkedin`, `threads`) sin duplicar parches en el frontend ni alterar el dominio.

## Alcance

- Registrar un converter HTTP case-insensitive para `SocialProvider` en la configuración WebFlux
  existente.
- Cubrir el binding de `threads` y `linkedin` con una prueba WebFlux que atraviese el controller y
  verifique el comando enviado al mediator.
- Mantener la conversión estricta para valores desconocidos: un provider inválido debe seguir
  fallando en la capa de binding.
- No modificar `auth-api.ts`, `publishing.store.ts`, contratos OpenSpec ni configuración de
  despliegue: el contrato frontend lowercase ya es el correcto.

## Tareas

1. **RED — regresión de binding**
    - Añadir una prueba focalizada al área de configuración HTTP que construya un `WebTestClient`
      con `PublishingConnectionController` y `WebFluxConfiguration`.
    - Enviar `POST /api/publishing/threads/connections/initiate` con
      `{\"redirectUri\":\"https://app.example.com/integrations/threads/callback\"}`.
    - Verificar que el mediator recibe
      `InitiateProviderConnectionCommand(SocialProvider.THREADS, ...)`, demostrando que `threads` se
      convierte antes de invocar el método.
    - Ejecutar solo esa prueba y confirmar que falla con el comportamiento actual.

2. **GREEN — converter centralizado**
    - Crear el converter HTTP en el paquete de configuración WebFlux o en un archivo dedicado de
      infraestructura HTTP, siguiendo las convenciones existentes.
    - Normalizar con `uppercase()` usando una configuración de locale estable y resolver con
      `SocialProvider.valueOf`.
    - Registrar el converter mediante `WebFluxConfigurer.addFormatters` para cubrir path variables
      de los endpoints initiate, complete y disconnect.
    - Reejecutar la prueba de binding y confirmar que pasa.

3. **REFACTOR / cobertura de límites**
    - Añadir cobertura para `linkedin` lowercase y para un valor desconocido, si la prueba
      focalizada puede hacerlo sin inflar el alcance.
    - Mantener el código sin suppressions nuevas, casts inseguros ni comentarios explicativos.

4. **Verificación**
    - Ejecutar la prueba focalizada del backend.
    - Ejecutar las pruebas relacionadas de publishing HTTP y la prueba de configuración WebFlux.
    - Ejecutar `git diff --check` y revisar que solo aparezcan los archivos del fix y este plan,
      además de los cambios preexistentes.
    - No ejecutar builds amplios salvo que una prueba focalizada lo requiera.

## Evidencia esperada

- Antes del fix: la regresión falla porque `threads` no se puede convertir a `SocialProvider`.
- Después del fix: la regresión pasa y el comando llega al mediator con `SocialProvider.THREADS`.
- Los tests existentes de controller/configuración siguen pasando.
- El worktree conserva sin tocar `plan/tasks/pr-1209-ci-fixes.md` y
  `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/ConfigurationBddSteps.kt`.

## Estado

Ready — converter centralizado implementado y pruebas focalizadas, Detekt y Spotless pasan. El plan
queda listo para cerrar con revisión del diff y validación autenticada contra el entorno local.
