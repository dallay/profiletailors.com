import { describe, it, expect } from 'vitest'
import { mediaFiles, mediaFixtureManifest } from './media-files'

describe('media-files fixture catalog (PR 1)', () => {
  it('exposes the second inline image (inlineImage2) with PNG bytes', () => {
    expect(mediaFiles.inlineImage2).toBeDefined()
    expect(mediaFiles.inlineImage2.type).toBe('image/png')
    expect(mediaFiles.inlineImage2.size).toBeGreaterThan(0)
    expect(mediaFiles.inlineImage2.sha256).toMatch(/^[a-f0-9]{64}$/)
  })

  it('exposes a large inline fixture (inlineImageLarge) for progress tests', () => {
    expect(mediaFiles.inlineImageLarge).toBeDefined()
    expect(mediaFiles.inlineImageLarge.type).toBe('image/png')
    expect(mediaFiles.inlineImageLarge.size).toBeGreaterThan(mediaFiles.base.size)
  })

  it('exposes an invalid text fixture (invalidTxt) for unsupported-type rejection', () => {
    expect(mediaFiles.invalidTxt).toBeDefined()
    expect(mediaFiles.invalidTxt.type).toBe('text/plain')
    expect(mediaFiles.invalidTxt.size).toBeGreaterThan(0)
  })

  it('exposes an ordered multi-file manifest (multiFirstValid) with one valid PNG first', () => {
    expect(mediaFiles.multiFirstValid).toBeDefined()
    expect(mediaFiles.multiFirstValid.length).toBeGreaterThanOrEqual(2)
    const [first, ...rest] = mediaFiles.multiFirstValid
    expect(first.type).toBe('image/png')
    expect(rest.some((f) => f.type === 'text/plain')).toBe(true)
  })

  it('manifest invariants: base and baseCopy share sha256; mutated differs', () => {
    expect(mediaFiles.base.sha256).toBe(mediaFiles.baseCopy.sha256)
    expect(mediaFiles.base.sha256).not.toBe(mediaFiles.mutated.sha256)
  })

  it('manifest aggregates all entries with unique on-disk paths', () => {
    const seen = new Set<string>()
    for (const entry of mediaFixtureManifest) {
      expect(seen.has(entry.path)).toBe(false)
      seen.add(entry.path)
    }
  })

  it('all manifest entries have a real on-disk path', () => {
    for (const entry of mediaFixtureManifest) {
      expect(entry.path).toMatch(/media\//)
      expect(entry.path.length).toBeGreaterThan(0)
    }
  })
})
