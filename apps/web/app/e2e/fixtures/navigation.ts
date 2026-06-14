/**
 * WebKit-safe page navigation helper.
 *
 * WebKit throws "Navigation to X is interrupted by another navigation to X"
 * when the SPA route guard fires a redirect during the initial page load.
 * This helper catches the interruption error and waits for the page to settle.
 *
 * @see https://github.com/microsoft/playwright/issues/13037
 */
import type { Page, PageGoToOptions } from '@playwright/test'

/**
 * WebKit-safe page navigation helper.
 *
 * Chrome and Firefox handle SPA route guard redirects during `page.goto`
 * gracefully, but WebKit throws "interrupted by another navigation".
 * This wrapper catches that error and waits for the page to settle.
 *
 * Use for ALL direct `page.goto()` calls in tests — it's a no-op on
 * Chromium/Firefox (the try always succeeds) and prevents flaky WebKit
 * failures on SPA apps.
 */
export async function safeGoto(page: Page, url: string, options?: PageGoToOptions): Promise<void> {
  try {
    await page.goto(url, options)
  } catch {
    // WebKit: the SPA route guard interrupted the initial navigation.
    // The page is already at the final URL. Wait for load to complete.
    await page.waitForLoadState('load').catch(() => {})
  }
}
