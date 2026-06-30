/**
 * File hash utilities for computing SHA-256 hashes of browser File objects.
 *
 * Strategy:
 * - Files smaller than 100 MB use native `crypto.subtle.digest()` because it is fast and simple.
 * - Files at or above 100 MB use the incremental SHA-256 implementation below over
 *   `File.stream()` chunks so the browser does not materialise the full file as one ArrayBuffer.
 *
 * The streaming implementation is intentionally local and dependency-free: it keeps the marketing/app
 * bundle lightweight while satisfying the CAS contract for large files.
 */

const STREAMING_HASH_THRESHOLD_BYTES = 100 * 1024 * 1024

function bytesToHex(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer)
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

export function sanitizeFilename(name: string): string {
  return name.replaceAll(/[/\\]/g, '_').replaceAll(/\.\./g, '_').replaceAll(/\0/g, '').slice(0, 255)
}

export async function computeFileHash(file: File): Promise<string> {
  if (file.size < STREAMING_HASH_THRESHOLD_BYTES) {
    const buffer = await file.arrayBuffer()
    const digest = await crypto.subtle.digest('SHA-256', buffer)
    return bytesToHex(digest)
  }

  return computeHashStreaming(file)
}

export async function computeHashStreaming(file: File): Promise<string> {
  const hasher = new Sha256StreamingHasher()
  const reader = file.stream().getReader()

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    hasher.update(value)
  }

  return bytesToHex(hasher.digest())
}

class Sha256StreamingHasher {
  private readonly state = new Uint32Array([
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
  ])
  private readonly buffer = new Uint8Array(64)
  private bufferLength = 0
  private bytesHashed = 0
  private finished = false

  update(chunk: Uint8Array): void {
    if (this.finished) throw new Error('SHA-256 hasher already finalised')

    let position = 0
    this.bytesHashed += chunk.length

    while (position < chunk.length) {
      const take = Math.min(64 - this.bufferLength, chunk.length - position)
      this.buffer.set(chunk.subarray(position, position + take), this.bufferLength)
      this.bufferLength += take
      position += take

      if (this.bufferLength === 64) {
        this.compress(this.buffer)
        this.bufferLength = 0
      }
    }
  }

  digest(): Uint8Array {
    if (this.finished) throw new Error('SHA-256 hasher already finalised')
    this.finished = true

    const bitLengthHigh = Math.floor((this.bytesHashed * 8) / 0x100000000)
    const bitLengthLow = (this.bytesHashed * 8) >>> 0

    this.buffer[this.bufferLength++] = 0x80

    if (this.bufferLength > 56) {
      this.buffer.fill(0, this.bufferLength, 64)
      this.compress(this.buffer)
      this.bufferLength = 0
    }

    this.buffer.fill(0, this.bufferLength, 56)
    writeUint32(this.buffer, 56, bitLengthHigh)
    writeUint32(this.buffer, 60, bitLengthLow)
    this.compress(this.buffer)

    const output = new Uint8Array(32)
    for (let i = 0; i < this.state.length; i++) {
      writeUint32(output, i * 4, this.state[i] ?? 0)
    }
    return output
  }

  private compress(chunk: Uint8Array): void {
    const words = new Uint32Array(64)
    for (let i = 0; i < 16; i++) {
      words[i] = readUint32(chunk, i * 4)
    }
    for (let i = 16; i < 64; i++) {
      const w15 = words[i - 15] ?? 0
      const w2 = words[i - 2] ?? 0
      words[i] =
        (smallSigma1(w2) + (words[i - 7] ?? 0) + smallSigma0(w15) + (words[i - 16] ?? 0)) >>> 0
    }

    let a = this.state[0] ?? 0
    let b = this.state[1] ?? 0
    let c = this.state[2] ?? 0
    let d = this.state[3] ?? 0
    let e = this.state[4] ?? 0
    let f = this.state[5] ?? 0
    let g = this.state[6] ?? 0
    let h = this.state[7] ?? 0

    for (let i = 0; i < 64; i++) {
      const t1 = (h + bigSigma1(e) + choose(e, f, g) + (K[i] ?? 0) + (words[i] ?? 0)) >>> 0
      const t2 = (bigSigma0(a) + majority(a, b, c)) >>> 0
      h = g
      g = f
      f = e
      e = (d + t1) >>> 0
      d = c
      c = b
      b = a
      a = (t1 + t2) >>> 0
    }

    this.state[0] = ((this.state[0] ?? 0) + a) >>> 0
    this.state[1] = ((this.state[1] ?? 0) + b) >>> 0
    this.state[2] = ((this.state[2] ?? 0) + c) >>> 0
    this.state[3] = ((this.state[3] ?? 0) + d) >>> 0
    this.state[4] = ((this.state[4] ?? 0) + e) >>> 0
    this.state[5] = ((this.state[5] ?? 0) + f) >>> 0
    this.state[6] = ((this.state[6] ?? 0) + g) >>> 0
    this.state[7] = ((this.state[7] ?? 0) + h) >>> 0
  }
}

const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
])

function readUint32(bytes: Uint8Array, offset: number): number {
  return (
    (((bytes[offset] ?? 0) << 24) |
      ((bytes[offset + 1] ?? 0) << 16) |
      ((bytes[offset + 2] ?? 0) << 8) |
      (bytes[offset + 3] ?? 0)) >>>
    0
  )
}

function writeUint32(bytes: Uint8Array, offset: number, value: number): void {
  bytes[offset] = (value >>> 24) & 0xff
  bytes[offset + 1] = (value >>> 16) & 0xff
  bytes[offset + 2] = (value >>> 8) & 0xff
  bytes[offset + 3] = value & 0xff
}

function rotateRight(value: number, bits: number): number {
  return (value >>> bits) | (value << (32 - bits))
}

function choose(x: number, y: number, z: number): number {
  return (x & y) ^ (~x & z)
}

function majority(x: number, y: number, z: number): number {
  return (x & y) ^ (x & z) ^ (y & z)
}

function bigSigma0(x: number): number {
  return rotateRight(x, 2) ^ rotateRight(x, 13) ^ rotateRight(x, 22)
}

function bigSigma1(x: number): number {
  return rotateRight(x, 6) ^ rotateRight(x, 11) ^ rotateRight(x, 25)
}

function smallSigma0(x: number): number {
  return rotateRight(x, 7) ^ rotateRight(x, 18) ^ (x >>> 3)
}

function smallSigma1(x: number): number {
  return rotateRight(x, 17) ^ rotateRight(x, 19) ^ (x >>> 10)
}
