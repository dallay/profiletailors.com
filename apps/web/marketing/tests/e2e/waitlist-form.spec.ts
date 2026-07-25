import { test, expect } from '@bgotink/playwright-coverage';
import type { Page, Route, Request } from '@playwright/test';

const WAITLIST_KEY = 'profile-tailors-launch';

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
      source: 'marketing-site',
      formId: 'waitlist-hero',
      locale: 'en',
      consent: { earlyAccess: true, marketing: false },
    });
  });

  test('blocks submission when the email is empty', async ({ page }: { page: Page }): Promise<void> => {
    let posted = false;
    await page.route('**/api/waitlists/**/entries', async (route: Route, request: Request): Promise<void> => {
      if (request.method() === 'POST') posted = true;
      await route.fallback();
    });

    await page.goto('/');
    const form = page.locator('[data-waitlist-form]').first();
    await expect(form).toBeVisible();

    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    const error = page.locator('[data-waitlist-error]').first();
    await expect(error).toBeVisible();
    await expect(error).toContainText(/valid email/i);

    expect(posted).toBe(false);
  });

  test('blocks submission when email format is invalid', async ({ page }: { page: Page }): Promise<void> => {
    let posted = false;
    await page.route('**/api/waitlists/**/entries', async (route: Route, request: Request): Promise<void> => {
      if (request.method() === 'POST') posted = true;
      await route.fallback();
    });

    await page.goto('/');
    await page.locator('[data-waitlist-email]').first().fill('not-an-email');
    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    const error = page.locator('[data-waitlist-error]').first();
    await expect(error).toBeVisible();
    await expect(error).toContainText(/valid email/i);

    expect(posted).toBe(false);
  });

  test('blocks submission when early-access consent is missing', async ({ page }: { page: Page }): Promise<void> => {
    let posted = false;
    await page.route('**/api/waitlists/**/entries', async (route: Route, request: Request): Promise<void> => {
      if (request.method() === 'POST') posted = true;
      await route.fallback();
    });

    await page.goto('/');
    await page.locator('[data-waitlist-email]').first().fill('user@example.com');
    await page.locator('[data-waitlist-submit]').first().click();

    const error = page.locator('[data-waitlist-error]').first();
    await expect(error).toBeVisible();
    await expect(error).toContainText(/early-access consent/i);

    expect(posted).toBe(false);
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
        body: JSON.stringify({ status: 'rate_limited' }),
      });
    });

    await page.goto('/');
    await page.locator('[data-waitlist-email]').first().fill('user@example.com');
    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    const error = page.locator('[data-waitlist-error]').first();
    await expect(error).toBeVisible();
    await expect(error).toContainText(/too many/i);
  });

  test('submits against the configured API base and waitlist key', async ({ page }: { page: Page }): Promise<void> => {
    let requestUrl: string | null = null;
    await page.route('**/api/waitlists/**/entries', async (route: Route, request: Request): Promise<void> => {
      if (request.method() !== 'POST') {
        await route.fallback();
        return;
      }
      requestUrl = request.url();
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'accepted' }),
      });
    });

    await page.goto('/');
    await page.locator('[data-waitlist-email]').first().fill('user@example.com');
    await page.locator('[data-waitlist-consent-early]').first().check();
    await page.locator('[data-waitlist-submit]').first().click();

    await expect(page.locator('[data-waitlist-success]').first()).toBeVisible();
    if (!requestUrl) throw new Error('requestUrl was not captured');
    const parsed = new URL(requestUrl);
    expect(parsed.pathname).toBe(`/api/waitlists/${WAITLIST_KEY}/entries`);
    expect(parsed.host).toBe('localhost:7638');
  });
});
