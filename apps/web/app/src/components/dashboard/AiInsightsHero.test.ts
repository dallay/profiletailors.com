import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AiInsightsHero from './AiInsightsHero.vue'
import type { AiInsight } from '@/lib/types/dashboard'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeInsight(id: string, overrides: Partial<AiInsight> = {}): AiInsight {
  return {
    id,
    type: 'recommendation',
    title: 'Post more on LinkedIn Tuesdays',
    description: 'Your LinkedIn engagement peaks on Tuesdays.',
    actionLabel: 'View schedule',
    actionTarget: '/scheduler',
    priority: 'high',
    createdAt: '2026-06-10T08:00:00Z',
    dismissed: false,
    ...overrides,
  }
}

describe('AiInsightsHero', () => {
  it('renders the section title and subtitle', () => {
    const wrapper = mount(AiInsightsHero, {
      props: { insights: [makeInsight('a1')] },
    })
    expect(wrapper.text()).toContain('dashboard.insights.title')
    expect(wrapper.text()).toContain('dashboard.insights.subtitle')
  })

  it('renders the hero insight (first card) prominently', () => {
    const insights = [
      makeInsight('a1', { title: 'Hero Insight Title' }),
      makeInsight('a2', { title: 'Grid Insight' }),
    ]
    const wrapper = mount(AiInsightsHero, {
      props: { insights },
    })
    expect(wrapper.text()).toContain('Hero Insight Title')
    expect(wrapper.text()).toContain('Grid Insight')
  })

  it('renders high priority badge for high priority insights', () => {
    const wrapper = mount(AiInsightsHero, {
      props: { insights: [makeInsight('a1', { priority: 'high' })] },
    })
    expect(wrapper.text()).toContain('dashboard.insights.highPriority')
  })

  it('renders remaining insights in a grid', () => {
    const insights = [makeInsight('a1'), makeInsight('a2'), makeInsight('a3'), makeInsight('a4')]
    const wrapper = mount(AiInsightsHero, {
      props: { insights },
    })
    // 1 hero + 3 grid cards = 4 total
    const cards = wrapper.findAll('.border-l-2')
    expect(cards.length).toBe(4)
  })

  it('emits dismiss event when dismiss button is clicked', async () => {
    const wrapper = mount(AiInsightsHero, {
      props: { insights: [makeInsight('a1')] },
    })
    const dismissButton = wrapper.find('button')
    await dismissButton.trigger('click')
    expect(wrapper.emitted('dismiss')).toBeTruthy()
    expect(wrapper.emitted('dismiss')?.[0]?.[0]).toBe('a1')
  })

  it('shows empty state when insights array is empty', () => {
    const wrapper = mount(AiInsightsHero, {
      props: { insights: [] },
    })
    expect(wrapper.text()).toContain('dashboard.insights.empty')
  })
})
