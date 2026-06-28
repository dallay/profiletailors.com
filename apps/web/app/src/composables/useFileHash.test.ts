import { describe, expect, it, vi } from 'vitest'
import { computeFileHash, computeHashStreaming, sanitizeFilename } from './useFileHash'

const SHA256_ABC = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
const SHA256_EMPTY = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
const SHA256_64_A = 'ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb'
const SHA256_65_A = '635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0'

class StreamingTestFile extends File {
  private readonly chunks: Uint8Array<ArrayBuffer>[]

  constructor(parts: string[], name: string, options?: FilePropertyBag) {
    super(parts, name, options)
    this.chunks = parts.map((part) => {
      const encoded = new TextEncoder().encode(part)
      return new Uint8Array(encoded)
    })
  }

  override get size(): number {
    return 100 * 1024 * 1024
  }

  override stream(): ReadableStream<Uint8Array<ArrayBuffer>> {
    const chunks = [...this.chunks]
    return new ReadableStream<Uint8Array<ArrayBuffer>>({
      pull(controller) {
        const chunk = chunks.shift()
        if (chunk) controller.enqueue(chunk)
        else controller.close()
      },
    })
  }
}

describe('useFileHash', () => {
  it('uses native subtle digest for files smaller than 100MB', async () => {
    const digestSpy = vi.spyOn(crypto.subtle, 'digest')
    const file = new File(['abc'], 'small.txt', { type: 'text/plain' })

    await expect(computeFileHash(file)).resolves.toBe(SHA256_ABC)

    expect(digestSpy).toHaveBeenCalledOnce()
  })

  it('uses streaming SHA-256 for files at or above 100MB without arrayBuffer', async () => {
    const digestSpy = vi.spyOn(crypto.subtle, 'digest')
    const file = new StreamingTestFile(['abc'], 'large.txt', { type: 'text/plain' })
    const arrayBufferSpy = vi.spyOn(file, 'arrayBuffer')

    await expect(computeFileHash(file)).resolves.toBe(SHA256_ABC)

    expect(digestSpy).not.toHaveBeenCalled()
    expect(arrayBufferSpy).not.toHaveBeenCalled()
  })

  it('exposes streaming hasher API with the same digest as native SHA-256', async () => {
    const file = new StreamingTestFile(['abc'], 'stream.txt', { type: 'text/plain' })

    await expect(computeHashStreaming(file)).resolves.toBe(SHA256_ABC)
  })

  it('sanitizes path separators traversal and null bytes', () => {
    expect(sanitizeFilename('../bad\\name\0.png')).toBe('__bad_name.png')
  })

  it('computes hash of empty file (0 bytes) using native path', async () => {
    const file = new File([], 'empty.txt', { type: 'text/plain' })
    await expect(computeFileHash(file)).resolves.toBe(SHA256_EMPTY)
  })

  it('handles streaming data at chunk boundary (64 bytes — one compress call)', async () => {
    const data = 'a'.repeat(64)
    const file = new StreamingTestFile([data], 'boundary-64.txt', { type: 'text/plain' })
    await expect(computeFileHash(file)).resolves.toBe(SHA256_64_A)
  })

  it('handles streaming data crossing chunk boundary (65 bytes — spans two compress calls)', async () => {
    const data = 'a'.repeat(65)
    const file = new StreamingTestFile([data], 'cross-65.txt', { type: 'text/plain' })
    await expect(computeFileHash(file)).resolves.toBe(SHA256_65_A)
  })
})
