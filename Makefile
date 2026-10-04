# ────────────────────────────────────────────────────────────────
# Profile Tailors — Monorepo Command Hub (GNU Make)
# ────────────────────────────────────────────────────────────────
# Usage:  make <target>          # run a target
#         make help / make list  # list all targets
#
# One interface to rule them all:
#   Frontend (pnpm/Astro)  ·  Backend (Gradle/Kotlin)
#   Infrastructure (Docker)       ·  Git hooks (Lefthook)
#   CI simulation
#
# GNU Make equivalent of the repository Justfile.
# ────────────────────────────────────────────────────────────────

.DEFAULT_GOAL := help

# ——— OS & Paths —————————————————————————————————───────────────
ifeq ($(OS),Windows_NT)
    GRADLE_ROOT := gradlew.bat
else
    GRADLE_ROOT := ./gradlew
endif

FRONTEND_DIR       := apps/web/marketing
APP_DIR            := apps/web/app
ADMIN_DIR          := apps/web/admin
DOCKER_COMPOSE     := docker compose
PRODUCTION_COMPOSE := infra/apps/smp/production/compose.yaml
PRODUCTION_ENV     := infra/apps/smp/production/.env
SWARM_STACK        := infra/apps/smp/swarm/stack.yaml
SWARM_ENV          := infra/apps/smp/swarm/.env

# Default variable options
IMAGE_NAME       ?= profiletailors/smp:local
VERSION          ?= 0.0.1-SNAPSHOT
IMAGE_REPOSITORY ?= profiletailors/smp
EXCLUDE_TAGS     ?=
SERVICE          ?=
FLAGS            ?=
force            ?=

# ═══════════════════════════════════════════════════════════════
# HELP / LIST
# ═══════════════════════════════════════════════════════════════

.PHONY: help list
help list:
	@echo "────────────────────────────────────────────────────────────────"
	@echo "Profile Tailors — Monorepo Command Hub (Make equivalent)"
	@echo "────────────────────────────────────────────────────────────────"
	@echo "Usage: make <target> [VARIABLE=value]"
	@echo "       make help / make list"
	@echo ""
	@echo "Available targets:"
	@echo "  --- SETUP ---"
	@echo "  install                      Install all workspace dependencies"
	@echo "  playwright-install           Install Playwright browser binaries"
	@echo "  setup                        Full setup: .env -> install -> browsers -> hooks -> agentsync"
	@echo "  hooks-install                Install Lefthook git hooks"
	@echo "  worktree-check               Verify worktree identity and runtime env"
	@echo "  worktree-info                Print worktree namespace and local URLs"
	@echo ""
	@echo "  --- FRONTEND ---"
	@echo "  dev-frontend                 Start both frontend dev servers (force=--force)"
	@echo "  app                          Start only the Vue 3 app (dashboard SPA)"
	@echo "  admin                        Start the platform admin SPA"
	@echo "  frontend-build               Build marketing site for production"
	@echo "  app-build                    Build dashboard SPA for production"
	@echo "  admin-build                  Build platform admin SPA for production"
	@echo "  release-dashboard-build      Build dashboard SPA against deployed API (API_BASE_URL=...)"
	@echo "  frontend-preview             Preview production build locally"
	@echo "  frontend-lint                Lint frontend with Biome"
	@echo "  frontend-format              Format frontend code with Biome"
	@echo "  frontend-check               Run Astro type check"
	@echo "  frontend-test                Run frontend unit tests (Vitest)"
	@echo "  admin-test                   Run platform admin unit tests (Vitest)"
	@echo "  admin-check                  Run platform admin type check"
	@echo "  frontend-test-cov            Run frontend unit tests with coverage (FLAGS=...)"
	@echo "  frontend-test-e2e            Run E2E tests (Playwright headless)"
	@echo "  frontend-test-e2e-ui         Run E2E tests in Playwright UI mode"
	@echo "  frontend-test-e2e-headed     Run E2E tests headed"
	@echo "  frontend-test-e2e-report     Open Playwright HTML report"
	@echo "  app-test-e2e-media-mocked    Run app Media Library mocked E2E tests"
	@echo "  app-test-e2e-media-real      Run app Media Library real-CAS smoke E2E tests"
	@echo "  app-test-e2e-media           Run available app Media Library E2E lanes"
	@echo ""
	@echo "  --- BACKEND & SERVE ---"
	@echo "  backend-build                Compile and package backend"
	@echo "  backend-image                Build local OCI backend image (IMAGE_NAME=... VERSION=...)"
	@echo "  release-backend-verify       Verify release image (IMAGE_NAME=...)"
	@echo "  release-backend-image        Build release OCI backend image (VERSION=... IMAGE_REPOSITORY=...)"
	@echo "  backend-test                 Run backend tests (EXCLUDE_TAGS=...)"
	@echo "  backend-test-fast            Run backend tests (fast: no postgres, modularity)"
	@echo "  backend-check                Run full backend check: tests + Detekt"
	@echo "  backend-lint                 Run Detekt static analysis"
	@echo "  backend-lint-shared          Run Detekt across all shared modules"
	@echo "  backend-run                  Start Spring Boot dev server"
	@echo "  serve                        Start backend + frontend app in parallel (force=--force)"
	@echo "  serve-force                  Restart backend + frontend app, killing previous servers"
	@echo "  kill-servers                 Kill dev processes owned by this worktree"
	@echo "  backend-coverage             Run tests with JaCoCo coverage report"
	@echo "  backend-bdd-fast             Run fast BDD suite"
	@echo "  backend-test-postgres        Run PostgreSQL integration tests"
	@echo "  backend-bdd-postgres         Run Postgres BDD suite"
	@echo ""
	@echo "  --- INFRASTRUCTURE ---"
	@echo "  infra-up                     Start infrastructure services (Postgres, etc.)"
	@echo "  infra-down                   Stop and remove infrastructure containers"
	@echo "  infra-logs                   Tail infrastructure logs (SERVICE=...)"
	@echo "  infra-restart                Restart infrastructure services"
	@echo "  infra-info                   Show host ports assigned to infrastructure"
	@echo "  production-prepare          Generate files for production Compose"
	@echo "  production-config           Validate production Compose configuration"
	@echo "  production-build-dashboard  Build production dashboard image from source"
	@echo "  production-up               Start production Compose stack"
	@echo "  production-down             Stop production stack"
	@echo "  production-status           Show production stack status"
	@echo "  production-smoke            Verify HTTP routing, migrations, etc. (FLAGS=...)"
	@echo "  production-logs             Tail production stack logs (SERVICE=...)"
	@echo "  swarm-prepare               Generate config/secrets for Swarm"
	@echo "  swarm-label-storage         Label Swarm node for storage (NODE=...)"
	@echo "  swarm-config                Validate Swarm stack"
	@echo "  swarm-deploy                Deploy Swarm stack"
	@echo "  swarm-status                Show Swarm stack status"
	@echo "  swarm-logs                  Tail Swarm service logs (SERVICE=...)"
	@echo "  swarm-rollback              Roll back Swarm service (SERVICE=...)"
	@echo "  swarm-remove                Remove Swarm stack"
	@echo ""
	@echo "  --- DOCS, CI & CLEANUP ---"
	@echo "  docs-links                   Check Markdown links with lychee"
	@echo "  docs-lint                    Lint Markdown with markdownlint-cli2"
	@echo "  licence-check                Scan dependency licences for AGPL-3.0 compatibility"
	@echo "  ci-local                     Fast CI checks (lint, unit tests, builds)"
	@echo "  ci-full                      CI checks including Postgres integration + BDD"
	@echo "  ci                           Full CI pipeline (sequential fail-fast checks)"
	@echo "  clean                        Clean all build artifacts and caches"

# ═══════════════════════════════════════════════════════════════
# SETUP
# ═══════════════════════════════════════════════════════════════

.PHONY: install playwright-install setup hooks-install worktree-check worktree-info

install:
	pnpm install --frozen-lockfile

playwright-install:
	pnpm --filter marketing exec playwright install chromium firefox webkit
	pnpm --filter app exec playwright install chromium
	pnpm --filter @profiletailors/admin exec playwright install chromium

setup:
	node -e "const fs=require('fs');if(!fs.existsSync('.env')&&fs.existsSync('.env.example'))fs.copyFileSync('.env.example','.env')"
	$(MAKE) install
	$(MAKE) playwright-install
	$(MAKE) hooks-install
	pnpm dlx @dallay/agentsync@latest apply
	node scripts/setup-optional-tools.mjs

hooks-install:
	node scripts/hooks-install.mjs

worktree-check:
	node --test scripts/worktree-isolation.test.mjs

worktree-info:
	node scripts/worktree-context.mjs --json

# ═══════════════════════════════════════════════════════════════
# FRONTEND
# ═══════════════════════════════════════════════════════════════

.PHONY: dev-frontend app admin frontend-build app-build admin-build release-dashboard-build \
        frontend-preview frontend-lint frontend-format frontend-check frontend-test \
        admin-test admin-check frontend-test-cov frontend-test-e2e frontend-test-e2e-ui \
        frontend-test-e2e-headed frontend-test-e2e-report app-test-e2e-media-mocked \
        app-test-e2e-media-real app-test-e2e-media

dev-frontend:
	node scripts/dev-frontend.mjs "$(force)"

app:
	node scripts/frontend-run.mjs app

admin:
	node scripts/frontend-run.mjs admin

frontend-build:
	cd $(FRONTEND_DIR) && pnpm build

app-build:
	cd $(APP_DIR) && pnpm build

admin-build:
	cd $(ADMIN_DIR) && pnpm build

release-dashboard-build:
	cd $(APP_DIR) && VITE_API_BASE_URL='$(API_BASE_URL)' pnpm build

frontend-preview:
	cd $(FRONTEND_DIR) && pnpm preview

frontend-lint:
	cd $(FRONTEND_DIR) && pnpm lint

frontend-format:
	cd $(FRONTEND_DIR) && pnpm format

frontend-check:
	cd $(FRONTEND_DIR) && pnpm check

frontend-test:
	cd $(FRONTEND_DIR) && pnpm test

admin-test:
	cd $(ADMIN_DIR) && pnpm test:run

admin-check:
	cd $(ADMIN_DIR) && pnpm type-check

frontend-test-cov:
	cd $(FRONTEND_DIR) && pnpm test:coverage $(FLAGS)

frontend-test-e2e:
	$(MAKE) playwright-install
	cd $(FRONTEND_DIR) && pnpm test:e2e
	pnpm --filter app test:e2e:media:mocked

frontend-test-e2e-ui:
	$(MAKE) playwright-install
	cd $(FRONTEND_DIR) && pnpm test:e2e:ui

frontend-test-e2e-headed:
	$(MAKE) playwright-install
	cd $(FRONTEND_DIR) && pnpm test:e2e:headed

frontend-test-e2e-report:
	cd $(FRONTEND_DIR) && pnpm test:e2e:report

app-test-e2e-media-mocked:
	$(MAKE) playwright-install
	pnpm --filter app test:e2e:media:mocked

app-test-e2e-media-real:
	$(MAKE) playwright-install
	pnpm --filter app test:e2e:media:real

app-test-e2e-media: app-test-e2e-media-mocked app-test-e2e-media-real

# ═══════════════════════════════════════════════════════════════
# BACKEND & SERVE
# ═══════════════════════════════════════════════════════════════

.PHONY: backend-build backend-image release-backend-verify release-backend-image \
        backend-test backend-test-fast backend-check backend-lint backend-lint-shared \
        backend-run serve serve-force kill-servers backend-coverage backend-bdd-fast \
        backend-test-postgres backend-bdd-postgres

backend-build:
	$(GRADLE_ROOT) :server:smp:build --no-daemon

backend-image:
	BP_OCI_AUTHORS="Dallay" BP_OCI_CREATED="$$(date -u +%Y-%m-%dT%H:%M:%SZ)" BP_OCI_DESCRIPTION="Backend service for the Profile Tailors social media management platform." BP_OCI_DOCUMENTATION="https://github.com/dallay/profiletailors.com/tree/main/server/smp" BP_OCI_LICENSES="AGPL-3.0-only" BP_OCI_REVISION="$$(git rev-parse HEAD)" BP_OCI_SOURCE="https://github.com/dallay/profiletailors.com" BP_OCI_TITLE="Profile Tailors SMP" BP_OCI_URL="https://profiletailors.com" BP_OCI_VENDOR="Dallay" BP_OCI_VERSION="$(VERSION)" $(GRADLE_ROOT) :server:smp:bootBuildImage -PreleaseVersion="$(VERSION)" --imageName="$(IMAGE_NAME)" --no-daemon

release-backend-verify:
	node scripts/run-shell-script.mjs scripts/verify-release-image.sh "$(IMAGE_NAME)"

release-backend-image:
	node scripts/release-backend-image.mjs "$(VERSION)" "$(IMAGE_REPOSITORY)"

backend-test:
	node scripts/gradle-run.mjs :server:smp:test --no-daemon $(if $(EXCLUDE_TAGS),-PexcludeTags=$(EXCLUDE_TAGS))

backend-test-fast:
	node scripts/gradle-run.mjs :server:smp:test --no-daemon -PexcludeTags=modularity,postgres

backend-check:
	node scripts/gradle-run.mjs :server:smp:check --no-daemon -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest

backend-lint:
	$(GRADLE_ROOT) :server:smp:detekt --no-daemon

backend-lint-shared:
	$(GRADLE_ROOT) detekt --no-daemon

backend-run:
	node scripts/gradle-run.mjs :server:smp:bootRun --args=--spring.profiles.active=dev

serve:
	node scripts/serve-dev.mjs "$(force)"

serve-force:
	$(MAKE) serve force=--force

kill-servers:
	node scripts/kill-servers.mjs

backend-coverage:
	node scripts/gradle-run.mjs :server:smp:test :server:smp:jacocoTestReport --no-daemon

backend-bdd-fast:
	node scripts/gradle-run.mjs :server:smp:bddFastTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

backend-test-postgres:
	node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

backend-bdd-postgres:
	node scripts/gradle-run.mjs :server:smp:bddPostgresTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

# ═══════════════════════════════════════════════════════════════
# INFRASTRUCTURE
# ═══════════════════════════════════════════════════════════════

.PHONY: infra-up infra-down infra-logs infra-restart infra-info \
        production-prepare production-config production-build-dashboard \
        production-up production-down production-status production-smoke production-logs \
        swarm-prepare swarm-label-storage swarm-config swarm-deploy swarm-status \
        swarm-logs swarm-rollback swarm-remove

infra-up:
	node scripts/compose-run.mjs up -d

infra-down:
	node scripts/compose-run.mjs down

infra-logs:
	node scripts/compose-run.mjs logs -f $(SERVICE)

infra-restart:
	node scripts/compose-run.mjs restart

infra-info:
	node scripts/compose-run.mjs ports

production-prepare:
	node scripts/run-shell-script.mjs infra/apps/smp/production/prepare.sh

production-config:
	$(DOCKER_COMPOSE) --env-file $(PRODUCTION_ENV) -f $(PRODUCTION_COMPOSE) config --quiet

production-build-dashboard:
	$(DOCKER_COMPOSE) --env-file $(PRODUCTION_ENV) -f $(PRODUCTION_COMPOSE) build dashboard

production-up:
	$(DOCKER_COMPOSE) --env-file $(PRODUCTION_ENV) -f $(PRODUCTION_COMPOSE) up -d --wait

production-down:
	$(DOCKER_COMPOSE) --env-file $(PRODUCTION_ENV) -f $(PRODUCTION_COMPOSE) down

production-status:
	$(DOCKER_COMPOSE) --env-file $(PRODUCTION_ENV) -f $(PRODUCTION_COMPOSE) ps

production-smoke:
	node scripts/run-shell-script.mjs infra/apps/smp/production/smoke-test.sh $(FLAGS)

production-logs:
	$(DOCKER_COMPOSE) --env-file $(PRODUCTION_ENV) -f $(PRODUCTION_COMPOSE) logs -f $(SERVICE)

swarm-prepare:
	node scripts/run-shell-script.mjs infra/apps/smp/swarm/prepare.sh

swarm-label-storage:
	docker node update --label-add profiletailors.storage=true "$(NODE)"

swarm-config:
	node scripts/swarm-env-run.mjs config

swarm-deploy:
	node scripts/run-shell-script.mjs infra/apps/smp/swarm/deploy.sh

swarm-status:
	node scripts/swarm-env-run.mjs status

swarm-logs:
	node scripts/swarm-env-run.mjs logs "$(SERVICE)"

swarm-rollback:
	node scripts/swarm-env-run.mjs rollback "$(SERVICE)"

swarm-remove:
	node scripts/run-shell-script.mjs infra/apps/smp/swarm/remove.sh

# ═══════════════════════════════════════════════════════════════
# DOCUMENTATION & LICENCE COMPLIANCE
# ═══════════════════════════════════════════════════════════════

.PHONY: docs-links docs-lint licence-check

docs-links:
	@echo "▸ Markdown link check (lychee)..."
	lychee --no-progress --cache --max-cache-age 1d --exclude-path node_modules --exclude 'http://localhost' --exclude 'https://localhost' --exclude-path openspec './**/*.md' './**/*.mdx'

docs-lint:
	@echo "▸ Markdown lint (markdownlint-cli2)..."
	pnpm exec markdownlint-cli2

licence-check:
	@echo "▸ Frontend: dependency licence scan..."
	pnpm licenses list --json | node scripts/check-frontend-licences.mjs
	@echo "▸ Backend: dependency licence report..."
	$(GRADLE_ROOT) :server:smp:generateLicenseReport --no-daemon
	@echo "  Report: server/smp/build/reports/dependency-licence/dependency-licence.txt"

# ═══════════════════════════════════════════════════════════════
# CI / VALIDATION
# ═══════════════════════════════════════════════════════════════

.PHONY: ci-local ci-full ci

ci-local:
	@echo "══════════════════════════════════════════════"
	@echo "  CI Pipeline Simulation"
	@echo "══════════════════════════════════════════════"
	@echo ""
	@echo "▸ Gitleaks (secrets scan)..."
	gitleaks protect --staged --redact --exit-code 1 --config .gitleaks.toml
	@echo ""
	@echo ""
	@echo "▸ Markdown lint..."
	$(MAKE) docs-lint
	@echo ""
	@echo "▸ Dependency licence scan..."
	$(MAKE) licence-check
	@echo ""
	@echo "▸ Marketing: Biome lint..."
	cd $(FRONTEND_DIR) && pnpm lint
	@echo ""
	@echo "▸ App: Biome lint..."
	cd $(APP_DIR) && pnpm lint
	@echo ""
	@echo "▸ App: unit tests..."
	cd $(APP_DIR) && pnpm test:run
	@echo ""
	@echo "▸ App: production build..."
	cd $(APP_DIR) && pnpm build
	@echo ""
	@echo "▸ Admin: Biome lint..."
	cd $(ADMIN_DIR) && pnpm lint
	@echo ""
	@echo "▸ Admin: unit tests..."
	cd $(ADMIN_DIR) && pnpm test:run
	@echo ""
	@echo "▸ Admin: production build..."
	cd $(ADMIN_DIR) && pnpm build
	@echo ""
	@echo "▸ Frontend: unit tests + coverage..."
	cd $(FRONTEND_DIR) && pnpm test:coverage
	@echo ""
	@echo "▸ Frontend: build..."
	cd $(FRONTEND_DIR) && pnpm build
	@echo ""
	@echo "▸ Backend: Detekt static analysis..."
	$(GRADLE_ROOT) :server:smp:detekt --no-daemon
	@echo ""
	@echo "▸ Backend: unit tests (fast)..."
	node scripts/gradle-run.mjs :server:smp:test --no-daemon -PexcludeTags=modularity,postgres
	@echo ""
	@echo "▸ Backend: build..."
	node scripts/gradle-run.mjs :server:smp:assemble --no-daemon
	@echo ""
	@echo "══════════════════════════════════════════════"
	@echo "  ✅ CI Pipeline Simulation Complete"
	@echo "══════════════════════════════════════════════"

ci-full: infra-up
	$(MAKE) ci-local
	@echo ""
	@echo "▸ Backend: Postgres integration suite..."
	node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
	@echo ""
	@echo "▸ Backend: Postgres BDD suite..."
	node scripts/gradle-run.mjs :server:smp:bddPostgresTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
	@echo ""
	@echo "══════════════════════════════════════════════"
	@echo "  ✅ Full CI Suite Complete (incl. Postgres)"
	@echo "══════════════════════════════════════════════"

ci:
	@echo "════════════════════════════════════════════════"
	@echo "  🚀 Full CI Pipeline (fail-fast)"
	@echo "════════════════════════════════════════════════"
	@echo ""
	@node scripts/ci-step.mjs "[1/15] Gitleaks (secrets scan)" "." gitleaks protect --staged --redact --exit-code 1 --config .gitleaks.toml
	@echo ""
	@echo "▸ [1b/8] Dependency licence scan..."
	$(MAKE) licence-check
	@echo ""
	@node scripts/ci-step.mjs "[3/15] Marketing: Biome lint" "$(FRONTEND_DIR)" pnpm lint
	@echo ""
	@node scripts/ci-step.mjs "[4/15] App: Biome lint" "$(APP_DIR)" pnpm lint
	@node scripts/ci-step.mjs "[5/15] App: unit tests" "$(APP_DIR)" pnpm test:run
	@node scripts/ci-step.mjs "[6/15] App: production build" "$(APP_DIR)" pnpm build
	@echo ""
	@node scripts/ci-step.mjs "[7/15] Admin: Biome lint" "$(ADMIN_DIR)" pnpm lint
	@node scripts/ci-step.mjs "[8/15] Admin: unit tests" "$(ADMIN_DIR)" pnpm test:run
	@node scripts/ci-step.mjs "[9/15] Admin: production build" "$(ADMIN_DIR)" pnpm build
	@echo ""
	@node scripts/ci-step.mjs "[10/15] Frontend: unit tests + coverage" "$(FRONTEND_DIR)" pnpm test:coverage
	@echo ""
	@node scripts/ci-step.mjs "[11/15] Frontend: production build" "$(FRONTEND_DIR)" pnpm build
	@echo ""
	@node scripts/ci-step.mjs "[12/15] Backend: Detekt static analysis" "." $(GRADLE_ROOT) :server:smp:detekt --no-daemon
	@echo ""
	@node scripts/ci-step.mjs "[13/15] Backend: unit tests (fast)" "." node scripts/gradle-run.mjs :server:smp:test --no-daemon -PexcludeTags=modularity,postgres
	@echo ""
	@node scripts/ci-step.mjs "[14/15] Backend: BDD fast suite" "." node scripts/gradle-run.mjs :server:smp:bddFastTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
	@echo ""
	@node scripts/ci-step.mjs "[15/15] Frontend: E2E tests (Playwright, all browsers)" "$(FRONTEND_DIR)" pnpm test:e2e
	@echo ""
	@echo "════════════════════════════════════════════════"
	@echo "  ✅ Full CI Pipeline Complete — all 15 checks passed"
	@echo "════════════════════════════════════════════════"

# ═══════════════════════════════════════════════════════════════
# CLEANUP
# ═══════════════════════════════════════════════════════════════

.PHONY: clean

clean:
	node scripts/kill-servers.mjs
	node scripts/workspace-clean.mjs
