import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useOnlineStatus } from './useOnlineStatus'

const Harness = defineComponent({
  setup: useOnlineStatus,
  template: '<div />',
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('useOnlineStatus', () => {
  it.each([
    { newestResponse: new Response(null, { status: 200 }), newestStatus: 'online', oldFails: true },
    {
      newestResponse: new Response(null, { status: 503 }),
      newestStatus: 'api-unreachable',
      oldFails: false,
    },
  ])(
    'keeps the newest result when an older probe settles later',
    async ({ newestResponse, newestStatus, oldFails }) => {
      const pending: Array<{
        resolve: (response: Response) => void
        reject: (error: Error) => void
      }> = []
      vi.stubGlobal(
        'fetch',
        vi.fn(
          () =>
            new Promise<Response>((resolve, reject) => {
              pending.push({ resolve, reject })
            }),
        ),
      )

      const wrapper = mount(Harness)
      const newest = wrapper.vm.retry()
      pending[1]!.resolve(newestResponse)
      await newest
      expect(wrapper.vm.status).toBe(newestStatus)

      if (oldFails) pending[0]!.reject(new Error('old request failed'))
      else pending[0]!.resolve(new Response(null, { status: 200 }))
      await flushPromises()

      expect(wrapper.vm.status).toBe(newestStatus)
      wrapper.unmount()
    },
  )
})
