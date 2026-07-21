# Autonomous Repository Maintenance

## Purpose

This is the internal control plane for scheduled autonomous maintenance agents. It is outside docs because docs serves contributors, users, operators, and public documentation.

## Directory Structure

.agents/automation/
- README.md: orientation and scheduler entry point.
- framework.md: mandatory shared execution contract.
- tasks/: 20 task definitions.
- state/: 20 task-owned YAML state files.
- reports/: 20 task-owned operational reports.

## Lifecycle, Ownership, Results, and Risk

Every run inspects, verifies, detects drift, classifies risk, makes the smallest safe correction, validates, self-corrects or reverts, updates state/report, commits, pushes, and creates one Draft PR. No-op runs are observable. Tasks own only matching state/report; cross-task artifacts are signals needing independent verification. Standard results are CHANGES_APPLIED, NO_DRIFT_DETECTED, PARTIALLY_COMPLETED, and BLOCKED. Risk boundaries are defined by framework.md.

## Adding a Task

Add a lowercase kebab-case definition, matching empty state/report, exclusive ownership, evidence, deterministic rules, risk boundaries, validation, and Draft PR completion.

## Scheduler Prompt

Execute the autonomous repository maintenance task defined in:
.agents/automation/tasks/<task-name>.md
You must follow the shared automation framework defined in:
.agents/automation/framework.md
Read both files before repository work. Operate autonomously. Do not ask for confirmation, approval, or implementation decisions. Complete state/report updates, validation, commit, push, and Draft Pull Request creation.
