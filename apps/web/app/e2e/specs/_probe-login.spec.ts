import { test } from '../fixtures/scheduler-base-test'
import { authenticateAs, mockLoginResponse } from '../fixtures/auth-helpers'

test('probe login flow', async ({ page }) => {
  page.on('console', (msg) => console.log('[browser]', msg.type(), msg.text()))
  page.on('pageerror', (err) => console.log('[pageerror]', err.message))
  page.on('request', (req) => {
    if (req.url().includes('/api/')) console.log('[req]', req.method(), req.url())
  })
  page.on('response', (res) => {
    if (res.url().includes('/api/')) console.log('[res]', res.status(), res.url())
  })

  await mockLoginResponse(page, {
    status: 200,
    body: {
      accessToken: 'probe-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      principalId: 'test-user',
      email: 'dev@profiletailors.com',
      username: 'dev',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    },
  })
  await authenticateAs(page)
  console.log('[final-url]', page.url())
  console.log('[has-token]', await page.evaluate(() => !!(window as any).__AUTH_TOKEN__))
})
