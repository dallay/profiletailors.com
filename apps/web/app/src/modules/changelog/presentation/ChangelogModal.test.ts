import { afterEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import type { ChangelogEntry } from '@profiletailors/shared-web'
import i18n from '@shared/i18n'
import ChangelogModal from './ChangelogModal.vue'

enableAutoUnmount(afterEach)

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

function mountChangelog(props: Partial<InstanceType<typeof ChangelogModal>['$props']> = {}) {
  return mount(ChangelogModal, {
    attachTo: document.body,
    global: { plugins: [i18n] },
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
      ...props,
    },
  })
}

function getDialog() {
  const dialog = document.body.querySelector('[role="dialog"]')
  if (!dialog) throw new Error('Changelog dialog not rendered')
  return dialog
}

describe('ChangelogModal', () => {
  it('renders the Linear-style header, active entry content, and emits seen on open', async () => {
    const wrapper = mountChangelog()
    await flushPromises()
    const dialog = getDialog()

    expect(wrapper.emitted('seen')).toHaveLength(1)
    expect(dialog.textContent).toContain('Workspace shortlinks and click analytics')
    expect(dialog.textContent).toContain('Automatic URL shortening')
    expect(dialog.textContent).toContain('Best-effort shortening with non-blocking warnings.')
    expect(
      dialog.querySelector('[data-testid="changelog-external-link"]')?.getAttribute('href'),
    ).toBe(entries[0].url)

    dialog.querySelector<HTMLButtonElement>('[data-testid="changelog-previous-button"]')?.click()
    expect(wrapper.emitted('previous')).toHaveLength(1)
  })

  it('renders an explicit error state with retry action', async () => {
    mountChangelog({ status: 'error', entries: [], activeEntry: null })

    await flushPromises()
    expect(getDialog().querySelector('[role="alert"]')?.textContent).toContain(
      'Could not load updates',
    )
  })

  it('has an accessible name and loading announcement when opened during loading', async () => {
    mountChangelog({ status: 'loading', entries: [], activeEntry: null })
    await flushPromises()
    const dialog = getDialog()
    const titleId = dialog.getAttribute('aria-labelledby')

    expect(titleId ? document.getElementById(titleId)?.textContent?.trim() : null).toBe('Changelog')
    expect(dialog.querySelector('[role="status"]')?.textContent).toContain(
      'Loading product updates',
    )
  })

  it.each(['loading', 'error'] as const)(
    'does not emit seen in the %s state with cached entries',
    async (status) => {
      const wrapper = mountChangelog({ status })

      await flushPromises()
      expect(getDialog().querySelector('article')).toBeNull()
      expect(wrapper.emitted('seen')).toBeUndefined()
    },
  )

  it('does not emit seen when ready without an active entry', async () => {
    const wrapper = mountChangelog({ activeEntry: null })

    await flushPromises()
    expect(getDialog().querySelector('article')).toBeNull()
    expect(wrapper.emitted('seen')).toBeUndefined()
  })

  it('emits seen when a refresh of the same length reveals the new latest entry', async () => {
    const replaced = { ...entries[0], id: 'new-update', title: 'New update' }
    const visibleTitles: string[] = []
    const wrapper = mountChangelog({
      open: false,
      onSeen: () =>
        visibleTitles.push(
          document.querySelector('[role=dialog] article h2')?.textContent?.trim() ?? '',
        ),
    })

    await wrapper.setProps({ open: true })
    await flushPromises()
    await wrapper.setProps({ status: 'loading' })
    await wrapper.setProps({
      status: 'ready',
      entries: [replaced, entries[1]],
      activeEntry: replaced,
    })

    expect(visibleTitles).toEqual([entries[0].title, replaced.title])
  })
})
