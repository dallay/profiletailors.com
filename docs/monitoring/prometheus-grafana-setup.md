# Prometheus & Grafana Monitoring Setup

**Date:** 2026-05-25  
**Status:** ✅ Completed

## Overview

This document describes the monitoring setup for the Profile Tailors Social Media Platform (SMP)
server using Prometheus and Grafana. The setup provides real-time visibility into application
health, performance metrics, and infrastructure status.

## Architecture

The monitoring infrastructure follows a standard scrape-based architecture:

1. **SMP Server**: Exposes metrics via Spring Boot Actuator at `/actuator/prometheus`.
2. **Prometheus**: Periodically scrapes the SMP server and stores time-series data.
3. **Grafana**: Connects to Prometheus as a data source and provides visual dashboards.

## Changes Made

### 1. Spring Boot Configuration

- Added `io.micrometer:micrometer-registry-prometheus` dependency to `server/smp/build.gradle.kts`.
- Configured Actuator in `application.yaml` to expose Prometheus metrics and health probes.
- Implemented layered security for endpoints (Public health status vs Internal detailed metrics).

### 2. Infrastructure (Docker Compose)

The monitoring stack is defined in `infra/monitoring/compose.yaml`:

- **Prometheus**: Version `v2.51.2`, exposed on port `9090`.
- **Grafana**: Version `10.4.2`, exposed on port `3000`.

## Exposed Endpoints

### Public (Port 7638)

- `GET /actuator/health`: Basic UP/DOWN status for load balancers.

### Internal (Port 9091 in Prod / 9091 in Dev)

- `GET /actuator/prometheus`: Metrics in Prometheus format.
- `GET /actuator/health/liveness`: Kubernetes liveness probe.
- `GET /actuator/health/readiness`: Kubernetes readiness probe.
- `GET /actuator/info`: Application information.
- `GET /actuator/metrics`: Detailed metric listing.

## How to Use

### 1. Start Infrastructure

Prometheus authenticates its SMP scrape with a bearer token. Put only the raw token value (without
the `Bearer` prefix) in a local file, keep the file outside the repository, and export its absolute
path before starting the monitoring stack:

```bash
mkdir -p "$HOME/.config/profiletailors"
install -m 600 /dev/null "$HOME/.config/profiletailors/smp-prometheus-token"
read -rsp "SMP bearer token: " smp_prometheus_bearer_token
printf '%s' "$smp_prometheus_bearer_token" > "$HOME/.config/profiletailors/smp-prometheus-token"
unset smp_prometheus_bearer_token
export SMP_PROMETHEUS_BEARER_TOKEN_FILE="$HOME/.config/profiletailors/smp-prometheus-token"
```

Use a bearer token issued by the SMP local login flow or by its configured JWT issuer. Refresh the
file before that token expires and reload Prometheus with `POST /-/reload` so subsequent scrapes use
the new credential. Never commit the token file.

From the `server/smp` directory:

```bash
docker compose up -d
```

### 2. Start Application

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 3. Access Interfaces

- **Prometheus**: [http://localhost:9090](http://localhost:9090)
- **Grafana**: [http://localhost:3000](http://localhost:3000) (User: `admin`, Password: `admin`)

## Available Metrics

- **HTTP Requests**: Rate, latency (p95, p99), error count.
- **JVM**: Heap/Non-heap memory usage, GC pauses, thread count.
- **System**: CPU usage (process and system), load average.
- **Database**: R2DBC connection pool status (acquired, idle, pending).

## Grafana Dashboards

A pre-configured dashboard **"Spring Boot SMP - Overview"** is automatically provisioned and
includes panels for all key metrics mentioned above.

## Troubleshooting

### Prometheus cannot scrape the server

1. Verify the server is running: `curl http://localhost:7638/actuator/health`
2. Verify the token file contains a current raw bearer token and is mounted at
   `/etc/prometheus/secrets/smp-bearer-token` in the Prometheus container.
3. Check Prometheus targets: [http://localhost:9090/targets](http://localhost:9090/targets)
4. If running inside Docker, ensure the target is set to `host.docker.internal` or the service name.

### Grafana is empty

1. Verify Prometheus has data: `up{job="spring-boot-smp"}` should return `1`.
2. Check the Prometheus Data Source in Grafana settings.

## References

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [Micrometer Prometheus Registry](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)
