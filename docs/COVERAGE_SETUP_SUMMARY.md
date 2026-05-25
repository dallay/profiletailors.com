# Test Coverage Configuration Summary

## ✅ Configuración Completada

Se ha configurado el test coverage completo para SonarQube en el proyecto Profile Tailors, cubriendo tanto backend (Kotlin/Spring Boot) como frontend (TypeScript/Astro).

## 📋 Archivos Creados

### Configuración de Testing

- ✅ `apps/web/marketing/vitest.config.ts` - Configuración de Vitest con coverage v8
- ✅ `apps/web/marketing/src/i18n/utils.test.ts` - Test de ejemplo para i18n utils

### Workflows de CI/CD

- ✅ `.github/workflows/sonarqube.yml` - Workflow completo de análisis SonarQube

### Documentación

- ✅ `docs/SONARQUBE_COVERAGE.md` - Guía técnica completa de coverage
- ✅ `docs/SONARQUBE_SETUP.md` - Guía de configuración inicial paso a paso

## 📝 Archivos Modificados

### Configuración del Proyecto

- ✅ `sonar-project.properties` - Actualizado con rutas de coverage para backend y frontend
- ✅ `apps/web/marketing/package.json` - Agregados scripts de test y dependencias de Vitest
- ✅ `.gitignore` - Agregadas exclusiones para reportes de coverage

## 🎯 Configuración por Stack

### Backend (Kotlin/Spring Boot)

**Coverage Tool**: JaCoCo (ya configurado)

**Reporte**: 

- XML: `server/smp/build/reports/jacoco/test/jacocoTestReport.xml`
- HTML: `server/smp/build/reports/jacoco/test/html/index.html`

**Comando**:

```bash
cd server/smp
./gradlew test jacocoTestReport
```

**Threshold**: 80% mínimo

**Exclusiones**:

- `**/config/**` - Clases de configuración
- `**/dto/**` - Data Transfer Objects
- `**/entity/**` - Entidades JPA
- `**/Application.kt` - Clase principal

### Frontend (TypeScript/Astro)

**Coverage Tool**: Vitest con v8 provider

**Reporte**:

- LCOV: `apps/web/marketing/coverage/lcov.info`
- HTML: `apps/web/marketing/coverage/index.html`

**Comandos**:

```bash
cd apps/web/marketing
pnpm install          # Instalar dependencias (incluye vitest)
pnpm test             # Ejecutar tests
pnpm test:coverage    # Ejecutar tests con coverage
```

**Threshold**: 80% mínimo (lines, functions, branches, statements)

**Exclusiones**:

- `node_modules/**`
- `dist/**`
- `.astro/**`
- `**/*.config.{js,ts}`
- `**/env.d.ts`

## 🔧 Configuración de SonarQube

### sonar-project.properties

```properties
# Coverage reports
sonar.coverage.jacoco.xmlReportPaths=server/smp/build/reports/jacoco/test/jacocoTestReport.xml
sonar.javascript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info
sonar.typescript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info

# Source directories
sonar.sources=server/smp/src/main,shared,apps/web/marketing/src
sonar.tests=server/smp/src/test,apps/web/marketing/src
```

## 🚀 Workflow de CI/CD

### `.github/workflows/sonarqube.yml`

El workflow ejecuta:

1. **Backend Tests**:
   - Setup JDK 21
   - `./gradlew test jacocoTestReport`
   - Verifica que el XML de JaCoCo existe

2. **Frontend Tests**:
   - Setup Node.js 22 + pnpm
   - `pnpm install`
   - `pnpm test:coverage`
   - Verifica que el LCOV existe

3. **SonarQube Analysis**:
   - Ejecuta scanner con ambos reportes
   - Verifica quality gate
   - Sube artifacts de coverage

### Triggers

- Push a `main` o `develop`
- Pull requests a `main` o `develop`

## 🔐 Secrets Requeridos

Configurar en **GitHub Settings → Secrets and variables → Actions**:

```text
SONAR_TOKEN           # Token de autenticación de SonarQube/SonarCloud
SONAR_HOST_URL        # URL del servidor (ej: https://sonarcloud.io)
SONAR_PROJECT_KEY     # Key del proyecto
SONAR_ORGANIZATION    # Organización de SonarCloud (solo para SonarCloud)
```

## 📊 Métricas de Coverage

### Backend

- **Actual**: Configurado con JaCoCo
- **Target**: ≥ 80%
- **Formato**: XML (compatible con SonarQube)

### Frontend

- **Actual**: Configurado con Vitest + v8
- **Target**: ≥ 80% (lines, functions, branches, statements)
- **Formato**: LCOV (compatible con SonarQube)

## 🧪 Testing

### Verificación Local

**Backend**:

```bash
cd server/smp
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

**Frontend**:

```bash
cd apps/web/marketing
pnpm test:coverage
open coverage/index.html
```

### Verificación en CI

1. Crear PR con estos cambios
2. El workflow `sonarqube.yml` se ejecutará automáticamente
3. Verificar que ambos reportes se generan correctamente
4. Revisar el dashboard de SonarQube

## 📚 Próximos Pasos

1. **Instalar dependencias del frontend**:

   ```bash
   cd apps/web/marketing
   pnpm install
   ```

2. **Configurar secrets en GitHub**:

   - Ir a Settings → Secrets and variables → Actions
   - Agregar los 4 secrets requeridos

3. **Actualizar `sonar-project.properties`**:

   - Reemplazar `profiletailors-change-me` con el project key real
   - Descomentar y configurar `sonar.organization` (si usas SonarCloud)

4. **Ejecutar tests localmente** para verificar:

   ```bash
   # Backend
   cd server/smp && ./gradlew test jacocoTestReport
   
   # Frontend
   cd apps/web/marketing && pnpm test:coverage
   ```

5. **Crear PR y verificar workflow**:

   - El workflow debe ejecutarse sin errores
   - Ambos reportes de coverage deben generarse
   - SonarQube debe mostrar las métricas

## 📖 Documentación

- **Guía Técnica**: `docs/SONARQUBE_COVERAGE.md`
- **Guía de Setup**: `docs/SONARQUBE_SETUP.md`
- **SonarQube Docs**: https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview

## ⚠️ Notas Importantes

1. **Frontend**: Necesitas ejecutar `pnpm install` para instalar Vitest y las dependencias de coverage
2. **Secrets**: El workflow fallará hasta que configures los secrets de SonarQube en GitHub
3. **Project Key**: Debes actualizar el project key en `sonar-project.properties` con el valor real de tu proyecto en SonarQube/SonarCloud
4. **Quality Gate**: El workflow incluye verificación de quality gate que puede fallar si no cumples los umbrales

## 🎉 Resultado Final

Una vez configurado completamente:

- ✅ Coverage automático en cada PR
- ✅ Reportes visuales en SonarQube dashboard
- ✅ Quality gate que bloquea PRs con baja cobertura
- ✅ Métricas históricas de calidad de código
- ✅ Detección de code smells, bugs y vulnerabilidades
