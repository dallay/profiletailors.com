import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import enIdeas from './shared/i18n/locales/en/ideas'
import enInvitation from './shared/i18n/locales/en/invitation'
import enScheduler from './shared/i18n/locales/en/scheduler'
import esIdeas from './shared/i18n/locales/es/ideas'
import esInvitation from './shared/i18n/locales/es/invitation'
import esScheduler from './shared/i18n/locales/es/scheduler'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

describe('authentication UUID fixtures', () => {
  it('replays raw principal UUIDs in the authentication HAR', () => {
    const har = JSON.parse(readFileSync('e2e/hars/auth-flow.har', 'utf8'))
    const principalIds = har.log.entries.flatMap(
      (entry: { response: { content: { text?: string } } }) => {
        const text = entry.response.content.text
        if (!text) return []
        const body = JSON.parse(text)
        return typeof body.principalId === 'string' ? [body.principalId] : []
      },
    )
    expect(principalIds.length).toBeGreaterThan(0)
    for (const principalId of principalIds) expect(principalId).toMatch(UUID_PATTERN)
  })

  it('uses raw UUID defaults for authentication flow helpers', () => {
    const source = readFileSync('e2e/fixtures/auth-helpers.ts', 'utf8')
    const defaults = [...source.matchAll(/principalId = '([^']+)'/g)]
    expect(defaults.length).toBeGreaterThan(0)
    for (const match of defaults) expect(match[1]).toMatch(UUID_PATTERN)
  })

  it('keeps refresh and profile principal defaults consistent', () => {
    const source = readFileSync('e2e/fixtures/auth-helpers.ts', 'utf8')
    const refresh = source.split('export async function mockRefreshResponse(')[1]?.split('\n}')[0]
    const profile = source.split('export async function mockUserProfile(')[1]?.split('\n}')[0]
    const principalId = profile?.match(/principalId = '([^']+)'/)?.[1]
    expect(principalId).toMatch(UUID_PATTERN)
    expect(refresh).toContain(`principalId = '${principalId}'`)
    expect(refresh).toMatch(/principalId,\s+email,/)
  })

  it('propagates session identity and workspace overrides to refresh', () => {
    const source = readFileSync('e2e/fixtures/auth-helpers.ts', 'utf8')
    const session = source
      .split('export async function mockAuthenticatedSession(')[1]
      ?.split('\n}')[0]
    expect(session).toMatch(
      /mockRefreshResponse\(page,\s*\{[^}]*\bprincipalId\b[^}]*\bworkspaceId\b/s,
    )
    const refresh = source.split('export async function mockRefreshResponse(')[1]?.split('\n}')[0]
    expect(refresh).toContain('overrides: SessionOverrides = {}')
    expect(refresh).toMatch(/emailStatus,\s+workspaceId,/)
  })
})

it.each([
  [
    'en',
    enIdeas,
    enInvitation,
    enScheduler,
    'Idea converted. Publication: {id}',
    'This invitation link is no longer valid. Request a new invitation.',
  ],
  [
    'es',
    esIdeas,
    esInvitation,
    esScheduler,
    'Idea convertida. Publicacion: {id}',
    'Este enlace de invitación ya no es válido. Solicita una nueva invitación.',
  ],
] as const)(
  'preserves user-facing messages in %s',
  (_locale, ideas, invitation, scheduler, converted, invalid) => {
    expect(ideas.toasts.convertedWithId).toBe(converted)
    expect(ideas.toasts.convertedWithId).toContain('{id}')
    expect(invitation.errors.invalid).toBe(invalid)
    expect(scheduler.timezoneMadrid).toBe('Madrid')
  },
)

it('uses fixture workspace headers and checks real Unsplash cleanup responses', () => {
  const source = readFileSync('e2e/specs/media-real-unsplash.spec.ts', 'utf8')
  expect(source).not.toContain('X-Workspace-Id')
  expect(source).toMatch(/expect\(\[204, 404\]\)\.toContain\(publicationCleanup.status\(\)\)/)
  expect(source).toMatch(/expect\(\[204, 404\]\)\.toContain\(assetCleanup.status\(\)\)/)
})

it.each([
  'e2e/specs/invitee-private-beta.spec.ts',
  'e2e/specs/register-flow.spec.ts',
  'e2e/specs/scheduler-threads.spec.ts',
  'e2e/specs/bulk-import.spec.ts',
])('uses UUIDs for internal API response identifiers in %s', (path) => {
  const source = readFileSync(path, 'utf8')
  const identifiers = [
    ...source.matchAll(/\b(?:principalId|workspaceId|connectionId|jobId|groupId): '([^']+)'/g),
  ]
  expect(identifiers.length).toBeGreaterThan(0)
  for (const identifier of identifiers) expect(identifier[1]).toMatch(UUID_PATTERN)
})
