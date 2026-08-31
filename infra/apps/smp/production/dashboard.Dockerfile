# syntax=docker/dockerfile:1.26@sha256:ecfaec9ed6d810b56388c508f4121597bfbba70d41a6dfeee4d8cad5f295fc32

FROM node:24.19.0-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS build

WORKDIR /workspace

RUN corepack enable && corepack prepare pnpm@11.11.0 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY scripts/prepare-workspace.mjs scripts/prepare-workspace.mjs
COPY apps/web/app/package.json apps/web/app/package.json
COPY shared/web/package.json shared/web/package.json

RUN --mount=type=cache,id=pnpm,target=/pnpm/store \
    pnpm config set store-dir /pnpm/store && \
    pnpm install --frozen-lockfile --filter app...

COPY shared shared
COPY apps/web/app apps/web/app

ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN pnpm --filter app build

FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:901e944d1f4fc2bd077e8f5568b98c1f6f8cdacf6b97a87747c43134a339b9a7 AS runtime

ARG IMAGE_VERSION="dev"
ARG IMAGE_CREATED=""
ARG IMAGE_REVISION=""
LABEL org.opencontainers.image.title="Profile Tailors Dashboard" \
      org.opencontainers.image.description="Web dashboard for scheduling, publishing, analyzing, and collaborating across social networks with Profile Tailors." \
      org.opencontainers.image.url="https://profiletailors.com" \
      org.opencontainers.image.source="https://github.com/dallay/profiletailors.com" \
      org.opencontainers.image.documentation="https://github.com/dallay/profiletailors.com/tree/main/apps/web/app" \
      org.opencontainers.image.version=$IMAGE_VERSION \
      org.opencontainers.image.revision=$IMAGE_REVISION \
      org.opencontainers.image.created=$IMAGE_CREATED \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.authors="Dallay" \
      org.opencontainers.image.vendor="Dallay"

COPY infra/apps/smp/production/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=101:101 /workspace/apps/web/app/dist /usr/share/nginx/html

USER 101:101

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
