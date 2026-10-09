# Propuesta: Acortamiento y métricas de clics por workspace

## Intención

Integrar el acortador propio al crear publicaciones con enlaces y permitir que usuarios consulten clics de enlaces cortos pertenecientes a su workspace. La autorización debe impedir leer o inferir recursos o métricas de otros workspaces. El cambio archivado de 2026-10-04 contemplaba un sistema avanzado, no este alcance específico; Core V1 backend ya existe, pero no se ha demostrado que registre clics ni tenga UI de métricas.

## Alcance

### Incluido
- Definir e integrar en creación de posts la selección/momento de acortamiento y la presentación de URLs cortas, sin cambiar silenciosamente destinos o contenido.
- Medir clics de enlaces cortos y ofrecer consulta de métricas dentro del workspace.
- Hacer cumplir aislamiento de workspace en resolución/consulta administrativa de enlaces y métricas, con escenarios negativos entre workspaces.
- Diseñar el mecanismo mínimo de registro y consulta de clics tras verificar la infraestructura disponible.

### Excluido
- Reproducir el diseño histórico completo: outbox durable, reconciliación Edge/Logpush, dominios custom, QR, automatización de abuso, SLO/capacidad extrema.
- Decidir retención, datos personales, precisión garantizada o tecnología de medición sin evidencia y decisión explícita.
- Cambiar infraestructura/desplegar o implementar antes de aprobar especificación, diseño y tareas.

## Capacidades

### Nuevas
- `publishing-shortlinks`: integración de enlaces cortos en la creación de publicaciones.
- `workspace-shortlink-click-analytics`: consulta de clics de enlaces cortos aislada por workspace.

### Modificadas
- `publishing`: cambiar el comportamiento contractual de creación de posts con enlaces.

## Enfoque

Reutilizar Core V1 como propietario de enlaces; conectar creación de publicaciones con la gestión/resolución ya existente mediante contratos de aplicación, no acoplamiento directo a adaptadores. Añadir medición y una superficie de consulta con alcance derivado del workspace autenticado. La fase de especificación debe comparar registro síncrono/persistencia existente con alternativas mínimas, definir qué cuenta como clic y resolver retención/privacidad antes de fijar contrato. No asumir que el redirect actual captura eventos ni que exista bus, outbox, analítica o retención configurada.

## Áreas afectadas

| Área | Impacto | Descripción |
|---|---|---|
| `.agents/sdd/specs/publishing/spec.md` | Modificada | Creación de publicaciones enlazadas y presentación del shortlink |
| Nueva spec `publishing-shortlinks` | Nueva | Contrato de integración del acortador en publicación |
| Nueva spec `workspace-shortlink-click-analytics` | Nueva | Registro/consulta de clics y aislamiento |
| `server/smp/shortlinks` | Modificada | Medición/consulta; alcance sujeto a diseño |
| `apps/web/app` publicación y analítica | Modificada | Flujo del compositor y consulta de métricas |
| `docs/architecture/adr/0028-defer-shortlinks-beyond-core-v1.md` | Modificada | Explicitar decisión de producto que autoriza esta porción acotada antes de triggers; preservar diferimiento del resto |

## Riesgos

| Riesgo | Probabilidad | Mitigación |
|---|---|---|
| ADR-0028 difiere explícitamente trabajo más allá de Core V1 | Media | Actualizar el ADR mediante decisión autorizada antes de adoptar la especificación; no reinterpretar sus triggers implícitamente |
| Conteos incompletos o duplicados / costo en redirect | Media | Comparar alternativas con evidencia y acordar semántica de conteo y garantías realistas antes de diseño |
| Fuga entre workspaces en consultas | Media | Workspace derivado del contexto autorizado; pruebas/BDD adversariales con dos workspaces y no revelar existencia ajena |
| Incógnitas de privacidad y retención | Media | No persistir atributos personales por defecto; obtener decisión explícita sobre retención, minimización y borrado antes de cerrar spec |

## Reversión

Desactivar la integración de publicación y la consulta/medición nueva, manteniendo Core V1 y sus enlaces existentes. El diseño debe evitar que revertir UI o instrumentación invalide destinos o publicaciones ya guardadas; especificar cualquier migración o dato que requiera limpieza antes de implementar.

## Dependencias

- Reconciliación del ADR-0028 como decisión expresa de producto; la elección de alcance no autoriza modificar el ADR unilateralmente.
- Investigación en diseño de contratos Core V1, mecanismo de tracking existente y controles de autorización por workspace.

## Criterios de éxito

- [ ] Crear una publicación con enlaces produce un resultado de acortamiento definido y visible según decisión contractual.
- [ ] Se consultan métricas de clics solamente para enlaces cortos accesibles en el workspace actual.
- [ ] Pruebas cubren intento de lectura cruzada sin filtrar presencia ni métricas del otro workspace.
- [ ] Precisión, semántica de clic, retención y privacidad se especifican sin afirmar capacidades no demostradas.
- [ ] ADR-0028 refleja de forma explícita la autorización acotada y mantiene diferidas las capacidades avanzadas restantes.
