# syntax=docker/dockerfile:1.26@sha256:ecfaec9ed6d810b56388c508f4121597bfbba70d41a6dfeee4d8cad5f295fc32
#
# Multi-arch Dockerfile for the Profile Tailors backend (server/smp).
#
# Two-stage build:
#   1. Build the executable jar with Gradle on the host architecture
#      ($BUILDPLATFORM). The jar is platform-independent Java bytecode,
#      so we only need to compile it once even when targeting multiple
#      architectures downstream.
#   2. Copy the jar into a multi-arch Temurin JRE-based runtime image,
#      built for each target architecture in $TARGETPLATFORM. Each
#      variant runs as uid 1002 / gid 1001 (matching the values used
#      in the production docker-compose.yml).
#
# Build:
#   docker buildx build \
#     --platform linux/amd64,linux/arm64 \
#     -t ghcr.io/dallay/profiletailors-smp:tag \
#     --push .

# ── Stage 1: build the executable jar ────────────────────────────────────
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-noble@sha256:35685c7e23352983a48882d97cd9875f5284c228db71d1e2476e5e6c1bab1080 AS builder

WORKDIR /workspace

# Bring the full monorepo into the builder. Gradle needs shared modules,
# the gradle wrapper, settings, and every included project to compile.
COPY . .

RUN --mount=type=cache,id=gradle,target=/root/.gradle/caches \
    --mount=type=cache,id=gradle-2,target=/root/.gradle/wrapper \
    ./gradlew :server:smp:bootJar --no-daemon -x test

# ── Stage 2: multi-arch runtime ───────────────────────────────────────────
# On each target platform the base image is automatically the matching
# variant. Temurin 21-jre-noble ships multi-arch (linux/amd64,
# linux/arm64).
FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre-noble@sha256:ca397720325ceefe39ce397f186759fc87d9efafb2dc4ce53315980844c2f4f2 AS runtime

ARG SMP_VERSION="dev"
LABEL org.opencontainers.image.version=$SMP_VERSION \
      org.opencontainers.image.title="Profile Tailors SMP" \
      org.opencontainers.image.source="https://github.com/dallay/profiletailors.com" \
      org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app

# Copy the executable jar built by the Spring Boot Gradle `bootJar`
# task. bootJar names the artifact `smp-<version>.jar`; the `jar` task
# also produces `smp-<version>-plain.jar` (no Spring Boot loader), so
# we select the executable and ignore the plain variant.
COPY --from=builder --chown=1002:1001 \
    /workspace/server/smp/build/libs/ /tmp/smp-jars/
RUN set -e; \
    jar=$(ls /tmp/smp-jars/smp-*.jar | grep -v '\-plain\.jar$' | head -n 1); \
    cp "$jar" /app/app.jar

# Read-only runtime: all writes go to tmpfs mounts at /tmp and the
# storage volume mounted at /var/lib/profiletailors/media. The entrypoint
# runs as uid 1002 / gid 1001 to match the production docker-compose.
RUN chown -R 1002:1001 /app
USER 1002:1001

EXPOSE 7638 9091

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SMP_BACKEND_PORT=7638 \
    MANAGEMENT_PORT=9091

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=60s \
    CMD wget -q -O - http://127.0.0.1:9091/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
