import { describe, expect, it } from 'vitest'
import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

const monorepoRoot = resolve(__dirname, '../../..')

const ALL_SUBPROJECT_READMES = [
  'apps/web/marketing/README.md',
  'apps/web/app/README.md',
  'apps/web/admin/README.md',
  'apps/web/app/e2e/README.md',
  'apps/web/admin/e2e/README.md',
  'server/smp/README.md',
  'shared/common/README.md',
  'shared/presentation/README.md',
  'shared/web/README.md',
  'shared/notifications/README.md',
  'shared/security/README.md',
  'shared/shield/ratelimit/README.md',
  'shared/storage/README.md',
  'shared/lead-capture/common/README.md',
  'shared/lead-capture/waitlist/README.md',
  'shared/bus/README.md',
  'shared/spring-boot-common/README.md',
  'tools/compliance/README.md',
  'gradle/build-logic/README.md',
  'infra/README.md',
  'infra/apps/smp/swarm/secrets/README.md',
  'infra/apps/smp/production/secrets/README.md',
]

const DEPLOYABLE_WITH_PUBLIC_VERSION = [
  'apps/web/marketing/README.md',
  'apps/web/app/README.md',
  'apps/web/admin/README.md',
]

const DEPLOYABLE_WITHOUT_PUBLIC_VERSION = [
  'server/smp/README.md',
]

describe('README Badges & Version Metadata', () => {
  it('root README contains release badges for all 4 independently released components', () => {
    const rootReadmePath = resolve(monorepoRoot, 'README.md')
    const content = readFileSync(rootReadmePath, 'utf8')

    expect(content).toContain('filter=landing%40v*')
    expect(content).toContain('filter=app%40v*')
    expect(content).toContain('filter=admin%40v*')
    expect(content).toContain('filter=smp%40v*')
  })

  it('every existing subproject README has technology badges', () => {
    for (const relativePath of ALL_SUBPROJECT_READMES) {
      const fullPath = resolve(monorepoRoot, relativePath)
      expect(existsSync(fullPath), `README exists at ${relativePath}`).toBe(true)

      const content = readFileSync(fullPath, 'utf8')
      const hasBadges = content.includes('https://img.shields.io/badge/') || content.includes('https://img.shields.io/github/')
      expect(hasBadges, `${relativePath} should contain shields.io badges`).toBe(true)
    }
  })

  it('only deployable web applications have Deployed version badges', () => {
    for (const relativePath of ALL_SUBPROJECT_READMES) {
      const fullPath = resolve(monorepoRoot, relativePath)
      const content = readFileSync(fullPath, 'utf8')
      const hasDeployedBadge = content.includes('label=Deployed') || content.includes('Deployed:')

      if (DEPLOYABLE_WITH_PUBLIC_VERSION.includes(relativePath)) {
        expect(hasDeployedBadge, `${relativePath} should have a Deployed badge`).toBe(true)
      } else {
        expect(hasDeployedBadge, `${relativePath} must NOT have a Deployed badge`).toBe(false)
      }
    }
  })

  it('deployable backend applications have a Release badge but no Deployed badge', () => {
    for (const relativePath of DEPLOYABLE_WITHOUT_PUBLIC_VERSION) {
      const fullPath = resolve(monorepoRoot, relativePath)
      const content = readFileSync(fullPath, 'utf8')

      const hasReleaseBadge = content.includes('filter=smp%40v*') || content.includes('label=Release')
      expect(hasReleaseBadge, `${relativePath} must have a Release badge`).toBe(true)

      const hasDeployedBadge = content.includes('label=Deployed') || content.includes('Deployed:')
      expect(hasDeployedBadge, `${relativePath} must NOT have a Deployed badge`).toBe(false)
    }
  })

  it('web app dist version.json outputs exist and contain valid version, gitSha, and buildTime keys when built', () => {
    const apps = [
      'apps/web/marketing/dist/version.json',
      'apps/web/app/dist/version.json',
      'apps/web/admin/dist/version.json',
    ]

    for (const relativePath of apps) {
      const fullPath = resolve(monorepoRoot, relativePath)
      expect(existsSync(fullPath), `${relativePath} must exist after build`).toBe(true)

      const json = JSON.parse(readFileSync(fullPath, 'utf8'))
      expect(json).toHaveProperty('version')
      expect(json).toHaveProperty('gitSha')
      expect(json).toHaveProperty('buildTime')

      expect(typeof json.version).toBe('string')
      expect(json.version.length).toBeGreaterThan(0)

      expect(typeof json.gitSha).toBe('string')
      expect(json.gitSha.length).toBeGreaterThan(0)

      expect(typeof json.buildTime).toBe('string')
      expect(json.buildTime.length).toBeGreaterThan(0)
      expect(Number.isNaN(Date.parse(json.buildTime))).toBe(false)
    }
  })
})
