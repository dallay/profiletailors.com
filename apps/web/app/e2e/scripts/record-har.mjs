#!/usr/bin/env node
/**
 * Record API responses into the HAR file using Playwright's
 * built-in recordHar feature on the browser context.
 *
 * Usage: node scripts/record-har.mjs
 * Prereq: target API/backend running + Vite dev server on :5173
 *
 * In replay mode, tests use the credentials already captured in the HAR.
 * In record mode, you can override them with E2E_TEST_USER_EMAIL and
 * E2E_TEST_USER_PASSWORD before regenerating the archive.
 */

import { chromium } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { readFileSync } from 'node:fs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const HAR_PATH = path.resolve(__dirname, '../hars/auth-flow.har')
const BASE_URL = 'http://localhost:5173'
const E2E_EMAIL = process.env.E2E_TEST_USER_EMAIL || 'dev@profiletailors.com'
const E2E_PASSWORD = process.env.E2E_TEST_USER_PASSWORD

if (!E2E_PASSWORD) {
  throw new Error(
    'E2E_TEST_USER_PASSWORD environment variable is required. ' +
      'Set it in your shell or CI pipeline before running HAR recording.',
  )
}

async function main() {
  const browser = await chromium.launch({ headless: true })

  // Use recordHar on context creation — captures ALL network traffic
  const context = await browser.newContext({
    recordHar: {
      path: HAR_PATH,
      urlFilter: '**/api/**',
    },
  })

  const page = await context.newPage()

  try {
    // 1. Successful login
    console.log('1. Logging in with valid credentials...')
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' })
    await page.getByLabel(/email/i).fill(E2E_EMAIL)
    await page.getByLabel(/password/i).fill(E2E_PASSWORD)
    await page.getByRole('button', { name: /sign in/i }).click()
    await page.waitForURL('**/')
    await page.waitForTimeout(1000)
    console.log('   Logged in, at:', page.url())

    // 2. Navigate to another authed page
    console.log('2. Navigating to /scheduler...')
    await page.goto(`${BASE_URL}/scheduler`, { waitUntil: 'networkidle' }).catch(() => {})
    await page.waitForTimeout(1000)

    // 3. Logout via API
    console.log('3. Logging out...')
    await page.evaluate(() =>
      fetch('/api/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
      }).catch(() => {}),
    )
    await page.context().clearCookies()
    await page.waitForTimeout(500)

    // 4. Failed login attempt
    console.log('4. Trying failed login...')
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' })
    await page.getByLabel(/email/i).fill('wrong@email.com')
    await page.getByLabel(/password/i).fill('badpassword')
    await page.getByRole('button', { name: /sign in/i }).click()
    await page.waitForTimeout(2000)

    console.log('\nDone!')
  } catch (err) {
    console.error('Error:', err.message)
  } finally {
    // Close context — this flushes the HAR to disk
    await context.close()
    await browser.close()
  }

  // Show HAR summary
  const har = JSON.parse(readFileSync(HAR_PATH, 'utf8'))
  const entries = har.log.entries
  console.log(`HAR entries recorded: ${entries.length}`)
  for (const e of entries) {
    const url = e.request.url
    const p = url.includes('/api') ? `/api${url.split('/api')[1]}` : url
    console.log(`  ${e.request.method} ${p} -> ${e.response.status}`)
  }
}

main().catch(console.error)
