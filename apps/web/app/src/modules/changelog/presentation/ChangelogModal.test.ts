import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ChangelogEntry } from '@profiletailors/shared-web'
import i18n from '@shared/i18n'

vi.mock('@/components/ui/dialog', () => ({
  Dialog: {
    props: ['open'],
    template: '<div v-if="open" data-testid="dialog-root"><slot /></div>',
  },
  DialogContent: {
    template: '<div data-testid="dialog-content"><slot /></div>',
  },
  DialogTitle: {
    template: '<h2><slot /></h2>',
  },
  DialogDescription: {
    template: '<p><slot /></p>',
  },
  DialogClose: {
    template: '<div><slot /></div>',
  },
}))

import ChangelogModal from './ChangelogModal.vue'

const entries: ChangelogEntry[] = [
  {
    id: '2026-10-09-shortlinks',
    slug: '2026-10-09-shortlinks',
    locale: 'en',
    date: '2026-10-09',
    version: 'v0.3.18',
    title: 'Workspace shortlinks and click analytics',
    summary: 'Automatic URL shortening on new posts.',
    categories: ['feature', 'improvement'],
    image: 'https://profiletailors.com/changelog/shortlinks.svg',
    imageAlt: 'Shortlinks preview',
    url: 'https://profiletailors.com/changelog/2026-10-09-shortlinks/',
    sections: [
      {
        heading: 'Automatic URL shortening',
        paragraphs: ['Distinct URLs are shortened on submission.'],
        bullets: ['Best-effort shortening with non-blocking warnings.'],
      },
    ],
  },
  {
    id: '2026-10-02-threads',
    slug: '2026-10-02-threads',
    locale: 'en',
    date: '2026-10-02',
    version: 'v0.3.14',
    title: 'Threads integration',
    summary: 'Connect Threads alongside LinkedIn.',
    categories: ['feature'],
    url: 'https://profiletailors.com/changelog/2026-10-02-threads/',
    sections: [],
  },
]

describe('ChangelogModal', () => {
  it('renders the Linear-style header, active entry content, and emits seen on open', async () => {
    const wrapper = mount(ChangelogModal, {
      global: {
        plugins: [i18n],
      },
      props: {
        open: true,
        status: 'ready',
        entries,
        activeEntry: entries[0],
        activeIndex: 0,
        hasPrevious: true,
        hasNext: false,
        locale: 'en',
        fullChangelogUrl: 'https://profiletailors.com/changelog/',
      },
    })

    expect(wrapper.emitted('seen')).toHaveLength(1)
    expect(wrapper.text()).toContain('Workspace shortlinks and click analytics')
    expect(wrapper.text()).toContain('Automatic URL shortening')
    expect(wrapper.text()).toContain('Best-effort shortening with non-blocking warnings.')

    const externalLink = wrapper.find('[data-testid="changelog-external-link"]')
    expect(externalLink.attributes('href')).toBe(entries[0].url)

    const prevButton = wrapper.find('[data-testid="changelog-previous-button"]')
    await prevButton.trigger('click')
    expect(wrapper.emitted('previous')).toHaveLength(1)

    wrapper.unmount()
  })

  it('renders an explicit error state with retry action', () => {
    const wrapper = mount(ChangelogModal, {
      global: {
        plugins: [i18n],
      },
      props: {
        open: true,
        status: 'error',
        entries: [],
        activeEntry: null,
        activeIndex: 0,
        hasPrevious: false,
        hasNext: false,
        locale: 'en',
        fullChangelogUrl: 'https://profiletailors.com/changelog/',
      },
    })

    const alert = wrapper.find('[data-testid="changelog-error-state"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Could not load updates')

    wrapper.unmount()
  })
})
