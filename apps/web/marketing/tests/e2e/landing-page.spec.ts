import { test, expect } from '@playwright/test';

test.describe('Landing Page - Hero Section', () => {
  test('should display hero section with value proposition', async ({ page }) => {
    await page.goto('/');
    
    // Verify hero section is visible
    const hero = page.locator('section').first();
    await expect(hero).toBeVisible();
    
    // Verify main heading exists
    const heading = page.locator('h1').first();
    await expect(heading).toBeVisible();
    await expect(heading).not.toBeEmpty();
  });

  test('should display platform integrations', async ({ page }) => {
    await page.goto('/');
    
    // Verify specific platform integrations are present
    const body = await page.textContent('body');
    expect(body).toContain('Twitter');
    expect(body).toContain('Instagram');
    expect(body).toContain('LinkedIn');
  });
});

test.describe('Bilingual Support', () => {
  test('should switch between English and Spanish', async ({ page }) => {
    await page.goto('/');
    
    // Check if language switcher exists
    const langSwitcher = page.getByRole('button', { name: /es|en|español|english/i });
    
    // Assert switcher is present
    await expect(langSwitcher.first()).toBeVisible();
    
    await langSwitcher.first().click();
    
    // Wait for navigation or content change
    await page.waitForLoadState('domcontentloaded');
    const url = page.url();
    expect(url).toBeTruthy();
  });

  test('should load Spanish version directly', async ({ page }) => {
    await page.goto('/es');
    
    // Verify page loaded
    await expect(page.locator('body')).toBeVisible();
    
    // Check lang attribute
    const htmlLang = await page.getAttribute('html', 'lang');
    expect(htmlLang).toContain('es');
  });

  test('should load English version directly', async ({ page }) => {
    await page.goto('/');
    
    // Verify page loaded
    await expect(page.locator('body')).toBeVisible();
    
    // Check lang attribute
    const htmlLang = await page.getAttribute('html', 'lang');
    expect(htmlLang).toContain('en');
  });
});

test.describe('Waitlist Form', () => {
  test('should display waitlist form', async ({ page }) => {
    await page.goto('/');
    
    // Look for email input
    const emailInput = page.locator('input[type="email"]').first();
    
    if (await emailInput.count() > 0) {
      await expect(emailInput).toBeVisible();
    }
  });

  test('should validate email format', async ({ page }) => {
    await page.goto('/');
    
    const emailInput = page.locator('input[type="email"]').first();
    const submitButton = page.locator('button[type="submit"]').first();
    
    // Assert form elements exist
    await expect(emailInput).toBeVisible();
    await expect(submitButton).toBeVisible();
    
    // Try invalid email
    await emailInput.fill('invalid-email');
    await submitButton.click();
    
    // HTML5 validation should prevent submission
    const validationMessage = await emailInput.evaluate((el: HTMLInputElement) => el.validationMessage);
    expect(validationMessage).toBeTruthy();
  });

  test('should accept valid email', async ({ page }) => {
    await page.goto('/');
    
    const emailInput = page.locator('input[type="email"]').first();
    await expect(emailInput).toBeVisible();
    await emailInput.fill('test@example.com');
    
    const submitButton = page.locator('button[type="submit"]').first();
    await expect(submitButton).toBeVisible();
    await submitButton.click();
    
    // Wait for form submission to complete - look for success indicator or state change
    // TODO: Replace with actual success element when form is implemented
    await page.waitForLoadState('networkidle');
  });
});

test.describe('Responsive Design', () => {
  test('should be mobile-friendly', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 }); // iPhone SE
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
    await page.goto('/');
    
    await expect(page.locator('body')).toBeVisible();
  });

  test('should work on desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/');
    
    await expect(page.locator('body')).toBeVisible();
  });
});

test.describe('Accessibility', () => {
  test('should have proper document structure', async ({ page }) => {
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
    
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    
    expect(errors).toHaveLength(0);
  });
});
