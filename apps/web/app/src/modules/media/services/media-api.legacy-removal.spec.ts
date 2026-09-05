import { describe, expect, it } from 'vitest'
import * as mediaApi from './media-api'

describe('legacy media-api removal', () => {
  it('no longer exports reserveAsset / uploadAsset legacy', () => {
    expect((mediaApi as Record<string, unknown>)['reserveAsset']).toBeUndefined()
    expect((mediaApi as Record<string, unknown>)['uploadAsset']).toBeUndefined()
  })

  it('keeps modern CAS flow', () => {
    expect(typeof (mediaApi as Record<string, unknown>)['putAsset']).toBe('function')
  })
})
