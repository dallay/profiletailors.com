import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import ThreadsPostPreview from './ThreadsPostPreview.vue'

vi.mock(
  '@modules/auth/infrastructure/auth-api',
  (): { proxyImageUrl: (url: string) => string } => ({
    proxyImageUrl: (url: string): string => url,
  }),
)

vi.mock('vue-i18n', () => ({
  useI18n: (): { t: (key: string) => string } => ({ t: (key: string): string => key }),
}))

import type { ThreadsPreviewModel } from './post-preview.types'

function buildPreview(overrides: Partial<ThreadsPreviewModel> = {}): ThreadsPreviewModel {
  return {
    authorName: 'WireMock Threads User',
    authorHandle: 'threads-user',
    authorAvatarUrl: null,
    authorInitials: 'WT',
    text: 'Hello Threads',
    placeholderText: "See your post's preview here",
    media: null,
    ...overrides,
  }
}

function mountPreview(preview: ThreadsPreviewModel = buildPreview()): VueWrapper {
  return mount(ThreadsPostPreview, {
    props: {
      preview,
    },
  })
}

describe('ThreadsPostPreview.vue', () => {
  it('renders the author, handle and full text', (): void => {
    const wrapper = mountPreview()

    expect(wrapper.get('[data-testid="threads-preview"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="threads-preview-text"]').text()).toBe('Hello Threads')
    expect(wrapper.text()).toContain('WireMock Threads User')
    expect(wrapper.text()).toContain('threads-user')
  })

  it('renders the placeholder when text is blank', (): void => {
    const wrapper = mountPreview(buildPreview({ text: '   ' }))

    expect(wrapper.get('[data-testid="threads-preview"]').text()).toContain(
      "See your post's preview here",
    )
  })

  it('renders the avatar fallback with initials when no avatar url is available', (): void => {
    const wrapper = mountPreview()

    expect(wrapper.get('[data-testid="threads-preview-avatar-fallback"]').text()).toBe('WT')
  })

  it('renders the avatar image when an avatar url is available', (): void => {
    const wrapper = mountPreview(buildPreview({ authorAvatarUrl: 'https://example.com/t.jpg' }))

    expect(wrapper.get('[data-testid="threads-preview-avatar"]').attributes('src')).toBe(
      'https://example.com/t.jpg',
    )
  })
})
