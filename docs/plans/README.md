# Plans

## Overview

Point-in-time implementation plans. They record intent and sequence at creation time; execution evidence lives in OpenSpec or `plan/tasks/`, not here.

## Usage

- [SonarQube Batch Remediation](./2026-09-11-sonarqube-batch-remediation.md) — batch remediation sequence

## Troubleshooting

- Do not treat a plan as proof of completion. Check `.agents/sdd/changes/*/verify-report.md` and `state.yaml` for actual status.
- If a plan conflicts with current code, trust code plus tests, then update or supersede the plan.

## References

- [Docs index](../README.md)
- [OpenSpec](../../.agents/sdd/README.md)
