import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { flattenTranslations, messages_en, messages_es } from './index'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const SRC_DIR = join(import.meta.dirname, '..')

/**
 * Recursively collect every `.vue` and `.ts` file under `dir`.
 */
function collectSourceFiles(dir: string): string[] {
  const files: string[] = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory() && entry.name !== 'node_modules') {
      files.push(...collectSourceFiles(full))
    } else if (
      entry.isFile() &&
      (full.endsWith('.vue') || full.endsWith('.ts')) &&
      !full.endsWith('.test.ts') &&
      !full.endsWith('.d.ts')
    ) {
      files.push(full)
    }
  }
  return files
}

/**
 * Extract static i18n key references from file content.
 *
 * Matches these patterns (single and double quotes):
 *   - `t('some.key')` or `t("some.key")`
 *   - `$t('some.key')` or `$t("some.key")`
 *   - `labelKey: 'some.key'` or `labelKey: "some.key"`
 *
 * Dynamic template literals like `` t(`nav.${name}`) `` are intentionally
 * NOT matched because they cannot be statically verified.
 */
function extractKeys(content: string): string[] {
  const keys = new Set<string>()

  // t('...')  and  $t('...')
  // Negative lookbehind ensures 't' starts at a word boundary, preventing
  // false matches inside function names that end with 't(' — e.g.
  // emit('close'), mount('#app'), split('-'), import('path'), etc.
  const dotCallRe = /(?<!\w)\$?t\(\s*['"]([^'"]+)['"]\s*\)/g
  for (const m of content.matchAll(dotCallRe)) {
    const key = m[1]
    if (key !== undefined) keys.add(key)
  }

  // labelKey: '...'  or  labelKey: "..."
  const labelKeyRe = /labelKey:\s*['"]([^'"]+)['"]/g
  for (const m of content.matchAll(labelKeyRe)) {
    const key = m[1]
    if (key !== undefined) keys.add(key)
  }

  return [...keys]
}

/**
 * Flatten both locale objects into a set of known dot‑notation keys.
 */
function getKnownKeys(en: Record<string, unknown>, es: Record<string, unknown>): Set<string> {
  return new Set([...flattenTranslations(en), ...flattenTranslations(es)])
}

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

const known = getKnownKeys(messages_en, messages_es)

const allFiles = collectSourceFiles(SRC_DIR)
const results: Array<{ file: string; key: string }> = []

for (const file of allFiles) {
  const content = readFileSync(file, 'utf-8')
  const keys = extractKeys(content)
  for (const key of keys) {
    if (!known.has(key)) {
      results.push({ file: relative(SRC_DIR, file), key })
    }
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('i18n key validation', () => {
  it('keeps English and Spanish password recovery keys in strict parity', () => {
    const enKeys = flattenTranslations(
      messages_en.passwordRecovery as Record<string, unknown>,
    ).sort()
    const esKeys = flattenTranslations(
      messages_es.passwordRecovery as Record<string, unknown>,
    ).sort()
    expect(esKeys).toEqual(enKeys)
  })

  it('every referenced i18n key exists in both en and es locales', () => {
    if (results.length > 0) {
      const report = results.map((r) => `  ❌ ${r.file} → "${r.key}"`).join('\n')
      expect(results, `Missing i18n keys:\n${report}`).toEqual([])
    }
  })
})
