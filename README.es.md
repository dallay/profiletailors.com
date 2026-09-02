# ProfileTailors (profiletailors.com)

Proyecto para la plataforma ProfileTailors — creación y personalización de perfiles profesionales.

Descripción
-----------
ProfileTailors es la plataforma para crear y personalizar perfiles profesionales. Este repositorio contiene el código fuente de la aplicación (frontend y/o backend). La infraestructura y pipelines de despliegue están en el repositorio `dallay/profiletailors-deploy`.

Estado del repositorio
----------------------
- Lenguaje principal: Kotlin
- Licencia: GNU Affero General Public License v3.0
- Rama por defecto: `main`

Enlaces relevantes
------------------
- Sitio: https://profiletailors.com
- Project board (organización): https://github.com/orgs/dallay/projects/6
- Repositorio de despliegue: https://github.com/dallay/profiletailors-deploy

Rápido inicio (desarrollo)
--------------------------
1. Clona el repositorio:
   git clone git@github.com:dallay/profiletailors.com.git
2. Entra al directorio:
   cd profiletailors.com
3. Instala dependencias (según stack):
   - Frontend: cd frontend && npm install
   - Backend (Kotlin): revisa el build (Gradle/Maven) — por ejemplo: `./gradlew build`
4. Variables de entorno:
   - Copia `.env.example` a `.env` si existe y configura credenciales locales (DB, API keys).
5. Levanta los servicios localmente:
   - Frontend: `npm run dev` (si aplica)
   - Backend: `./gradlew run` o `java -jar build/libs/*.jar`
   - Alternativa: `docker-compose up` si hay un `docker-compose.yml`.

Tests
-----
- Ejecuta los tests con la herramienta de build del proyecto (Gradle/Maven) o con `npm test` si el frontend lo usa.

Despliegue
---------
La configuración de despliegue y CI/CD se gestiona desde `dallay/profiletailors-deploy`. El flujo recomendado:
1. Pull request → CI (tests, lint) → build
2. Merge a `main` → despliegue a staging
3. Revisión y despliegue a producción

Contribuir
----------
1. Abre una issue describiendo el cambio o el bug.
2. Crea una rama con prefijo `feat/`, `fix/` o `chore/`.
3. Haz un PR con descripción clara y checklist de pruebas.
4. Asigna reviewers y referencia la issue.

Gestión de issues y proyecto
---------------------------
Las issues se organizan en el Project de la organización: https://github.com/orgs/dallay/projects/6. Añade las issues relevantes desde `dallay/profiletailors.com` o `dallay/profiletailors-deploy` a ese Project para seguimiento.

Contacto y mantenedores
-----------------------
- Organización: dallay
- Mantenedor principal: yacosta738

Notas
-----
Si quieres, puedo:
- Añadir badges (build, coverage).
- Extender instrucciones específicas para la estructura del repo (por ejemplo: comandos Gradle exactos, estructura de carpetas).
- Crear un script aquí (`scripts/add-issues-to-project.js`) que añada todas las issues de `dallay/profiletailors.com` y `dallay/profiletailors-deploy` al Project (necesitarás un token con permisos `repo` + `project` para ejecutarlo), o puedo ejecutarlo yo si me proporcionas un token con permisos o me das acceso.
