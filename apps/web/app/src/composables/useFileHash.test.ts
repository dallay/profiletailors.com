import { describe, expect, it, vi } from 'vitest'
import { ReadableStream } from 'node:stream/web'
import { computeFileHash, computeHashStreaming, sanitizeFilename } from './useFileHash'

const SHA256_ABC = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'

class StreamingTestFile extends File {
  private readonly chunks: Uint8Array[]

  constructor(parts: string[], name: string, options?: FilePropertyBag) {
    super(parts, name, options)
    this.chunks = parts.map((part) => new TextEncoder().encode(part))
  }

  override get size(): number {
    return 100 * 1024 * 1024
  }

  override stream(): ReadableStream<Uint8Array> {
    const chunks = [...this.chunks]
    return new ReadableStream<Uint8Array>({
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
})
