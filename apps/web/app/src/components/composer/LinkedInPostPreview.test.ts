import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import LinkedInPostPreview from './LinkedInPostPreview.vue'

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  proxyImageUrl: (url: string) => url,
}))

import type { LinkedInPreviewModel } from './post-preview.types'

function buildPreview(overrides: Partial<LinkedInPreviewModel> = {}) {
  return {
    authorName: 'Profile Tailors',
    authorHandle: 'profiletailors',
    authorAvatarUrl: null,
    authorInitials: 'PT',
    text: 'Short post copy',
    placeholderText: "See your post's preview here",
    media: null,
    ...overrides,
  }
}

describe('LinkedInPostPreview.vue', () => {
  it('renders full text without the more affordance when content is short', () => {
    const wrapper = mount(LinkedInPostPreview, {
      props: {
        preview: buildPreview(),
      },
    })

    expect(wrapper.get('[data-testid="linkedin-preview-text"]').text()).toBe('Short post copy')
    expect(wrapper.find('[data-testid="linkedin-preview-more"]').exists()).toBe(false)
  })

  it('shows the more affordance when multiline content exceeds the preview threshold', () => {
    const longText = [
      'Launching something new for social teams.',
      'This preview should stay visually stable even if the author pastes a much longer draft.',
      'Line breaks should not let the card grow forever inside the compose modal anymore.',
      'That is the whole point of this regression test.',
    ].join('\n\n')

    const wrapper = mount(LinkedInPostPreview, {
      props: {
        preview: buildPreview({ text: longText }),
      },
    })

    expect(wrapper.get('[data-testid="linkedin-preview-text"]').classes()).toContain(
      'preview-text-clamp',
    )
    expect(wrapper.get('[data-testid="linkedin-preview-more"]').text()).toBe('...more')
    expect(wrapper.get('[data-testid="linkedin-preview-text"]').text()).toContain(
      'Launching something new',
    )
  })

  it('renders placeholder copy when there is no post text', () => {
    const wrapper = mount(LinkedInPostPreview, {
      props: {
        preview: buildPreview({ text: '   ' }),
      },
    })

    expect(wrapper.text()).toContain("See your post's preview here")
    expect(wrapper.find('[data-testid="linkedin-preview-text"]').exists()).toBe(false)
  })

  it('keeps media visible when text is truncated', () => {
    const wrapper = mount(LinkedInPostPreview, {
      props: {
        preview: buildPreview({
          text: 'A'.repeat(320),
          media: {
            kind: 'image',
            url: 'blob:image-preview',
            alt: 'Media preview',
            name: 'preview.png',
          },
        }),
      },
    })

    const media = wrapper.get('[data-testid="linkedin-preview-media"] img')
    expect(media.attributes('src')).toBe('blob:image-preview')
    expect(wrapper.find('[data-testid="linkedin-preview-more"]').exists()).toBe(true)
  })

  it('renders a fallback media card for non-image attachments', () => {
    const wrapper = mount(LinkedInPostPreview, {
      props: {
        preview: buildPreview({
          text: 'Video post',
          media: {
            kind: 'video',
            url: null,
            alt: 'Media preview',
            name: 'launch-demo.mp4',
          },
        }),
      },
    })

    expect(wrapper.get('[data-testid="linkedin-preview-media"]').text()).toContain(
      'launch-demo.mp4',
    )
    expect(wrapper.get('[data-testid="linkedin-preview-media"]').text()).toContain('Video')
  })
})
