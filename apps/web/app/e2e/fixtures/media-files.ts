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

export const mediaFiles = {
  base: manifestEntry('base.png', 'image/png', 'base', basePngBytes),
  baseCopy: manifestEntry('base-copy.png', 'image/png', 'byte-equal-copy', basePngBytes),
  mutated: manifestEntry('base-mutated.png', 'image/png', 'one-bit-mutated', mutatedPngBytes),
  document: manifestEntry('document.pdf', 'application/pdf', 'document', pdfBytes),
  clip: manifestEntry('clip.mp4', 'video/mp4', 'video-placeholder', mp4PlaceholderBytes),
  invalidTxt: manifestEntry('invalid.txt', 'text/plain', 'document', invalidTxtBytes),
} as const

export const mediaFixtureManifest: readonly MediaFixtureManifestEntry[] = Object.values(mediaFiles)

if (mediaFiles.base.sha256 !== mediaFiles.baseCopy.sha256) {
  throw new Error(
    'Media fixture invariant failed: base.png and base-copy.png must be byte-identical',
  )
}

if (mediaFiles.base.sha256 === mediaFiles.mutated.sha256) {
  throw new Error('Media fixture invariant failed: base-mutated.png must have a distinct hash')
}
