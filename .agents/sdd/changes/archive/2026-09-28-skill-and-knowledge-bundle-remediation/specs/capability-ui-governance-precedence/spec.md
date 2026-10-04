# Capability — UI Governance Precedence (P0-E)

## Purpose

Declarar en `.agents/DESIGN.md` la cadena de autoridad explícita de
las tres skills de UI (`impeccable`, `nothing-design`,
`frontend-design`), con `DESIGN.md` mismo como tie-breaker cuando
haya conflicto entre skills y entre skill vs. DESIGN.md. La cadena
es:

```
impeccable  →  nothing-design  →  frontend-design
                  (DESIGN.md = tie-breaker)
```

El resultado es que un agente que tenga que decidir entre recomendaciones
dispares de UI encuentra la precedencia sin ambigüedad en la primera
sección del archivo canónico.

## Authority

- Governance decision **G-4** (UI precedence).
- Trazabilidad: bloque **P0-E** del `proposal.md`.
- `.agents/DESIGN.md` es el archivo canónico de tokens del proyecto;
  esta capability modifica su preludio, no su contenido de tokens.

## Scope

- Archivos modificados: `.agents/DESIGN.md` (adición de sección
  "UI precedence chain" en las primeras 100 líneas).
- Excluidos: cualquier contenido de tokens (colors, type, spacing,
  components) y secciones existentes; solo se prependa o inserta una
  sección declarativa.

## Requirements

| REQ-ID                | Statement                                                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-UGP-001           | `.agents/DESIGN.md` SHALL contener una sección titulada "UI precedence chain" (o equivalente acordado en design) en las primeras 100 líneas.   |
| REQ-UGP-002           | La sección SHALL declarar explícitamente la cadena `impeccable → nothing-design → frontend-design` con `DESIGN.md` como tie-breaker.          |
| REQ-UGP-003           | La sección SHALL explicitar el rol de cada skill: `impeccable` = proceso (UX, IA, polish, QA); `nothing-design` = lenguaje visual vigente (tokens, type, monocromo, dark+light); `frontend-design` = inspiración anti-slop cuando los dos anteriores no aplican. |
| REQ-UGP-004           | La sección SHALL declarar que cuando un token concreto (color, type, spacing, motion) de DESIGN.md entre en conflicto con la recomendación de cualquier skill, gana DESIGN.md. |
| REQ-UGP-005           | La sección SHALL preservar los tokens y secciones existentes de DESIGN.md sin modificación (solo inserción).                                  |

## Scenarios

### Scenario: Sección declarada al inicio

**REQ-UGP-001, REQ-UGP-002**

- GIVEN `.agents/DESIGN.md` actual no contiene ninguna mención a
  `impeccable`, `nothing-design`, `frontend-design` ni a la cadena
- WHEN se aplica esta capability
- THEN SHALL existir una sección "UI precedence chain" en las
  primeras 100 líneas, SHALL listar las tres skills en el orden
  `impeccable → nothing-design → frontend-design`, y SHALL nombrar
  a `DESIGN.md` como tie-breaker.

### Scenario: Roles por skill explicitados

**REQ-UGP-003**

- GIVEN un agente lee la sección "UI precedence chain"
- WHEN necesita decidir entre `impeccable` (criterio de proceso) y
  `nothing-design` (token vigente)
- THEN SHALL encontrar la guía explícita en la sección: si la
  recomendación es de proceso/calidad, aplica `impeccable`; si es
  de token visual, aplica `nothing-design` con la salvedad de que
  `DESIGN.md` gana si hay colisión.

### Scenario: DESIGN.md gana en conflicto de tokens

**REQ-UGP-004**

- GIVEN una skill recomienda `#FFFFFF` para surface y `DESIGN.md`
  declara `--surface: var(--pt-color-bg-primary)` con un token
  oscuro
- WHEN el agente resuelve la contradicción
- THEN SHALL ceder ante `DESIGN.md` y SHALL dejar el token de
  DESIGN.md, citando la sección "UI precedence chain" como autoridad.

### Scenario: Tokens existentes preservados

**REQ-UGP-005**

- GIVEN el diff aplicado a DESIGN.md
- WHEN se inspecciona con `git diff`
- THEN SHALL consistir exclusivamente en líneas añadidas (sección
  declarativa); SHALL NO haber líneas modificadas dentro de las
  secciones de tokens existentes.
