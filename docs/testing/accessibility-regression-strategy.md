# Accessibility Regression Test Strategy

## Overview

This document defines how accessibility regressions are detected and prevented across
the Profile Tailors marketing site and dashboard SPA. It complements the
[accessibility statement](/accessibility/) published at profiletailors.com.

---

## 1. Layer Model

Accessibility testing operates in three layers. Each layer catches a different class of
defect. **No single layer is sufficient on its own.**

| Layer                          | Tool                    | What it catches                                                                               | When it runs                               |
| ------------------------------ | ----------------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------ |
| **Automated (axe)**            | `@axe-core/playwright`  | ~30–40 % of WCAG issues — missing labels, contrast failures, invalid ARIA, landmark structure | Every PR, CI pipeline                      |
| **Keyboard-only walkthroughs** | Playwright + manual     | Focus order, focus traps, skip links, modal open/close, calendar navigation                   | Pre-release & after modal/calendar changes |
| **Screen-reader testing**      | Manual (NVDA/VoiceOver) | Reading order, dynamic content announcements, form instructions, live regions                 | Before each minor release                  |

---

## 2. Automated Checks (CI)

### Location

| App                  | Spec file                         | Fixture                                     |
| -------------------- | --------------------------------- | ------------------------------------------- |
| `apps/web/app`       | `e2e/specs/accessibility.spec.ts` | `e2e/fixtures/a11y-fixture.ts`              |
| `apps/web/marketing` | `tests/e2e/accessibility.spec.ts` | `@playwright/test` + `@axe-core/playwright` |

### Tags

All accessibility specs carry `@a11y`. The app specs additionally carry `@frontend` (no
auth needed) or `@integration` (HAR-replayed auth).

To run only accessibility checks locally:

```sh
# Marketing
pnpm --filter marketing test:e2e -- --grep @a11y

# App
npx playwright test -c e2e/playwright.config.ts --grep @a11y
```

### WCAG rule set

Both suites target:

```
wcag2a  wcag2aa  wcag21a  wcag21aa  wcag22aa
```

### Disabled rules

| Rule                | Reason                                                                     |
| ------------------- | -------------------------------------------------------------------------- |
| `aria-hidden-focus` | reka-ui animates dialogs out while focus is still inside; tracked upstream |

Any additional rule suppressions **must** be documented in the spec file with a comment
linking to an issue or upstream bug.

### CI enforcement

The `frontend-test-e2e` just recipe runs both app and marketing E2E suites. Accessibility
specs are included. Failures block merge.

---

## 3. Keyboard-Only Journey Checklist

Run this checklist after every change to modals, calendar, or navigation components.
Mark each item Pass / Fail / N/A. Attach a screenshot of failing states to the issue.

### Marketing

- [ ] Tab order on landing page is logical (skip link → nav → hero → waitlist form → footer)
- [ ] Skip link appears on first Tab and moves focus to `#main-content`
- [ ] Waitlist form: email → submit reachable with Tab, submit activates with Enter/Space
- [ ] Consent banner: Accept All / Reject All reachable with Tab, activates with Enter/Space
- [ ] No focus trap outside intentional modal contexts

### App — Login / Registration

- [ ] Email → Password → Submit tab order
- [ ] Password toggle (if visible) is reachable with Tab and activates with Enter/Space
- [ ] Checkbox fields (age eligibility, terms) reachable with Tab, toggleable with Space
- [ ] Error messages announced — `role="alert"` or `aria-live="assertive"` present

### App — Scheduler / Calendar

- [ ] "New Post" button reachable from keyboard
- [ ] Week view time slots: Tab moves between interactive slots without trapping
- [ ] View switcher (Week / Month / List) keyboard-operable
- [ ] Date navigation (previous/next) keyboard-operable
- [ ] Focus does not leave the page boundary when inside the calendar

### App — Compose Modal

- [ ] Focus moves into modal on open (first focusable element receives focus)
- [ ] Focus is trapped inside modal while open (Tab cycles within the modal)
- [ ] Escape key closes modal and returns focus to the trigger button
- [ ] All form fields (text area, schedule tabs, date/time picker) reachable with Tab
- [ ] Submit and Cancel buttons reachable with Tab, activate with Enter/Space

### App — Media Picker

- [ ] Upload button reachable with Tab
- [ ] Media grid items are focusable and selectable with Enter/Space
- [ ] Action menu (if present) reachable with keyboard
- [ ] Modal (when open) follows same focus-trap pattern as compose modal

### App — Account Settings

- [ ] All form inputs reachable with Tab
- [ ] Save / Delete / Danger-zone actions keyboard-operable
- [ ] Confirmation dialogs follow focus-trap pattern

---

## 4. Screen-Reader Test Protocol

Run before each minor release with NVDA on Firefox (Windows) and VoiceOver on Safari (macOS).

1. **Page landmark structure** — verify `<header>`, `<nav>`, `<main>`, `<footer>` are
   announced with correct roles.
2. **Headings** — verify heading hierarchy is logical (h1 → h2 → h3, no skipped levels).
3. **Form fields** — each input/select/checkbox must have an associated label announced by
   the screen reader.
4. **Error messages** — trigger a validation error and confirm the message is announced
   immediately without page reload (live region or focus move).
5. **Modals** — open a modal and confirm the screen reader announces the dialog role and
   its accessible name. Close the modal and confirm focus returns to the trigger.
6. **Calendar** — navigate the scheduler calendar and confirm time slots and navigation
   controls are announced with useful labels.
7. **Dynamic content** — create a post and confirm any success/failure toast is announced.

Document results in the release checklist in `docs/reviews/`.

---

## 5. Accepted Findings

The following issues are known but accepted with documented rationale. They must be
reviewed at each minor release to determine if the acceptance criteria still hold.

| ID       | Description                                            | Severity | Acceptance rationale                                                   | Target fix release |
| -------- | ------------------------------------------------------ | -------- | ---------------------------------------------------------------------- | ------------------ |
| A11Y-001 | Calendar arrow-key navigation not implemented          | Moderate | Workaround (Tab) documented in accessibility statement; sprint planned | v0.5               |
| A11Y-002 | Media picker drag-and-drop has no keyboard alternative | Moderate | Action menu alternative exists and is keyboard-operable                | v0.5               |

Any new `critical` or `serious` axe finding that cannot be fixed in the same sprint **must**
be added to this table with a target release date before merging.

---

## 6. EAA Applicability Assessment

The **European Accessibility Act (EAA / Directive 2019/882)** requires certain digital
products and services provided to EU consumers to meet EN 301 549 (which references
WCAG 2.1 AA as its web content standard).

### Current applicability

| Criterion                                               | Assessment                                                                   |
| ------------------------------------------------------- | ---------------------------------------------------------------------------- |
| Is the product offered to consumers in the EU?          | Yes (early-access preview)                                                   |
| Is it a "consumer e-commerce service" under the EAA?    | **Borderline** — early-access preview with no active commercial transactions |
| Is it a "communication service"?                        | No                                                                           |
| Is it a microenterprise (<10 employees, <€2M turnover)? | Currently yes — monitor as organisation scales                               |
| EAA obligation triggered?                               | **Not yet** — monitor at general availability launch                         |

### Action required at GA

Before Profile Tailors transitions from early-access preview to general availability:

1. Confirm EAA applicability with qualified legal counsel.
2. Conduct a full EN 301 549 / WCAG 2.1 AA audit and remediate critical findings.
3. Update this document and the accessibility statement with confirmed conformance level.
4. Establish a formal feedback mechanism and escalation path as required by EAA Article 13.

---

## 7. Adding New Features

When a new UI feature is introduced:

1. Run `just frontend-test-e2e` — axe checks run automatically.
2. Add one targeted axe test in the relevant spec if the feature introduces a new page or
   significant UI region.
3. Work through the keyboard checklist for any new modal, form, or interactive widget.
4. If an axe violation cannot be fixed immediately, add it to the Accepted Findings table
   above (Section 5) with a target fix release.

---

## 8. References

- [WCAG 2.2](https://www.w3.org/TR/WCAG22/)
- [EN 301 549 v3.2.1](https://www.etsi.org/deliver/etsi_en/301500_302000/301549/03.02.01_60/en_301549v030201p.pdf)
- [EAA Directive 2019/882](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32019L0882)
- [@axe-core/playwright](https://github.com/dequelabs/axe-core-npm/tree/develop/packages/playwright)
- [Accepted Findings tracker](#5-accepted-findings) (this document)
- [Accessibility Statement](https://profiletailors.com/accessibility/)
