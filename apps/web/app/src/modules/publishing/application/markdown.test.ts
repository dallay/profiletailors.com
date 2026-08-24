import { describe, it, expect } from 'vitest'
import {
  stripMarkdownToPlainText,
  applyInlineFormat,
  applyLinePrefix,
  applyHeading,
  applyLinkFormat,
  normalizeForSubmission,
} from './markdown'

describe('stripMarkdownToPlainText', () => {
  it('passes plain text through unchanged', () => {
    expect(stripMarkdownToPlainText('Hello world')).toBe('Hello world')
  })

  it('returns empty string for empty input', () => {
    expect(stripMarkdownToPlainText('')).toBe('')
  })

  it('strips bold markers', () => {
    expect(stripMarkdownToPlainText('This is **bold** text')).toBe('This is bold text')
  })

  it('strips italic markers', () => {
    expect(stripMarkdownToPlainText('This is *italic* text')).toBe('This is italic text')
  })

  it('strips strikethrough markers', () => {
    expect(stripMarkdownToPlainText('This is ~~deleted~~ text')).toBe('This is deleted text')
  })

  it('strips inline code markers', () => {
    expect(stripMarkdownToPlainText('Use `npm install` to install')).toBe(
      'Use npm install to install',
    )
  })

  it('strips heading markers', () => {
    expect(stripMarkdownToPlainText('# Heading 1')).toBe('Heading 1')
    expect(stripMarkdownToPlainText('## Heading 2')).toBe('Heading 2')
    expect(stripMarkdownToPlainText('### Heading 3')).toBe('Heading 3')
  })

  it('strips block quote markers', () => {
    expect(stripMarkdownToPlainText('> Quoted text')).toBe('Quoted text')
  })

  it('strips unordered list markers', () => {
    expect(stripMarkdownToPlainText('- List item')).toBe('List item')
    expect(stripMarkdownToPlainText('* List item')).toBe('List item')
  })

  it('strips ordered list markers', () => {
    expect(stripMarkdownToPlainText('1. First item')).toBe('First item')
    expect(stripMarkdownToPlainText('10. Tenth item')).toBe('Tenth item')
  })

  it('converts markdown links to label and url', () => {
    expect(stripMarkdownToPlainText('[Profile Tailors](https://profiletailors.com)')).toBe(
      'Profile Tailors (https://profiletailors.com)',
    )
  })

  it('converts image markdown to alt text', () => {
    expect(stripMarkdownToPlainText('![Alt description](https://example.com/img.png)')).toBe(
      'Alt description',
    )
  })

  it('preserves link when label equals url', () => {
    expect(stripMarkdownToPlainText('[https://example.com](https://example.com)')).toBe(
      'https://example.com',
    )
  })

  it('preserves link when label is empty', () => {
    expect(stripMarkdownToPlainText('[](https://example.com)')).toBe('https://example.com')
  })

  it('drops links with unsafe url schemes', () => {
    expect(stripMarkdownToPlainText('[click](javascript:alert(1))')).toBe('click')
    expect(stripMarkdownToPlainText('[click](data:text/html,<script>))')).toBe('click)')
  })

  it('keeps www. links', () => {
    expect(stripMarkdownToPlainText('[visit](www.example.com)')).toBe('visit (www.example.com)')
  })

  it('keeps mailto links', () => {
    expect(stripMarkdownToPlainText('[email](mailto:hi@example.com)')).toBe(
      'email (mailto:hi@example.com)',
    )
  })

  it('handles multiline markdown content', () => {
    const input = '# Title\n\n**Bold** paragraph\n\n- Item 1\n- Item 2'
    const expected = 'Title\n\nBold paragraph\n\nItem 1\nItem 2'
    expect(stripMarkdownToPlainText(input)).toBe(expected)
  })

  it('preserves fenced code block contents', () => {
    const input = '```\n**not bold**\n```\n'
    expect(stripMarkdownToPlainText(input)).toBe('**not bold**\n')
  })

  it('handles multiple inline formats on one line', () => {
    expect(stripMarkdownToPlainText('**bold** and *italic* and `code`')).toBe(
      'bold and italic and code',
    )
  })

  it('does not strip marker-like text that is not wrapping', () => {
    expect(stripMarkdownToPlainText('3 * 4 = 12')).toBe('3 * 4 = 12')
  })
})

describe('applyInlineFormat', () => {
  it('wraps selected text with markers', () => {
    const result = applyInlineFormat('Hello world', 0, 5, '**')
    expect(result.text).toBe('**Hello** world')
    expect(result.selectionStart).toBe(2)
    expect(result.selectionEnd).toBe(7)
  })

  it('inserts placeholder when no selection', () => {
    const result = applyInlineFormat('Hello world', 5, 5, '*')
    expect(result.text).toBe('Hello*text* world')
    expect(result.selectionStart).toBe(6)
    expect(result.selectionEnd).toBe(10)
  })

  it('toggles off when selection is already wrapped', () => {
    const result = applyInlineFormat('**Hello** world', 2, 7, '**')
    expect(result.text).toBe('Hello world')
    expect(result.selectionStart).toBe(0)
    expect(result.selectionEnd).toBe(5)
  })

  it('inserts placeholder when caret is inside wrapped text', () => {
    const result = applyInlineFormat('**Hello**', 4, 4, '**')
    expect(result.text).toBe('**He**text**llo**')
    expect(result.selectionStart).toBe(6)
    expect(result.selectionEnd).toBe(10)
  })
})

describe('applyLinePrefix', () => {
  it('adds prefix to a single line', () => {
    const result = applyLinePrefix('Hello world', 0, 5, '> ')
    expect(result.text).toBe('> Hello world')
    expect(result.selectionStart).toBe(2)
    expect(result.selectionEnd).toBe(7)
  })

  it('removes prefix when already present on single line', () => {
    const result = applyLinePrefix('> Hello world', 2, 7, '> ')
    expect(result.text).toBe('Hello world')
    expect(result.selectionStart).toBe(0)
    expect(result.selectionEnd).toBe(5)
  })

  it('adds prefix to multiple selected lines', () => {
    const result = applyLinePrefix('one\ntwo\nthree', 0, 9, '- ')
    expect(result.text).toBe('- one\n- two\n- three')
    expect(result.selectionStart).toBe(0)
    expect(result.selectionEnd).toBe(19)
  })

  it('removes prefix from multiple lines when all have it', () => {
    const result = applyLinePrefix('- one\n- two\nthree', 0, 9, '- ')
    expect(result.text).toBe('one\ntwo\nthree')
    expect(result.selectionStart).toBe(0)
    expect(result.selectionEnd).toBe(7)
  })
})

describe('applyHeading', () => {
  it('adds heading to a plain line', () => {
    const result = applyHeading('My heading', 0, 11)
    expect(result.text).toBe('# My heading')
  })

  it('cycles from H1 to H2', () => {
    const result = applyHeading('# My heading', 0, 11)
    expect(result.text).toBe('## My heading')
  })

  it('cycles from H2 to H3', () => {
    const result = applyHeading('## My heading', 0, 11)
    expect(result.text).toBe('### My heading')
  })

  it('removes heading at H3 (cycles back to plain)', () => {
    const result = applyHeading('### My heading', 0, 11)
    expect(result.text).toBe('My heading')
  })

  it('does not treat # inside a word as a heading', () => {
    const result = applyHeading('use C# carefully', 0, 4)
    expect(result.text).toBe('# use C# carefully')
  })
})

describe('applyLinkFormat', () => {
  it('wraps selected text as a link with url placeholder', () => {
    const result = applyLinkFormat('Click here', 0, 5)
    expect(result.text).toBe('[Click](https://) here')
    expect(result.selectionStart).toBe(8)
    expect(result.selectionEnd).toBe(16)
  })

  it('inserts placeholder text when no selection', () => {
    const result = applyLinkFormat('Hello world', 5, 5)
    expect(result.text).toBe('Hello[Link text](https://) world')
    expect(result.selectionStart).toBe(17)
    expect(result.selectionEnd).toBe(25)
  })
})

describe('normalizeForSubmission', () => {
  it('strips markdown markers', () => {
    expect(normalizeForSubmission('**bold** and *italic*')).toBe('bold and italic')
  })

  it('trims leading and trailing whitespace', () => {
    expect(normalizeForSubmission('  Hello world  ')).toBe('Hello world')
  })

  it('normalizes hashtags to lowercase', () => {
    expect(normalizeForSubmission('#HELLO #WORLD')).toBe('#hello #world')
  })

  it('removes invalid characters from hashtags', () => {
    expect(normalizeForSubmission('#hello-world! #test')).toBe('#helloworld #test')
  })

  it('collapses excessive newlines to maximum two', () => {
    expect(normalizeForSubmission('Hello\n\n\n\n\nWorld')).toBe('Hello\n\nWorld')
  })

  it('preserves single and double newlines', () => {
    expect(normalizeForSubmission('Hello\nWorld\n\nFoo\nBar')).toBe('Hello\nWorld\n\nFoo\nBar')
  })

  it('combines markdown stripping, hashtag normalization, and whitespace cleanup', () => {
    expect(normalizeForSubmission('  #HELLO\n\n\n\n  #WORLD  ')).toBe('#hello\n\n#world')
  })

  it('strips markdown links with safe urls', () => {
    expect(normalizeForSubmission('[Profile Tailors](https://profiletailors.com)')).toBe(
      'Profile Tailors (https://profiletailors.com)',
    )
  })

  it('drops unsafe url schemes during submission normalization', () => {
    expect(normalizeForSubmission('[click](javascript:alert(1))')).toBe('click')
    expect(normalizeForSubmission('[x](data:text/html,<script>))')).toBe('x)')
  })

  it('strips heading, list, and blockquote markers', () => {
    expect(normalizeForSubmission('# Title\n- item\n> quote')).toBe('Title\nitem\nquote')
  })

  it('handles empty input', () => {
    expect(normalizeForSubmission('')).toBe('')
  })

  it('handles whitespace-only input', () => {
    expect(normalizeForSubmission('   \n\n  \n  ')).toBe('')
  })
})
