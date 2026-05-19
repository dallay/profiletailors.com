# Profile Tailors — C4 Architecture Models

This directory contains the C4 architecture models for Profile Tailors, a social media management platform.

## Model Hierarchy

The C4 model provides a hierarchical set of architecture diagrams:

1. **[Level 1: System Context](01-system-context.md)** — Shows Profile Tailors in relation to users and external systems
2. **[Level 2: Container](02-container.md)** — Shows the high-level technology choices and how containers communicate
3. **[Level 3: Component](03-component.md)** — Shows the internal structure of key containers
4. **[Level 4: Code](04-code.md)** — Shows implementation details for critical components

## Quick Reference

| Level | Audience | Focus |
|-------|----------|-------|
| Context | Everyone | Big picture, external dependencies |
| Container | Technical leadership | Deployable units, technology stack |
| Component | Developers, architects | Internal structure, bounded contexts |
| Code | Developers | Implementation patterns, class design |

## Notation

All diagrams use [C4 model notation](https://c4model.com/):

- **Person** (blue): Human users
- **Software System** (blue): The system being documented
- **External System** (gray): Third-party systems
- **Container** (blue): Deployable/runnable unit
- **Component** (blue): Grouping of related functionality

## Tooling

Diagrams are written in:
- **PlantUML** with C4-PlantUML extension for rendering
- **Mermaid** for simpler diagrams where appropriate

## Current State

**Status**: Early development (v0.0.1-SNAPSHOT)

**What's implemented**:
- Marketing site (Astro 6, static)
- Backend foundation (Spring Boot 4, Kotlin, WebFlux)
- Core bounded contexts: Identity, Authorization, Tenancy, Credentials, Governance, Platform

**What's planned**:
- Social media integrations (Twitter, LinkedIn, Instagram, Facebook, TikTok)
- Content scheduling and publishing
- Analytics and reporting
- Team collaboration features

---

Last updated: 2026-05-19
