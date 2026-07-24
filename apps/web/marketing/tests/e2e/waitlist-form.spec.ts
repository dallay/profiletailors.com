import { test, expect } from '@bgotink/playwright-coverage';
import type { Page, Route, Request } from '@playwright/test';

const WAITLIST_KEY = 'profile-tailors-launch';

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

test.describe('Waitlist Form — Marketing E2E', () => {
  test('submits successfully when the backend responds 202', async ({ page }: { page: Page }): Promise<void> => {
    let interceptedBody: unknown = null;

    await page.route('**/api/waitlists/**/entries', async (route: Route, request: Request): Promise<void> => {
      if (request.method() !== 'POST') {
        await route.fallback();
        return;
      }
      const raw = request.postData() ?? '{}';
      interceptedBody = JSON.parse(raw);
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'accepted', message: "You're on the waitlist" }),
      });
    });

    await dismissConsentBanner(page);
    await page.goto('/');

    const form = page.locator('[data-waitlist-form]').first();
    await expect(form).toBeVisible();

    await page.locator('[data-waitlist-email]').first().fill('user@example.com');
    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    const success = page.locator('[data-waitlist-success]').first();
    await expect(success).toBeVisible();
    await expect(success).toContainText("You're on the list");

    expect(interceptedBody).toMatchObject({
      email: 'user@example.com',
      consents: {
        earlyAccess: true,
        marketing: false,
      },
    });
  });

  test('blocks submission when the email is empty', async ({ page }: { page: Page }): Promise<void> => {
    await dismissConsentBanner(page);
    await page.goto('/');

    const form = page.locator('[data-waitlist-form]').first();
    await expect(form).toBeVisible();

    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    const error = page.locator('[data-waitlist-error]').first();
    await expect(error).toBeVisible();
    await expect(error).toContainText('Please enter a valid email');
  });

  test('blocks submission when email format is invalid', async ({ page }: { page: Page }): Promise<void> => {
    await dismissConsentBanner(page);
    await page.goto('/');

    const form = page.locator('[data-waitlist-form]').first();
    await expect(form).toBeVisible();

    await page.locator('[data-waitlist-email]').first().fill('invalid-email');
    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    const error = page.locator('[data-waitlist-error]').first();
    await expect(error).toBeVisible();
    await expect(error).toContainText('Please enter a valid email');
  });

  test('blocks submission when early-access consent is missing', async ({ page }: { page: Page }): Promise<void> => {
    await dismissConsentBanner(page);
    await page.goto('/');

    const form = page.locator('[data-waitlist-form]').first();
    await expect(form).toBeVisible();

    await page.locator('[data-waitlist-email]').first().fill('user@example.com');
    await page.locator('[data-waitlist-submit]').first().click();

    const error = page.locator('[data-waitlist-error]').first();
    await expect(error).toBeVisible();
    await expect(error).toContainText('Early-access consent is required');
  });

  test('shows friendly message when the backend returns 429', async ({ page }: { page: Page }): Promise<void> => {
    await page.route('**/api/waitlists/**/entries', async (route: Route, request: Request): Promise<void> => {
      if (request.method() !== 'POST') {
        await route.fallback();
        return;
      }
      await route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Rate limit exceeded' }),
      });
    });

    await dismissConsentBanner(page);
    await page.goto('/');

    await page.locator('[data-waitlist-email]').first().fill('user@example.com');
    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    const error = page.locator('[data-waitlist-error]').first();
    await expect(error).toBeVisible();
    await expect(error).toContainText('Too many requests');
  });

  test('submits against the configured API base and waitlist key', async ({ page }: { page: Page }): Promise<void> => {
    let capturedUrl = '';

    await page.route('**/api/waitlists/**/entries', async (route: Route, request: Request): Promise<void> => {
      capturedUrl = request.url();
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'accepted' }),
      });
    });

    await dismissConsentBanner(page);
    await page.goto('/');
    await page.locator('[data-waitlist-email]').first().fill('user@example.com');
    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    await expect(page.locator('[data-waitlist-success]').first()).toBeVisible();

    expect(capturedUrl).toContain('/api/waitlists/');
    expect(capturedUrl).toContain(`/${WAITLIST_KEY}/entries`);
  });
});
