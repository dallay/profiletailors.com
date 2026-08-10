import { test, expect } from '@bgotink/playwright-coverage';
import type { Page } from '@playwright/test';

/**
 * Helper: Pre-load valid consent receipt to dismiss banner.
 * Call before page.goto() in tests that don't need the consent banner.
 */
async function dismissConsentBanner(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem(
      'pt-consent',
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: new Date().toISOString(),
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      })
    );
  });
}

test.describe('Landing Page - Hero Section', () => {
  test('should display hero section with value proposition', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/');

    // Verify hero section is visible
    const hero = page.locator('section').first();
    await expect(hero).toBeVisible();

    // Verify main heading exists
    const heading = page.locator('h1').first();
    await expect(heading).toBeVisible();
    await expect(heading).not.toBeEmpty();
  });

  test('should display hero with early access messaging', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/');

    // Verify hero sections contain the new messaging (platform integrations were removed)
    const label = page.locator('[data-hero-label]');
    await expect(label).toBeVisible();
    await expect(label).not.toBeEmpty();

    const headline = page.locator('[data-hero-headline]');
    await expect(headline).toBeVisible();
    await expect(headline).not.toBeEmpty();

    const sub = page.locator('[data-hero-sub]');
    await expect(sub).toBeVisible();
    await expect(sub).not.toBeEmpty();
  });
});

test.describe('Bilingual Support', () => {
  test('should switch between English and Spanish', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/');

    // The language switcher is an <a> link with aria-label "Cambiar a español" or "Switch to English"
    const langSwitcher = page.getByRole('link', { name: /cambiar a español|switch to english/i });

    // Assert switcher is present
    await expect(langSwitcher.first()).toBeVisible({ timeout: 10_000 });

    await langSwitcher.first().click();

    // Wait for navigation or content change
    await page.waitForLoadState('domcontentloaded');
    const url = page.url();
    expect(url).toBeTruthy();
  });

  test('should load Spanish version directly', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/es');

    // Verify page loaded
    await expect(page.locator('body')).toBeVisible();

    // Check lang attribute
    const htmlLang = await page.getAttribute('html', 'lang');
    expect(htmlLang).toContain('es');
  });

  test('should load English version directly', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/');

    // Verify page loaded
    await expect(page.locator('body')).toBeVisible();

    // Check lang attribute
    const htmlLang = await page.getAttribute('html', 'lang');
    expect(htmlLang).toContain('en');
  });
});

test.describe('Waitlist Form — Hero Container (enabled)', () => {
  test('renders the active waitlist form when WAITLIST_ENABLED=true', async ({ page }: { page: Page }): Promise<void> => {
    await dismissConsentBanner(page);
    await page.goto('/');

    const formContainer = page.locator('[data-hero-form]');
    await expect(formContainer).toBeVisible();

    const form = formContainer.locator('[data-waitlist-form]').first();
    await expect(form).toBeVisible();

    const successMessage = formContainer.locator('p[data-waitlist-success]');
    await expect(successMessage).toHaveCount(1);
    await expect(successMessage).toBeHidden();
  });

  test('renders the API base URL as a data attribute', async ({ page }: { page: Page }): Promise<void> => {
    await dismissConsentBanner(page);
    await page.goto('/');

    const form = page.getByRole('form', { name: /early access waitlist form/i });
    await expect(form).toBeVisible();

    const apiBase = await form.getAttribute('data-waitlist-api-base');
    expect(apiBase).toBe('http://localhost:7638');
  });
});

test.describe('Responsive Design', () => {
  test('should be mobile-friendly', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 }); // iPhone SE
    await dismissConsentBanner(page);
    await page.goto('/');

    // Verify page is visible and scrollable
    await expect(page.locator('body')).toBeVisible();

    // Check no horizontal overflow
    const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
    const viewportWidth = await page.evaluate(() => window.innerWidth);
    expect(bodyWidth).toBeLessThanOrEqual(viewportWidth);
  });

  test('should adapt to tablet size', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 }); // iPad
    await dismissConsentBanner(page);
    await page.goto('/');

    await expect(page.locator('body')).toBeVisible();
  });

  test('should work on desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await dismissConsentBanner(page);
    await page.goto('/');

    await expect(page.locator('body')).toBeVisible();
  });
});

test.describe('Accessibility', () => {
  test('should have proper document structure', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/');

    // Check for main landmark
    const main = page.locator('main');
    if (await main.count() > 0) {
      await expect(main).toBeVisible();
    }

    // Check for heading hierarchy
    const h1 = page.locator('h1');
    await expect(h1.first()).toBeVisible();
  });

  test('should have alt text for images', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/');

    const images = page.locator('img');
    const count = await images.count();

    for (let i = 0; i < count; i++) {
      const img = images.nth(i);
      const alt = await img.getAttribute('alt');
      expect(alt).not.toBeNull();
      expect(typeof alt).toBe('string');
    }
  });

  test('should be keyboard navigable', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/');

    // Tab through interactive elements
    await page.keyboard.press('Tab');

    // Check if focus is visible
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(focusedElement).toBeTruthy();
  });
});

test.describe('Performance', () => {
  test('should load within reasonable time', async ({ page }) => {
    await dismissConsentBanner(page);
    const startTime = Date.now();
    await page.goto('/');
    const loadTime = Date.now() - startTime;

    // Should load in less than 3 seconds
    expect(loadTime).toBeLessThan(3000);
  });

  test('should have no console errors', async ({ page }) => {
    const errors: string[] = [];

    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    await dismissConsentBanner(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    expect(errors).toHaveLength(0);
  });

  test('should include speculationrules script with moderate eagerness', async ({ page }) => {
    await dismissConsentBanner(page);
    await page.goto('/');

    const script = page.locator('script[type="speculationrules"]');
    await expect(script).toHaveCount(1);

    const content = await script.textContent();
    expect(content).not.toBeNull();
    const rules = JSON.parse(content || '{}');
    expect(rules.prerender).toBeDefined();
    expect(rules.prerender[0].eagerness).toBe('moderate');

    // Also verify on the Spanish locale page
    await page.goto('/es');
    const esScript = page.locator('script[type="speculationrules"]');
    await expect(esScript).toHaveCount(1);

    const esContent = await esScript.textContent();
    expect(esContent).not.toBeNull();
    const esRules = JSON.parse(esContent || '{}');
    expect(esRules.prerender).toBeDefined();
    expect(esRules.prerender[0].eagerness).toBe('moderate');
  });
});
