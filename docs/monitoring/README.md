# Monitoring

## Overview

Metrics, dashboards, and actuator hardening for local and production observability.

## Quick path

1. Setup: [Prometheus and Grafana Setup](./prometheus-grafana-setup.md)
2. Harden: [Actuator Security](./actuator-security.md)
3. Contracts: [Observability Contracts](../observability-contracts.md)

## Usage

- [Prometheus and Grafana Setup](./prometheus-grafana-setup.md) — metrics collection and visualization
- [Actuator Security](./actuator-security.md) — securing Spring Boot Actuator endpoints
- Shared contracts: [Observability Contracts](../observability-contracts.md) and [Observability Usage](../observability-usage.md)

## Troubleshooting

- If dashboards are empty, verify the app exposes actuator endpoints per `actuator-security.md` before changing Prometheus targets.
- Keep alert thresholds in version control; do not tune them only in the Grafana UI.

## References

- [Docs index](../README.md)
- [Infrastructure](../infrastructure/README.md)
