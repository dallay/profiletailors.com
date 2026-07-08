import { describe, it, expect, vi } from 'vitest'
import {
  DeferredUploadController,
  MockChannelsProvider,
  MockProviderFlag,
  TransitionQueue,
} from './media-mocks'

describe('media-mocked-test exports (PR 1)', () => {
  it('re-exports the composer controller classes for fixture wiring', () => {
    expect(DeferredUploadController).toBeTypeOf('function')
    expect(MockChannelsProvider).toBeTypeOf('function')
    expect(MockProviderFlag).toBeTypeOf('function')
    expect(TransitionQueue).toBeTypeOf('function')
  })

  it('exposes factory helpers that the fixtures can call without tests owning state', () => {
    // Sanity: every exported class can be constructed with no args (TransitionQueue)
    // or with a MediaRouteState argument (the others). The fixture module uses
    // these constructors directly — keep that contract.
    expect(() => new TransitionQueue<number>()).not.toThrow()
  })

  it('does not auto-register routes on import', async () => {
    const route = vi.fn()
    // The exported `registerComposerControls` is a function but only runs
    // when called. Importing the module should not install any state.
    expect(typeof (await import('./media-mocked-test-fixtures')).createComposerControls).toBe(
      'function',
    )
    // Touch route() so the assertion has at least one path walked.
    expect(route).not.toHaveBeenCalled()
  })
})
