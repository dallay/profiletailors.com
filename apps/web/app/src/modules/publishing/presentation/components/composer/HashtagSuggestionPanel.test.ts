import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { HashtagSavedSet, HashtagSuggestion } from '@modules/publishing/services/hashtag-api'
import HashtagSuggestionPanel from './HashtagSuggestionPanel.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
}))

vi.mock('@lucide/vue', () => {
  const icon = { template: '<span />' }
  return {
    Hash: icon,
    TrendingUp: icon,
    Bookmark: icon,
    BookmarkPlus: icon,
    X: icon,
    ChevronDown: icon,
    ChevronUp: icon,
    Loader2: icon,
  }
})

describe('HashtagSuggestionPanel accessibility', () => {
  function mountPanel(overrides: Record<string, unknown> = {}) {
    return mount(HashtagSuggestionPanel, {
      props: {
        suggestions: [],
        trending: [],
        savedSets: [],
        addedHashtags: new Set(['#testing']),
        hashtagCount: 1,
        isAtLimit: false,
        isApproachingLimit: false,
        isAnalyzing: false,
        isSaving: false,
        ...overrides,
      },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          Button: {
            inheritAttrs: false,
            template: '<button v-bind="$attrs"><slot /></button>',
          },
        },
      },
    })
  }

  it('associates the saved-set name field with its label', async () => {
    const wrapper = mountPanel()

    const saveButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('saveAsSet'))
    expect(saveButton).toBeDefined()
    await saveButton!.trigger('click')

    expect(wrapper.find('label[for="hashtag-save-set-name"]').exists()).toBe(true)
    const input = wrapper.get('#hashtag-save-set-name')
    expect(wrapper.get('label[for="hashtag-save-set-name"]').attributes('for')).toBe(
      input.attributes('id'),
    )
  })

  it('emits add and remove actions while respecting the limit guard', async () => {
    const wrapper = mountPanel({
      suggestions: [
        { hashtag: '#one', relevanceScore: 1, popularity: 'high', category: 'test', usageCount: 1 },
      ],
    })
    const suggestion = wrapper.find('button[aria-pressed="false"]')
    await suggestion.trigger('click')
    expect(wrapper.emitted('add')).toEqual([['#one']])

    const added = mountPanel({
      suggestions: [
        { hashtag: '#one', relevanceScore: 1, popularity: 'high', category: 'test', usageCount: 1 },
      ],
      addedHashtags: new Set(['#one']),
    })
    await added.find('button[aria-pressed="true"]').trigger('click')
    expect(added.emitted('remove')).toEqual([['#one']])

    const atLimit = mountPanel({
      suggestions: [
        { hashtag: '#two', relevanceScore: 1, popularity: 'high', category: 'test', usageCount: 1 },
      ],
      hashtagCount: 30,
      isAtLimit: true,
      addedHashtags: new Set(),
    })
    await atLimit.find('button[aria-pressed="false"]').trigger('click')
    expect(atLimit.emitted('add')).toBeUndefined()
  })

  it('falls back to trending tags and emits saved-set actions', async () => {
    const savedSet: HashtagSavedSet = {
      id: 'set-1',
      workspaceId: 'workspace-1',
      name: 'Engineering',
      hashtags: ['#testing'],
      createdAt: '',
      updatedAt: '',
    }
    const wrapper = mountPanel({
      suggestions: [],
      trending: [
        {
          hashtag: '#trending',
          relevanceScore: 1,
          popularity: 'trending',
          category: 'test',
          usageCount: 1,
        },
      ],
      savedSets: [savedSet],
    })

    expect(wrapper.text()).toContain('#trending')
    const savedSetsToggle = wrapper
      .findAll('button')
      .find((button) => button.text().includes('savedSetsLabel'))
    await savedSetsToggle?.trigger('click')
    const apply = wrapper.findAll('button').find((button) => button.text().includes('applySet'))
    await apply?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.attributes('aria-label') === 'composer.hashtags.deleteSet')
      ?.trigger('click')

    expect(wrapper.emitted('apply-set')).toEqual([[savedSet]])
    expect(wrapper.emitted('delete-set')).toEqual([['set-1']])
  })

  it('renders the limit states, analyzing indicator, empty state, and accessible labels', async () => {
    const baseSuggestion: HashtagSuggestion = {
      hashtag: '#one',
      relevanceScore: 1,
      popularity: 'trending',
      category: 'test',
      usageCount: 1,
    }
    const approaching = mountPanel({
      suggestions: [baseSuggestion],
      hashtagCount: 28,
      isApproachingLimit: true,
    })
    expect(approaching.text()).toContain('limitWarning')
    expect(approaching.find('button[title="composer.hashtags.addTag"]').exists()).toBe(true)

    const atLimit = mountPanel({
      suggestions: [baseSuggestion],
      hashtagCount: 30,
      isAtLimit: true,
      addedHashtags: new Set(),
    })
    expect(atLimit.find('[role="alert"]').text()).toContain('limitError')
    expect(atLimit.find('button[disabled]').exists()).toBe(true)

    const analyzing = mountPanel({ isAnalyzing: true })
    expect(analyzing.text()).toContain('analyzing')
    expect(analyzing.find('[aria-label="Suggestions"]').exists()).toBe(false)

    const empty = mountPanel({ addedHashtags: new Set() })
    expect(empty.text()).toContain('emptyHint')
    expect(empty.find('aside[aria-label="Hashtag suggestions"]').exists()).toBe(true)
  })

  it('submits non-blank saved-set names, ignores blank names, and can cancel the form', async () => {
    const wrapper = mountPanel()
    const saveButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('saveAsSet'))
    await saveButton!.trigger('click')

    const form = wrapper.get('form')
    const nameInput = wrapper.get<HTMLInputElement>('#hashtag-save-set-name')
    const submit = form.find('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
    await form.trigger('submit')
    expect(wrapper.emitted('save-current-as-set')).toBeUndefined()

    await nameInput.setValue('Engineering')
    expect(submit.attributes('disabled')).toBeUndefined()
    await form.trigger('submit')
    expect(wrapper.emitted('save-current-as-set')).toEqual([[['#testing']]])
    expect(wrapper.find('#hashtag-save-set-name').exists()).toBe(false)

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('saveAsSet'))!
      .trigger('click')
    await wrapper.get<HTMLInputElement>('#hashtag-save-set-name').setValue('Temporary')
    await wrapper.find('form button[type="button"]').trigger('click')
    expect(wrapper.find('#hashtag-save-set-name').exists()).toBe(false)
  })

  it('blocks applying saved sets at the limit and toggles the trending section', async () => {
    const savedSet: HashtagSavedSet = {
      id: 'set-1',
      workspaceId: 'workspace-1',
      name: 'Engineering',
      hashtags: ['#testing'],
      createdAt: '',
      updatedAt: '',
    }
    const trending: HashtagSuggestion[] = [
      {
        hashtag: '#trending',
        relevanceScore: 1,
        popularity: 'trending',
        category: 'test',
        usageCount: 1,
      },
    ]
    const wrapper = mountPanel({
      suggestions: [
        {
          hashtag: '#suggested',
          relevanceScore: 1,
          popularity: 'high',
          category: 'test',
          usageCount: 1,
        },
      ],
      trending,
      savedSets: [savedSet],
      isAtLimit: true,
      hashtagCount: 30,
      addedHashtags: new Set(),
    })

    const savedToggle = wrapper
      .findAll('button')
      .find((button) => button.text().includes('savedSetsLabel'))
    await savedToggle!.trigger('click')
    const applyButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('applySet'))
    expect(applyButton?.attributes('disabled')).toBeDefined()

    const trendingToggle = wrapper
      .findAll('button')
      .find((button) => button.text().includes('trendingLabel'))
    await trendingToggle!.trigger('click')
    expect(wrapper.find('section[aria-label="Trending hashtags"] ul').exists()).toBe(true)
    await trendingToggle!.trigger('click')
    expect(wrapper.find('section[aria-label="Trending hashtags"] ul').exists()).toBe(false)
  })

  it('removes an already-added tag from the expanded trending section', async () => {
    const wrapper = mountPanel({
      suggestions: [
        {
          hashtag: '#suggested',
          relevanceScore: 1,
          popularity: 'high',
          category: 'test',
          usageCount: 1,
        },
      ],
      trending: [
        {
          hashtag: '#trending',
          relevanceScore: 1,
          popularity: 'trending',
          category: 'test',
          usageCount: 1,
        },
      ],
      addedHashtags: new Set(['#trending']),
      hashtagCount: 1,
    })

    const trendingToggle = wrapper
      .findAll('button')
      .find((button) => button.text().includes('trendingLabel'))
    await trendingToggle!.trigger('click')
    const trendingTag = wrapper
      .find('section[aria-label="Trending hashtags"]')
      .find('button[aria-pressed="true"]')
    expect(trendingTag.attributes('title')).toBe('composer.hashtags.removeTag')
    await trendingTag.trigger('click')

    expect(wrapper.emitted('remove')).toEqual([['#trending']])
  })

  it('adds a new tag from the expanded trending section', async () => {
    const wrapper = mountPanel({
      suggestions: [
        {
          hashtag: '#suggested',
          relevanceScore: 1,
          popularity: 'high',
          category: 'test',
          usageCount: 1,
        },
      ],
      trending: [
        {
          hashtag: '#trending',
          relevanceScore: 1,
          popularity: 'trending',
          category: 'test',
          usageCount: 1,
        },
      ],
      addedHashtags: new Set(),
      hashtagCount: 0,
    })

    const trendingToggle = wrapper
      .findAll('button')
      .find((button) => button.text().includes('trendingLabel'))
    await trendingToggle!.trigger('click')
    await wrapper
      .find('section[aria-label="Trending hashtags"]')
      .find('button[aria-pressed="false"]')
      .trigger('click')

    expect(wrapper.emitted('add')).toEqual([['#trending']])
  })

  it('disables saved-set submission while a save is in progress', async () => {
    const wrapper = mountPanel({ isSaving: true })
    const saveButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('saveAsSet'))
    await saveButton!.trigger('click')
    await wrapper.get<HTMLInputElement>('#hashtag-save-set-name').setValue('Engineering')

    expect(wrapper.find('form button[type="submit"]').attributes('disabled')).toBeDefined()
  })
})
