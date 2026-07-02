# Actuator Security Configuration

**Date:** 2026-05-25  
**Status:** ✅ Completed

## Overview

Spring Boot Actuator endpoints often expose sensitive information about application internals,
environment variables, and performance. This document outlines the security strategy implemented to
protect these endpoints in the Profile Tailors monorepo.

## Security Strategy: Layered Access

We use a **Port Separation** strategy combined with **Spring Security** rules to ensure maximum
safety.

### 1. Port Separation

In production environments, Actuator is configured to run on a dedicated management port (`9091`),
while the public API remains on port `7638`.

- **Port 7638**: Public traffic. Only the basic health status is exposed.
- **Port 9091**: Internal traffic. Accessible only by Prometheus and operators within the internal
  network (VPC/VPN).

### 2. Spring Security Configuration

The `IdentitySecurityConfiguration.kt` file defines granular access rules:

```kotlin
.authorizeExchange {
    // Only basic health status is public
    it.pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
    
    // Everything else requires authentication
    it.anyExchange().authenticated()
}
```

## Environment Specifics

### Development (profile: `dev`)

- **Management Port**: Dedicated (`9091`) to match production posture.
- **Security**: All endpoints are accessible locally for easier debugging.
- **Configuration**: `management.server.port: 9091`

### Production (default)

- **Management Port**: Dedicated (`9091`).
- **Security**: Port `9091` must be blocked at the firewall level for external traffic.
- **Configuration**: `management.server.port: 9091`

## Risk Matrix

| Endpoint                | Information Exposed                 | Risk Level | Recommendation   |
|:------------------------|:------------------------------------|:-----------|:-----------------|
| `/actuator/health`      | Status UP/DOWN                      | Low        | ✅ Public OK      |
| `/actuator/prometheus`  | Performance metrics, internal paths | High       | 🔒 Internal Only |
| `/actuator/metrics`     | Detailed metrics                    | High       | 🔒 Internal Only |
| `/actuator/info`        | Build version, dependencies         | Medium     | 🔒 Internal Only |
| `/actuator/env`         | Environment variables (secrets!)    | Critical   | ❌ Never Expose   |
| `/actuator/configprops` | Detailed configuration              | Critical   | ❌ Never Expose   |

## Firewall & Infrastructure Rules

### AWS Security Groups

- **Inbound 7638**: `0.0.0.0/0` (Public)
- **Inbound 9091**: `10.0.0.0/8` (Internal VPC Only)

### Docker Networks

Services are isolated using Docker networks. Prometheus and SMP share the `monitoring` network where
port `9091` is reachable.

## Verification

### External Check (Internet)

```bash
# Should return only {"status":"UP"}
curl https://api.profiletailors.com/actuator/health

# Should timeout or return 403/404
curl https://api.profiletailors.com/actuator/prometheus
```

### Internal Check (VPN/SSH)

```bash
# Should return full metrics
curl http://smp-internal:9091/actuator/prometheus
```

## References

- [Spring Boot Management Port Documentation](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html#actuator.monitoring.customizing-management-server-port)
- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
