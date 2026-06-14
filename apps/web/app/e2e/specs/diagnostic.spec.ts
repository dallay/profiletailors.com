import { test, expect } from '../fixtures/base-test'

test('DIAGNOSTIC A: WITH clearCookies', async ({ page }) => {
  const responses: Array<{ url: string; status: number }> = []
  page.on('response', (res) => {
    if (res.url().includes('/api/')) {
      responses.push({ url: res.url(), status: res.status() })
    }
  })

  await page.context().clearCookies()
  await page.goto('/scheduler')
  await page.waitForTimeout(2000)

  console.log('\n=== DIAGNOSTIC A (WITH clearCookies) ===')
  console.log('Final URL:', page.url())
  for (const r of responses) {
    console.log(`  [${r.status}] ${r.url}`)
  }

  expect(true).toBe(true)
})

test('DIAGNOSTIC B: WITHOUT clearCookies', async ({ page }) => {
  const responses: Array<{ url: string; status: number }> = []
  page.on('response', (res) => {
    if (res.url().includes('/api/')) {
      responses.push({ url: res.url(), status: res.status() })
    }
  })

  await page.goto('/scheduler')
  await page.waitForTimeout(2000)

  console.log('\n=== DIAGNOSTIC B (WITHOUT clearCookies) ===')
  console.log('Final URL:', page.url())
  for (const r of responses) {
    console.log(`  [${r.status}] ${r.url}`)
  }

  expect(true).toBe(true)
})
