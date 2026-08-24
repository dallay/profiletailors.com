import { describe, it, expect, vi } from 'vitest'
import { ref, nextTick } from 'vue'
import { useMarkdownEditor } from './useMarkdownEditor'

function setup(initialText = '') {
  const postText = ref(initialText)
  const editor = useMarkdownEditor({ postText })
  const fakeTextarea = {
    selectionStart: 0,
    selectionEnd: 0,
    focus: vi.fn(),
    setSelectionRange: vi.fn(),
  }
  ;(editor as unknown as { textareaEl: { value: unknown } }).textareaEl.value = fakeTextarea
  return { postText, editor, fakeTextarea }
}

describe('useMarkdownEditor', () => {
  it('initializes plainTextForPreview from initial text', () => {
    const { editor } = setup('**bold** text')
    expect(editor.plainTextForPreview.value).toBe('bold text')
  })

  it('updates plainTextForPreview when postText changes', async () => {
    const { postText, editor } = setup('')
    postText.value = '# Heading'
    await nextTick()
    expect(editor.plainTextForPreview.value).toBe('Heading')
  })

  it('applyBold wraps selected text', () => {
    const { postText, editor, fakeTextarea } = setup('Hello world')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.applyBold()
    expect(postText.value).toBe('**Hello** world')
  })

  it('applyBold inserts placeholder when no selection', () => {
    const { postText, editor, fakeTextarea } = setup('Hello world')
    fakeTextarea.selectionStart = 5
    fakeTextarea.selectionEnd = 5
    editor.applyBold()
    expect(postText.value).toBe('Hello**text** world')
  })

  it('applyItalic wraps selected text with single asterisk', () => {
    const { postText, editor, fakeTextarea } = setup('Hello')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.applyItalic()
    expect(postText.value).toBe('*Hello*')
  })

  it('applyStrikethrough wraps with tilde markers', () => {
    const { postText, editor, fakeTextarea } = setup('Hello')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.applyStrikethrough()
    expect(postText.value).toBe('~~Hello~~')
  })

  it('applyInlineCode wraps with backticks', () => {
    const { postText, editor, fakeTextarea } = setup('Hello')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.applyInlineCode()
    expect(postText.value).toBe('`Hello`')
  })

  it('applyHeading adds heading marker', () => {
    const { postText, editor, fakeTextarea } = setup('My title')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 8
    editor.applyHeading()
    expect(postText.value).toBe('# My title')
  })

  it('applyUnorderedList adds list prefix', () => {
    const { postText, editor, fakeTextarea } = setup('Item')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 4
    editor.applyUnorderedList()
    expect(postText.value).toBe('- Item')
  })

  it('applyOrderedList adds numbered prefix', () => {
    const { postText, editor, fakeTextarea } = setup('Item')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 4
    editor.applyOrderedList()
    expect(postText.value).toBe('1. Item')
  })

  it('applyBlockquote adds quote prefix', () => {
    const { postText, editor, fakeTextarea } = setup('Quote')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.applyBlockquote()
    expect(postText.value).toBe('> Quote')
  })

  it('applyLink wraps selected text as link', () => {
    const { postText, editor, fakeTextarea } = setup('Click here')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.applyLink()
    expect(postText.value).toBe('[Click](https://) here')
  })

  it('reads selection from the textarea element when formatting', () => {
    const { postText, editor, fakeTextarea } = setup('Hello world')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.applyBold()
    expect(postText.value).toBe('**Hello** world')
  })

  it('plainTextForSubmit strips markdown and trims', () => {
    const { editor } = setup('  **bold** and *italic*  ')
    expect(editor.plainTextForSubmit()).toBe('bold and italic')
  })

  it('plainTextForSubmit normalizes hashtags', () => {
    const { editor } = setup('#HELLO #WORLD')
    expect(editor.plainTextForSubmit()).toBe('#hello #world')
  })

  it('plainTextForSubmit collapses excessive newlines', () => {
    const { editor } = setup('Hello\n\n\n\n\nWorld')
    expect(editor.plainTextForSubmit()).toBe('Hello\n\nWorld')
  })

  it('plainTextForSubmit combines markdown stripping with hashtag normalization', () => {
    const { editor } = setup('  **#HELLO**\n\n\n  #WORLD  ')
    expect(editor.plainTextForSubmit()).toBe('#hello\n\n#world')
  })

  it('stripMarkdown delegates to stripMarkdownToPlainText', () => {
    const { editor } = setup()
    expect(editor.stripMarkdown('# Title')).toBe('Title')
    expect(editor.stripMarkdown('**bold**')).toBe('bold')
  })
})

describe('useMarkdownEditor — keyboard shortcuts', () => {
  function makeEvent(key: string, opts: { meta?: boolean; ctrl?: boolean; shift?: boolean } = {}) {
    return {
      key,
      metaKey: opts.meta ?? false,
      ctrlKey: opts.ctrl ?? false,
      shiftKey: opts.shift ?? false,
      preventDefault: vi.fn(),
    } as unknown as KeyboardEvent
  }

  it('Cmd/Ctrl+B triggers bold', () => {
    const { postText, editor, fakeTextarea } = setup('Hello')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.handleKeyDown(makeEvent('b', { meta: true }))
    expect(postText.value).toBe('**Hello**')
  })

  it('Cmd/Ctrl+I triggers italic', () => {
    const { postText, editor, fakeTextarea } = setup('Hello')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.handleKeyDown(makeEvent('i', { ctrl: true }))
    expect(postText.value).toBe('*Hello*')
  })

  it('Cmd/Ctrl+Shift+X triggers strikethrough', () => {
    const { postText, editor, fakeTextarea } = setup('Hello')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.handleKeyDown(makeEvent('x', { meta: true, shift: true }))
    expect(postText.value).toBe('~~Hello~~')
  })

  it('Cmd/Ctrl+Shift+K triggers link', () => {
    const { postText, editor, fakeTextarea } = setup('Click')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    editor.handleKeyDown(makeEvent('k', { meta: true, shift: true }))
    expect(postText.value).toBe('[Click](https://)')
  })

  it('does nothing for unmodified keys', () => {
    const { postText, editor } = setup('Hello')
    editor.handleKeyDown(makeEvent('b'))
    expect(postText.value).toBe('Hello')
  })

  it('prevents default for handled shortcuts', () => {
    const { editor, fakeTextarea } = setup('Hello')
    fakeTextarea.selectionStart = 0
    fakeTextarea.selectionEnd = 5
    const event = makeEvent('b', { meta: true })
    editor.handleKeyDown(event)
    expect(event.preventDefault).toHaveBeenCalled()
  })
})

describe('useMarkdownEditor — XSS safety', () => {
  it('plainTextForPreview strips script-like markdown', async () => {
    const { postText, editor } = setup()
    postText.value = '[click](javascript:alert(1))'
    await nextTick()
    expect(editor.plainTextForPreview.value).toBe('click')
  })

  it('plainTextForPreview strips data: URLs', async () => {
    const { postText, editor } = setup()
    postText.value = '[x](data:text/html,<script>alert(1)</script>)'
    await nextTick()
    expect(editor.plainTextForPreview.value).toBe('x')
  })

  it('plainTextForSubmit strips script-like markdown', () => {
    const { editor } = setup('[click](javascript:alert(1))')
    expect(editor.plainTextForSubmit()).toBe('click')
  })

  it('does not produce any HTML from markdown', () => {
    const { postText, editor } = setup()
    postText.value = '**bold** *italic* `code` # heading > quote'
    const result = editor.plainTextForPreview.value
    expect(result).not.toContain('<')
    expect(result).not.toContain('>')
  })
})
