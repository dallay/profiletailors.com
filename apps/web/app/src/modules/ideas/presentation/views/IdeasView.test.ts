import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'
import IdeasView from './IdeasView.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@lucide/vue', () => {
  const icon = { template: '<span />' }
  return {
    Plus: icon,
    Settings: icon,
    Sparkles: icon,
    Trash2: icon,
    ArrowUp: icon,
    ArrowDown: icon,
    Lightbulb: icon,
    Link: icon,
  }
})

type IdeasStoreMock = {
  orderedColumns: IdeaColumn[]
  ideas: Idea[]
  ideasByColumn: Record<string, Idea[]>
  hasWorkspace: boolean
  loading: boolean
  saving: boolean
  error: string | null
  loadBoard: ReturnType<typeof vi.fn>
  createIdea: ReturnType<typeof vi.fn>
  updateIdea: ReturnType<typeof vi.fn>
  moveIdea: ReturnType<typeof vi.fn>
  deleteIdea: ReturnType<typeof vi.fn>
  convertIdea: ReturnType<typeof vi.fn>
  updateColumns: ReturnType<typeof vi.fn>
  createLocalColumn: ReturnType<typeof vi.fn>
  clearState: ReturnType<typeof vi.fn>
}

const { ideasStore, workspaceStore, dnd, toast } = vi.hoisted(() => {
  const monitorOptions: unknown[] = []
  const draggableOptions: unknown[] = []
  const dropTargetOptions: unknown[] = []
  const cleanups: Array<ReturnType<typeof vi.fn>> = []

  const makeCleanup = () => {
    const cleanup = vi.fn()
    cleanups.push(cleanup)
    return cleanup
  }

  const ideasStore: IdeasStoreMock = {
    orderedColumns: [
      { id: 'raw', name: 'Raw', order: 0, color: null },
      { id: 'done', name: 'Done', order: 1, color: null },
    ],
    ideas: [
      {
        id: 'idea-1',
        workspaceId: 'workspace-1',
        title: 'Test idea',
        notes: null,
        tags: [],
        links: [],
        columnId: 'raw',
        orderInColumn: 0,
        convertedToPublicationId: null,
        createdAt: '2026-08-01T00:00:00Z',
        updatedAt: '2026-08-01T00:00:00Z',
      },
    ],
    ideasByColumn: {
      raw: [
        {
          id: 'idea-1',
          workspaceId: 'workspace-1',
          title: 'Test idea',
          notes: null,
          tags: [],
          links: [],
          columnId: 'raw',
          orderInColumn: 0,
          convertedToPublicationId: null,
          createdAt: '2026-08-01T00:00:00Z',
          updatedAt: '2026-08-01T00:00:00Z',
        },
      ],
      done: [],
    },
    hasWorkspace: true,
    loading: false,
    saving: false,
    error: null,
    loadBoard: vi.fn(),
    createIdea: vi.fn(),
    updateIdea: vi.fn(),
    moveIdea: vi.fn(),
    deleteIdea: vi.fn(),
    convertIdea: vi.fn(),
    updateColumns: vi.fn(),
    createLocalColumn: vi.fn((name: string, color: string | null) => ({
      id: 'new-column',
      name,
      color,
      order: 2,
    })),
    clearState: vi.fn(),
  }

  return {
    ideasStore,
    workspaceStore: {
      activeWorkspaceId: 'workspace-1' as string | null,
    },
    dnd: {
      monitorOptions,
      draggableOptions,
      dropTargetOptions,
      cleanups,
      draggable: vi.fn((options: unknown) => {
        draggableOptions.push(options)
        return makeCleanup()
      }),
      dropTargetForElements: vi.fn((options: unknown) => {
        dropTargetOptions.push(options)
        return makeCleanup()
      }),
      monitorForElements: vi.fn((options: unknown) => {
        monitorOptions.push(options)
        return makeCleanup()
      }),
    },
    toast: {
      success: vi.fn(),
      error: vi.fn(),
    },
  }
})

vi.mock('@modules/ideas/infrastructure/ideas.store', () => ({
  useIdeasStore: () => ideasStore,
}))

vi.mock('@modules/workspace/infrastructure/workspace.store', () => ({
  useWorkspaceStore: () => workspaceStore,
}))

vi.mock('@atlaskit/pragmatic-drag-and-drop/element/adapter', () => dnd)
vi.mock('vue-sonner', () => ({ toast }))

const passthrough = { template: '<div><slot /></div>' }
const nativeButton = {
  inheritAttrs: false,
  template: '<button v-bind="$attrs"><slot /></button>',
}
const nativeInput = {
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
}
const nativeTextarea = {
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<textarea v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>',
}
const nativeSelectTrigger = {
  inheritAttrs: false,
  template: '<button v-bind="$attrs"><slot /></button>',
}

function mountIdeasView() {
  return mount(IdeasView, {
    global: {
      mocks: { $t: (key: string) => key },
      stubs: {
        Button: nativeButton,
        Input: nativeInput,
        Textarea: nativeTextarea,
        Select: passthrough,
        SelectContent: passthrough,
        SelectItem: passthrough,
        SelectTrigger: nativeSelectTrigger,
        SelectValue: passthrough,
        Card: passthrough,
        CardContent: passthrough,
        CardHeader: passthrough,
        CardTitle: passthrough,
        Badge: passthrough,
        Dialog: passthrough,
        DialogContent: passthrough,
        DialogDescription: passthrough,
        DialogFooter: passthrough,
        DialogHeader: passthrough,
        DialogTitle: passthrough,
        Sheet: passthrough,
        SheetContent: passthrough,
        SheetDescription: passthrough,
        SheetHeader: passthrough,
        SheetTitle: passthrough,
      },
    },
  })
}

function makeTestIdea(id: string, columnId: string, orderInColumn: number) {
  return {
    id,
    workspaceId: 'workspace-1',
    title: id,
    notes: null,
    tags: [],
    links: [],
    columnId,
    orderInColumn,
    convertedToPublicationId: null,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  }
}

type MonitorOptions = {
  onDragStart: (event: { source: { data: Record<string, unknown> } }) => void
  onDrop: (event: {
    source: { data: Record<string, unknown> }
    location: {
      current: {
        dropTargets: Array<{ data: Record<string, unknown> }>
        input: { clientY: number }
      }
    }
  }) => Promise<void>
}

function latestMonitorOptions(): MonitorOptions {
  return dnd.monitorOptions.at(-1) as MonitorOptions
}

function dropEvent(
  sourceData: Record<string, unknown>,
  dropTargets: Array<{ data: Record<string, unknown> }>,
  clientY = 0,
): Parameters<MonitorOptions['onDrop']>[0] {
  return {
    source: { data: sourceData },
    location: { current: { dropTargets, input: { clientY } } },
  }
}

describe('IdeasView accessibility', () => {
  beforeEach(() => {
    ideasStore.createIdea.mockReset()
    ideasStore.loadBoard.mockReset()
    ideasStore.clearState.mockReset()
    ideasStore.updateIdea.mockReset()
    ideasStore.deleteIdea.mockReset()
    ideasStore.convertIdea.mockReset()
    ideasStore.updateColumns.mockReset()
    ideasStore.createLocalColumn
      .mockReset()
      .mockImplementation((name: string, color: string | null) => ({
        id: 'new-column',
        name,
        color,
        order: 2,
      }))
    ideasStore.hasWorkspace = true
    ideasStore.loading = false
    ideasStore.saving = false
    ideasStore.error = null
    ideasStore.orderedColumns = [
      { id: 'raw', name: 'Raw', order: 0, color: null },
      { id: 'done', name: 'Done', order: 1, color: null },
    ]
    ideasStore.ideas = [
      {
        id: 'idea-1',
        workspaceId: 'workspace-1',
        title: 'Test idea',
        notes: null,
        tags: [],
        links: [],
        columnId: 'raw',
        orderInColumn: 0,
        convertedToPublicationId: null,
        createdAt: '2026-08-01T00:00:00Z',
        updatedAt: '2026-08-01T00:00:00Z',
      },
    ]
    ideasStore.ideasByColumn = {
      raw: [...ideasStore.ideas],
      done: [],
    }
    workspaceStore.activeWorkspaceId = 'workspace-1'
    dnd.monitorOptions.length = 0
    dnd.draggableOptions.length = 0
    dnd.dropTargetOptions.length = 0
    dnd.cleanups.length = 0
    dnd.draggable.mockClear()
    dnd.dropTargetForElements.mockClear()
    dnd.monitorForElements.mockClear()
    toast.success.mockReset()
    toast.error.mockReset()
  })

  it('shows the board loading error instead of rendering stale columns', async () => {
    ideasStore.error = 'Unable to load ideas board.'

    const wrapper = mountIdeasView()
    await flushPromises()

    expect(wrapper.text()).toContain('Unable to load ideas board.')
    expect(wrapper.find('[data-dnd-draggable="idea-1"]').exists()).toBe(false)
  })

  it('shows the workspace guard and clears local state without loading the board', async () => {
    ideasStore.hasWorkspace = false
    workspaceStore.activeWorkspaceId = null

    const wrapper = mountIdeasView()
    await flushPromises()

    expect(wrapper.text()).toContain('ideas.workspaceRequired')
    expect(ideasStore.clearState).toHaveBeenCalledOnce()
    expect(ideasStore.loadBoard).not.toHaveBeenCalled()
  })

  it('shows the loading state before rendering board columns', () => {
    ideasStore.loading = true

    const wrapper = mountIdeasView()

    expect(wrapper.text()).toContain('ideas.loading')
    expect(wrapper.find('[data-dnd-draggable="idea-1"]').exists()).toBe(false)
  })

  it('renders empty-column guidance when a board column has no ideas', () => {
    ideasStore.ideas = []
    ideasStore.ideasByColumn = { raw: [], done: [] }

    const wrapper = mountIdeasView()

    expect(
      wrapper.findAll('p').filter((paragraph) => paragraph.text() === 'ideas.emptyColumn'),
    ).toHaveLength(2)
  })

  it('associates board settings inputs and selects with stable labels', async () => {
    const wrapper = mountIdeasView()

    await wrapper.find('[data-dnd-draggable="idea-1"]').trigger('click')
    await nextTick()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.columns.button'))
      ?.trigger('click')
    await nextTick()

    for (const id of [
      'idea-column-select',
      'idea-column-name-raw',
      'idea-column-color-raw',
      'idea-new-column-name',
      'idea-new-column-color',
    ]) {
      expect(wrapper.find(`label[for="${id}"]`).exists(), `label ${id}`).toBe(true)
      expect(wrapper.find(`#${id}`).exists(), `control ${id}`).toBe(true)
    }
  })

  it('captures a new idea from the quick-capture form', async () => {
    const wrapper = mountIdeasView()

    const addIdeaButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.quickCapture.button'))
    await addIdeaButton?.trigger('click')
    await wrapper.find('#idea-title-input').setValue('A captured idea')
    await wrapper.find('#idea-tags-input').setValue('launch, testing')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.quickCapture.save'))
      ?.trigger('click')

    expect(ideasStore.createIdea).toHaveBeenCalledWith({
      title: 'A captured idea',
      columnId: 'raw',
      tags: ['launch', 'testing'],
    })
  })

  it('saves detail edits with normalized tags, notes, and labeled or unlabeled links', async () => {
    const wrapper = mountIdeasView()
    await wrapper.find('[data-dnd-draggable="idea-1"]').trigger('click')
    await nextTick()

    await wrapper.find('#idea-detail-title').setValue('  Updated idea  ')
    await wrapper.find('#idea-detail-notes').setValue('  More notes  ')
    await wrapper.find('#idea-detail-tags').setValue('launch, testing')
    await wrapper
      .find('#idea-detail-links')
      .setValue('Docs|https://example.com\nhttps://profiletailors.com')
    ideasStore.updateIdea.mockResolvedValue(undefined)

    const saveButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.actions.save'))
    expect(saveButton).toBeDefined()
    await saveButton!.trigger('click')
    await flushPromises()

    expect(ideasStore.updateIdea).toHaveBeenCalledWith('idea-1', {
      title: 'Updated idea',
      notes: 'More notes',
      columnId: 'raw',
      tags: ['launch', 'testing'],
      links: [
        { label: 'Docs', url: 'https://example.com' },
        { label: null, url: 'https://profiletailors.com' },
      ],
    })
    expect(toast.success).toHaveBeenCalledWith('ideas.toasts.updated')
  })

  it('deletes the selected idea and closes its detail sheet', async () => {
    const wrapper = mountIdeasView()
    await wrapper.find('[data-dnd-draggable="idea-1"]').trigger('click')
    await nextTick()
    ideasStore.deleteIdea.mockResolvedValue(undefined)

    const deleteButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.actions.delete'))
    expect(deleteButton).toBeDefined()
    await deleteButton!.trigger('click')
    await flushPromises()

    expect(ideasStore.deleteIdea).toHaveBeenCalledWith('idea-1')
    expect(wrapper.find('#idea-detail-title').exists()).toBe(false)
    expect(toast.success).toHaveBeenCalledWith('ideas.toasts.deleted')
  })

  it('converts the selected idea with and without a publication id', async () => {
    const wrapper = mountIdeasView()
    await wrapper.find('[data-dnd-draggable="idea-1"]').trigger('click')
    await nextTick()
    ideasStore.convertIdea.mockResolvedValueOnce('publication-1').mockResolvedValueOnce(null)

    const convertButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.actions.convert'))
    expect(convertButton).toBeDefined()
    await convertButton!.trigger('click')
    await flushPromises()
    await convertButton!.trigger('click')
    await flushPromises()

    expect(ideasStore.convertIdea).toHaveBeenCalledTimes(2)
    expect(ideasStore.convertIdea).toHaveBeenNthCalledWith(1, 'idea-1')
    expect(ideasStore.convertIdea).toHaveBeenNthCalledWith(2, 'idea-1')
    expect(toast.success).toHaveBeenNthCalledWith(1, 'ideas.toasts.convertedWithId')
    expect(toast.success).toHaveBeenNthCalledWith(2, 'ideas.toasts.converted')
  })

  it('reports detail action failures without closing the selected idea', async () => {
    const wrapper = mountIdeasView()
    await wrapper.find('[data-dnd-draggable="idea-1"]').trigger('click')
    await nextTick()
    ideasStore.updateIdea.mockRejectedValueOnce(new Error('update failed'))
    ideasStore.deleteIdea.mockRejectedValueOnce(new Error('delete failed'))
    ideasStore.convertIdea.mockRejectedValueOnce(new Error('convert failed'))

    const saveButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.actions.save'))
    const deleteButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.actions.delete'))
    const convertButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.actions.convert'))
    expect(saveButton).toBeDefined()
    expect(deleteButton).toBeDefined()
    expect(convertButton).toBeDefined()

    await saveButton!.trigger('click')
    await deleteButton!.trigger('click')
    await convertButton!.trigger('click')
    await flushPromises()

    expect(toast.error).toHaveBeenCalledWith('update failed')
    expect(toast.error).toHaveBeenCalledWith('delete failed')
    expect(toast.error).toHaveBeenCalledWith('convert failed')
    expect(wrapper.find('#idea-detail-title').exists()).toBe(true)
  })

  it('edits, adds, reorders, removes, and saves board columns', async () => {
    const wrapper = mountIdeasView()
    const settingsButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.columns.button'))
    expect(settingsButton).toBeDefined()
    await settingsButton!.trigger('click')
    await nextTick()

    await wrapper.find('#idea-column-name-raw').setValue('Inbox')
    await wrapper.find('#idea-column-color-raw').setValue('#22c55e')
    await wrapper.find('#idea-new-column-name').setValue('Review')
    await wrapper.find('#idea-new-column-color').setValue('#3b82f6')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.columns.add'))
      ?.trigger('click')

    expect(ideasStore.createLocalColumn).toHaveBeenCalledWith('Review', '#3b82f6')
    expect(wrapper.find('#idea-column-name-new-column').exists()).toBe(true)

    const rawRow = wrapper.find('#idea-column-name-raw').element.parentElement
    expect(rawRow).toBeTruthy()
    await rawRow!.querySelectorAll('button')[1]!.dispatchEvent(new Event('click'))
    await nextTick()
    expect(wrapper.findAll('input[id^="idea-column-name-"]')[0]!.attributes('id')).toBe(
      'idea-column-name-done',
    )
    const doneRow = wrapper.find('#idea-column-name-done').element.parentElement
    expect(doneRow).toBeTruthy()
    await doneRow!.querySelectorAll('button')[1]!.dispatchEvent(new Event('click'))
    await nextTick()
    expect(wrapper.findAll('input[id^="idea-column-name-"]')[0]!.attributes('id')).toBe(
      'idea-column-name-raw',
    )

    const newColumnRow = wrapper.find('#idea-column-name-new-column').element.parentElement
    expect(newColumnRow).toBeTruthy()
    await newColumnRow!.querySelectorAll('button')[2]!.dispatchEvent(new Event('click'))
    await nextTick()
    ideasStore.updateColumns.mockResolvedValue(undefined)
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('common.save'))
      ?.trigger('click')
    await flushPromises()

    expect(ideasStore.updateColumns).toHaveBeenCalledWith([
      { id: 'raw', name: 'Inbox', order: 0, color: '#22c55e' },
      { id: 'done', name: 'Done', order: 1, color: '' },
    ])
    expect(toast.success).toHaveBeenCalledWith('ideas.toasts.columnsUpdated')
  })

  it('keeps the final column and reports column-save failures', async () => {
    ideasStore.orderedColumns = [{ id: 'raw', name: 'Raw', order: 0, color: null }]
    ideasStore.ideasByColumn = { raw: [...ideasStore.ideas] }
    const wrapper = mountIdeasView()
    const settingsButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('ideas.columns.button'))
    expect(settingsButton).toBeDefined()
    await settingsButton!.trigger('click')
    await nextTick()

    const row = wrapper.find('#idea-column-name-raw').element.parentElement
    expect(row).toBeTruthy()
    await row!.querySelectorAll('button')[2]!.dispatchEvent(new Event('click'))
    await nextTick()
    expect(toast.error).toHaveBeenCalledWith('ideas.columns.minimumOne')
    expect(wrapper.find('#idea-column-name-raw').exists()).toBe(true)

    ideasStore.updateColumns.mockRejectedValueOnce(new Error('columns failed'))
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('common.save'))
      ?.trigger('click')
    await flushPromises()

    expect(toast.error).toHaveBeenCalledWith('columns failed')
  })

  it('registers drag sources and moves ideas across columns or within a column', async () => {
    const first = makeTestIdea('idea-1', 'raw', 0)
    const second = makeTestIdea('idea-2', 'raw', 1)
    const third = makeTestIdea('idea-3', 'done', 0)
    ideasStore.ideas = [first, second, third]
    ideasStore.ideasByColumn = { raw: [first, second], done: [third] }
    ideasStore.moveIdea.mockResolvedValue(undefined)

    const wrapper = mountIdeasView()
    await flushPromises()
    const monitor = latestMonitorOptions()

    monitor.onDragStart({ source: { data: { ideaId: 'idea-2' } } })
    await nextTick()
    expect(wrapper.find('[data-dnd-draggable="idea-2"]').classes()).toContain('opacity-50')

    const draggable = dnd.draggableOptions
      .map((options) => options as { getInitialData: () => Record<string, unknown> })
      .find((options) => options.getInitialData().ideaId === 'idea-2')
    expect(draggable?.getInitialData()).toEqual({ ideaId: 'idea-2', columnId: 'raw' })

    const doneColumn = dnd.dropTargetOptions
      .map((options) => options as { getData: () => Record<string, unknown> })
      .find(
        (options) => options.getData().kind === 'column' && options.getData().columnId === 'done',
      )
    expect(doneColumn?.getData()).toEqual({ kind: 'column', columnId: 'done' })

    await monitor.onDrop(
      dropEvent(
        { ideaId: 'idea-2' },
        [{ data: { kind: 'card', columnId: 'raw', ideaId: 'idea-1' } }],
        -1,
      ),
    )
    await monitor.onDrop(dropEvent({ ideaId: 'idea-1' }, [{ data: doneColumn!.getData() }]))

    expect(ideasStore.moveIdea).toHaveBeenNthCalledWith(1, 'idea-2', {
      columnId: 'raw',
      orderInColumn: 0,
    })
    expect(ideasStore.moveIdea).toHaveBeenNthCalledWith(2, 'idea-1', {
      columnId: 'done',
      orderInColumn: 1,
    })

    const activeCleanups = dnd.cleanups.filter((cleanup) => cleanup.mock.calls.length === 0)
    wrapper.unmount()
    expect(activeCleanups.length).toBeGreaterThan(0)
    expect(activeCleanups.every((cleanup) => cleanup.mock.calls.length === 1)).toBe(true)
  })

  it('ignores invalid, missing, and no-op drag/drop events', async () => {
    const first = makeTestIdea('idea-1', 'raw', 0)
    const second = makeTestIdea('idea-2', 'raw', 1)
    ideasStore.ideas = [first, second]
    ideasStore.ideasByColumn = { raw: [first, second], done: [] }
    ideasStore.moveIdea.mockResolvedValue(undefined)

    const wrapper = mountIdeasView()
    await flushPromises()
    const monitor = latestMonitorOptions()

    await monitor.onDrop(dropEvent({}, [{ data: { kind: 'column', columnId: 'raw' } }]))
    await monitor.onDrop(
      dropEvent({ ideaId: 'missing' }, [{ data: { kind: 'column', columnId: 'raw' } }]),
    )
    await monitor.onDrop(dropEvent({ ideaId: 'idea-1' }, []))
    await monitor.onDrop(
      dropEvent({ ideaId: 'idea-1' }, [{ data: { kind: 'unsupported', columnId: 'raw' } }]),
    )
    await monitor.onDrop(
      dropEvent({ ideaId: 'idea-1' }, [
        { data: { kind: 'card', columnId: 'raw', ideaId: 'idea-1' } },
      ]),
    )

    expect(ideasStore.moveIdea).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
