import { describe, expect, it } from 'vitest'
import { sanitizeSentryPayload } from './sentry-sanitizer'

describe('sanitizeSentryPayload', () => {
  it('removes request credentials and identity fields while preserving opaque identifiers', () => {
    const event = {
      request: {
        url: 'https://app.profiletailors.com/reset-password?token=reset-secret',
        headers: {
          Authorization: 'Bearer access-secret',
          Cookie: 'pt_refresh=refresh-secret',
          'Set-Cookie': 'pt_refresh=refresh-secret',
          Accept: 'application/json',
        },
        cookies: { pt_refresh: 'refresh-secret' },
        data: { password: 'plaintext-secret' },
      },
      response: {
        status_code: 500,
        headers: {
          'X-Forwarded-For': '203.0.113.42',
          'Content-Type': 'application/json',
        },
        data: { resetToken: 'reset-secret' },
      },
      user: {
        id: 'principal-123',
        email: 'person@example.com',
        username: 'person@example.com',
        ip_address: '203.0.113.42',
      },
      tags: {
        principalId: 'principal-123',
        workspaceId: 'workspace-456',
        email: 'person@example.com',
      },
      extra: { payload: 'private-content' },
      spans: [
        {
          data: {
            'url.query': 'code=oauth-secret',
            'http.request.body': 'private-content',
            principalId: 'principal-123',
          },
        },
      ],
    }

    sanitizeSentryPayload(event)

    expect(event).toEqual({
      request: {
        url: 'https://app.profiletailors.com/reset-password',
        headers: { Accept: 'application/json' },
      },
      response: {
        status_code: 500,
        headers: { 'Content-Type': 'application/json' },
      },
      user: { id: 'principal-123' },
      tags: {
        principalId: 'principal-123',
        workspaceId: 'workspace-456',
      },
      spans: [{ data: { principalId: 'principal-123' } }],
    })
  })

  it('sanitizes breadcrumb content, bearer credentials, JWTs, and bounds retained text', () => {
    const breadcrumb = {
      message:
        'OAuth failure for person@example.com from 203.0.113.42 and 2001:db8::1 at https://api.profiletailors.com/callback?code=oauth-secret',
      data: {
        code: 'oauth-secret',
        access_token: 'access-secret',
        detail: 'Authorization: Bearer access-secret',
        unsafeText:
          'password=plaintext-secret api_key=api-secret encryption_key=encryption-secret oauth code=oauth-secret pt_refresh=refresh-secret',
        jwt: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature',
        href: '/oauth/callback?code=oauth-secret#access-secret',
        safeId: 'job-123',
        longValue: 'x'.repeat(600),
      },
    }

    sanitizeSentryPayload(breadcrumb)

    expect(breadcrumb.message).toBe(
      'OAuth failure for [redacted] from [redacted] and [redacted] at https://api.profiletailors.com/callback',
    )
    expect(breadcrumb.data).toEqual({
      detail: 'Authorization: [redacted]',
      unsafeText:
        'password=[redacted] api_key=[redacted] encryption_key=[redacted] oauth code=[redacted] pt_refresh=[redacted]',
      href: '/oauth/callback',
      safeId: 'job-123',
      longValue: 'x'.repeat(512),
    })
  })
})
