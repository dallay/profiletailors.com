# Ámbitos de consentimiento

## Requisitos

- El consentimiento MUST pertenecer exactamente a un ámbito `WORKSPACE` o `WAITLIST`, identificado
  por el UUID de una entidad existente. Un ámbito `WAITLIST` MUST NOT crear ni simular un tenant.
- La clave semántica de waitlist MUST conservarse como clave semántica; MUST NOT sustituir una
  referencia UUID a workspace ni usarse como identidad numérica inferida.
- La unicidad activa MUST incluir el ámbito, sujeto, propósito y versión de política. Los ámbitos
  de listas diferentes y de workspaces MUST permanecer aislados aunque compartan sujeto y propósito.
- La retirada MUST preservar historial y eventos, y MUST afectar únicamente al ámbito solicitado.
- La migración MUST resolver históricos `waitlist:<key>` hacia la lista correspondiente y convertir
  proyecciones y eventos sin perder filas, identidad del sujeto, propósito, versión ni evidencia legal.
- Una referencia histórica ausente o ambigua MUST abortar con rollback. La conversión MUST NOT
  descartar consentimiento ni fabricar un workspace o una lista.
- Los endpoints tenant existentes MUST mantener autorización y alcance `WORKSPACE`; MUST NOT
  permitir acceso a registros `WAITLIST` por usar un workspace nulo.

## Escenarios de aceptación

1. Captura pública: una lista existente registra consentimiento con su UUID `WAITLIST`, sin workspace.
2. Idempotencia: repetir el mismo consentimiento en el mismo ámbito no duplica la proyección activa.
3. Aislamiento: dos listas y un workspace pueden registrar el mismo sujeto/propósito sin compartir estado.
4. Retirada: retirar un ámbito no afecta los otros y conserva eventos e historial del ámbito retirado.
5. Concurrencia: dos registros equivalentes concurrentes producen una sola proyección activa.
6. Histórico válido: proyección y eventos `waitlist:<key>` conservan sus relaciones tras el corte UUID.
7. Histórico inválido: una lista inexistente o una clave ambigua aborta sin mutación persistida.
8. Seguridad tenant: consultar o retirar consentimiento de workspace no devuelve ni modifica registros públicos.
