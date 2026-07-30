# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM node:24.16.0-alpine@sha256:21f403ab171f2dc89bad4dd69d7721bfd15f084ccb46cdd225f31f2bc59b5c9a AS build

WORKDIR /workspace

RUN corepack enable && corepack prepare pnpm@11.11.0 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/app/package.json apps/web/app/package.json

RUN --mount=type=cache,id=pnpm,target=/pnpm/store \
    pnpm config set store-dir /pnpm/store && \
    pnpm install --frozen-lockfile --filter app...

COPY shared shared
COPY apps/web/app apps/web/app

ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN pnpm --filter app build

FROM nginxinc/nginx-unprivileged:1.29-alpine@sha256:0c79d56aee561a1d81c63f00eee5fb5fe29279560cdc55e91425133104c7fbe6 AS runtime

COPY infra/apps/smp/production/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=101:101 /workspace/apps/web/app/dist /usr/share/nginx/html

USER 101:101

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
