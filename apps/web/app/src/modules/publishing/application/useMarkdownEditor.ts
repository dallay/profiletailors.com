import { ref, watch, type Ref } from 'vue'
import {
  applyHeading as applyHeadingFormat,
  applyInlineFormat,
  applyLinePrefix,
  applyLinkFormat,
  normalizeForSubmission,
  stripMarkdownToPlainText,
  type FormatResult,
} from './markdown'

export type UseMarkdownEditorOptions = {
  postText: Ref<string>
}

export type UseMarkdownEditorResult = {
  textareaEl: Ref<HTMLTextAreaElement | null>
  applyBold: () => void
  applyItalic: () => void
  applyStrikethrough: () => void
  applyInlineCode: () => void
  applyHeading: () => void
  applyUnorderedList: () => void
  applyOrderedList: () => void
  applyBlockquote: () => void
  applyLink: () => void
  handleKeyDown: (event: KeyboardEvent) => void
  plainTextForPreview: Ref<string>
  plainTextForSubmit: () => string
  stripMarkdown: (text: string) => string
}

function applyFormat(
  postText: Ref<string>,
  textareaEl: Ref<HTMLTextAreaElement | null>,
  transform: (text: string, start: number, end: number) => FormatResult,
): void {
  const el = textareaEl.value
  const start = el?.selectionStart ?? postText.value.length
  const end = el?.selectionEnd ?? postText.value.length

  const result = transform(postText.value, start, end)
  postText.value = result.text
}

export function useMarkdownEditor(options: UseMarkdownEditorOptions): UseMarkdownEditorResult {
  const textareaEl = ref<HTMLTextAreaElement | null>(null)

  const plainTextForPreview = ref('')

  watch(
    () => options.postText.value,
    (text) => {
      plainTextForPreview.value = stripMarkdownToPlainText(text)
    },
    { immediate: true },
  )

  function applyBold(): void {
    applyFormat(options.postText, textareaEl, (text, start, end) =>
      applyInlineFormat(text, start, end, '**'),
    )
  }

  function applyItalic(): void {
    applyFormat(options.postText, textareaEl, (text, start, end) =>
      applyInlineFormat(text, start, end, '*'),
    )
  }

  function applyStrikethrough(): void {
    applyFormat(options.postText, textareaEl, (text, start, end) =>
      applyInlineFormat(text, start, end, '~~'),
    )
  }

  function applyInlineCode(): void {
    applyFormat(options.postText, textareaEl, (text, start, end) =>
      applyInlineFormat(text, start, end, '`'),
    )
  }

  function applyHeading(): void {
    applyFormat(options.postText, textareaEl, applyHeadingFormat)
  }

  function applyUnorderedList(): void {
    applyFormat(options.postText, textareaEl, (text, start, end) =>
      applyLinePrefix(text, start, end, '- '),
    )
  }

  function applyOrderedList(): void {
    applyFormat(options.postText, textareaEl, (text, start, end) =>
      applyLinePrefix(text, start, end, '1. '),
    )
  }

  function applyBlockquote(): void {
    applyFormat(options.postText, textareaEl, (text, start, end) =>
      applyLinePrefix(text, start, end, '> '),
    )
  }

  function applyLink(): void {
    applyFormat(options.postText, textareaEl, applyLinkFormat)
  }

  function handleKeyDown(event: KeyboardEvent): void {
    const isMod = event.metaKey || event.ctrlKey
    if (!isMod) return

    const key = event.key.toLowerCase()

    if (key === 'b') {
      event.preventDefault()
      applyBold()
      return
    }

    if (key === 'i') {
      event.preventDefault()
      applyItalic()
      return
    }

    if (event.shiftKey && key === 'x') {
      event.preventDefault()
      applyStrikethrough()
      return
    }

    if (event.shiftKey && key === 'k') {
      event.preventDefault()
      applyLink()
      return
    }
  }

  function plainTextForSubmit(): string {
    return normalizeForSubmission(options.postText.value)
  }

  function stripMarkdown(text: string): string {
    return stripMarkdownToPlainText(text)
  }

  return {
    textareaEl,
    applyBold,
    applyItalic,
    applyStrikethrough,
    applyInlineCode,
    applyHeading,
    applyUnorderedList,
    applyOrderedList,
    applyBlockquote,
    applyLink,
    handleKeyDown,
    plainTextForPreview,
    plainTextForSubmit,
    stripMarkdown,
  }
}
