import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const GENERATED_DIR = path.resolve(__dirname, '../.generated/media')

export type MediaFixtureRelation =
  | 'base'
  | 'byte-equal-copy'
  | 'one-bit-mutated'
  | 'document'
  | 'video-placeholder'

export interface MediaFixtureManifestEntry {
  readonly name: string
  readonly type: string
  readonly size: number
  readonly sha256: string
  readonly relation: MediaFixtureRelation
  readonly path: string
}

const basePngBytes = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
  'base64',
)
const mutatedPngBytes = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64',
)
// Second inline image — distinct bytes from `base` so multi-card assertions
// can rely on different sha256 / previewUrl identifiers.
const inlineImage2Bytes = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgYGD4DwABBAEAfbLI3wAAAABJRU5ErkJggg==',
  'base64',
)
// Larger inline PNG — pads the inlineImage2 bytes so the product's upload
// progress is observable in a real browser tick without external assets.
const inlineImageLargeBytes = (() => {
  const chunks: Buffer[] = []
  // Header + IHDR-like sentinel
  chunks.push(inlineImage2Bytes.subarray(0, 8))
  // Payload: ~ 192 KiB of repeating IEND block to inflate size
  const pad = Buffer.alloc(192 * 1024, 0x42)
  chunks.push(pad)
  chunks.push(inlineImage2Bytes.subarray(inlineImage2Bytes.length - 12))
  return Buffer.concat(chunks)
})()
const pdfBytes = Buffer.from(
  '%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n',
)
const mp4PlaceholderBytes = Buffer.from('mocked-playwright-video-placeholder\n')
const invalidTxtBytes = Buffer.from('not a supported media type\n', 'utf-8')

function sha256(bytes: Buffer): string {
  return crypto.createHash('sha256').update(bytes).digest('hex')
}

function writeFixture(name: string, bytes: Buffer): string {
  fs.mkdirSync(GENERATED_DIR, { recursive: true })
  const outputPath = path.join(GENERATED_DIR, name)
  fs.writeFileSync(outputPath, bytes)
  return outputPath
}

function manifestEntry(
  name: string,
  type: string,
  relation: MediaFixtureRelation,
  bytes: Buffer,
): MediaFixtureManifestEntry {
  return {
    name,
    type,
    size: bytes.byteLength,
    sha256: sha256(bytes),
    relation,
    path: writeFixture(name, bytes),
  }
}

/**
 * Single-file catalog. Every entry has a unique sha256 so consumers can use
 * the manifest invariant safely.
 */
const baseCatalog = {
  base: manifestEntry('base.png', 'image/png', 'base', basePngBytes),
  baseCopy: manifestEntry('base-copy.png', 'image/png', 'byte-equal-copy', basePngBytes),
  mutated: manifestEntry('base-mutated.png', 'image/png', 'one-bit-mutated', mutatedPngBytes),
  inlineImage2: manifestEntry(
    'inline-image-2.png',
    'image/png',
    'byte-equal-copy',
    inlineImage2Bytes,
  ),
  inlineImageLarge: manifestEntry(
    'inline-image-large.png',
    'image/png',
    'byte-equal-copy',
    inlineImageLargeBytes,
  ),
  document: manifestEntry('document.pdf', 'application/pdf', 'document', pdfBytes),
  clip: manifestEntry('clip.mp4', 'video/mp4', 'video-placeholder', mp4PlaceholderBytes),
  invalidTxt: manifestEntry('invalid.txt', 'text/plain', 'document', invalidTxtBytes),
} as const

/**
 * Ordered multi-file manifest: first entry is a supported PNG, second is an
 * unsupported .txt, third is a duplicate of the first to assert "first-valid"
 * semantics in drop / multi-select scenarios. NOT included in the manifest
 * aggregator because the duplicate is intentional and would break the
 * sha256-uniqueness invariant.
 */
export const multiFirstValidManifest: readonly MediaFixtureManifestEntry[] = [
  baseCatalog.base,
  baseCatalog.invalidTxt,
  baseCatalog.baseCopy,
] as const

export const mediaFiles = {
  ...baseCatalog,
  multiFirstValid: multiFirstValidManifest,
} as const

export const mediaFixtureManifest: readonly MediaFixtureManifestEntry[] = Object.values(baseCatalog)
