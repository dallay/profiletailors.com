#!/usr/bin/env node
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, extname, relative, resolve } from 'node:path'
import process from 'node:process'

const DEFAULT_ALLOWLIST = resolve(dirname(new URL(import.meta.url).pathname), 'skill-comment-allowlist.json')
const CODE_EXTENSIONS = new Set(['.js', '.mjs', '.ts', '.tsx', '.vue', '.astro', '.kt', '.kts'])
const HASH_EXTENSIONS = new Set(['.py', '.sh'])
const MARKDOWN_EXTENSIONS = new Set(['.md'])
const DEFAULT_SCOPE = ['**/SKILL.md']
const MARKER_PATTERNS = [
  /^\s*#!\//,
  /^\s*(?:\/\/|#|<!--)\s*SPDX-License-Identifier:/,
  /^\s*(?:\/\/|#|<!--)\s*(?:AUTO-GENERATED|@generated)\b/i,
]
const POLICY_PATTERNS = [
  ['TODO', /\bTODO\b/],
  ['FIXME', /\bFIXME\b/],
  ['HACK', /\bHACK\b/],
  ['suppress', /@(?:file:)?Suppress\b/],
  ['biome-ignore', /biome-ignore/],
  ['eslint-disable', /eslint-disable/],
  ['typescript-suppression', /@ts-(?:ignore|expect-error|nocheck)\b/],
  ['nolint', /\/\/nolint\b/],
  ['detekt-disable', /detekt-disable/],
  ['ktlint-disable', /ktlint-disable/],
  ['spotless-off', /spotless:off/],
]

const parseArguments = (args) => {
  const options = { paths: [], allowlist: DEFAULT_ALLOWLIST, json: false, all: false }
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index]
    if (argument === '--paths') {
      const value = args[index + 1]
      if (!value) throw new Error('--paths requires a directory or file')
      options.paths.push(value)
      index += 1
    } else if (argument === '--allowlist') {
      const value = args[index + 1]
      if (!value) throw new Error('--allowlist requires a JSON file')
      options.allowlist = resolve(value)
      index += 1
    } else if (argument === '--json') {
      options.json = true
    } else if (argument === '--all') {
      options.all = true
    } else if (argument === '--help') {
      options.help = true
    } else {
      throw new Error(`Unknown argument: ${argument}`)
    }
  }
  if (!options.help && options.paths.length === 0) throw new Error('At least one --paths value is required')
  return options
}

const printUsage = () => {
  process.stdout.write('Usage: skill-comment-scan.mjs --paths <dir> [--paths <dir>] [--allowlist <json>] [--json] [--all]\n')
}

const collectFiles = (entry, all) => {
  const target = resolve(entry)
  if (!existsSync(target)) throw new Error(`Path not found: ${entry}`)
  const information = statSync(target)
  if (information.isFile()) return [target]
  const files = readdirSync(target, { withFileTypes: true }).flatMap((child) => collectFiles(resolve(target, child.name), all))
  if (all) return files
  const hasSkillDocument = files.some((file) => file.endsWith('/SKILL.md'))
  if (!hasSkillDocument) return files
  return files.filter((file) => DEFAULT_SCOPE.some((pattern) => globToRegex(pattern).test(relative(target, file).replaceAll('\\', '/'))))
}

const escapeRegex = (value) => value.replace(/[.+^${}()|[\]\\]/g, '\\$&')
const globToRegex = (pattern) => {
  const normalized = pattern.replaceAll('\\', '/')
  let expression = ''
  for (let index = 0; index < normalized.length; index += 1) {
    const character = normalized[index]
    if (character === '*' && normalized[index + 1] === '*' && normalized[index + 2] === '/') {
      expression += '(?:.*/)?'
      index += 2
    } else if (character === '*' && normalized[index + 1] === '*') {
      expression += '.*'
      index += 1
    } else if (character === '*') {
      expression += '[^/]*'
    } else {
      expression += escapeRegex(character)
    }
  }
  return new RegExp(`^${expression}$`)
}

const loadAllowlist = (file) => {
  if (!existsSync(file)) return []
  const entries = JSON.parse(readFileSync(file, 'utf8'))
  if (!Array.isArray(entries)) throw new Error('Allowlist must be an array')
  return entries.map((entry) => ({ pattern: String(entry.path), reason: String(entry.reason || '') }))
}

const isAllowlisted = (file, root, entries) => {
  const relativePath = relative(root, file).replaceAll('\\', '/')
  return entries.some(({ pattern }) => {
    const candidate = pattern.replaceAll('\\', '/')
    const targets = candidate.includes('/') ? [relativePath] : [relativePath, relativePath.split('/').at(-1)]
    return targets.some((target) => globToRegex(candidate).test(target))
  })
}

const isMarker = (line) => MARKER_PATTERNS.some((pattern) => pattern.test(line))
const isCommentCapable = (extension) => CODE_EXTENSIONS.has(extension)
const isHashCommentCapable = (extension) => HASH_EXTENSIONS.has(extension)
const isMarkdown = (extension) => MARKDOWN_EXTENSIONS.has(extension)

const scanFile = (file, root, allowlist) => {
  const relativePath = relative(root, file).replaceAll('\\', '/')
  if (isAllowlisted(file, root, allowlist)) return []
  const extension = extname(file).toLowerCase()
  const lines = readFileSync(file, 'utf8').split(/\r?\n/)
  const violations = []
  let inKdoc = false
  let inFence = false

  lines.forEach((line, index) => {
    const lineNumber = index + 1
    if (/^\s*```/.test(line)) {
      inFence = !inFence
      return
    }
    if (inFence) return
    const marker = isMarker(line)
    if (inKdoc && /^\s*\*/.test(line) && !/^\s*\*\//.test(line) && !marker) {
      violations.push({ path: relativePath, line: lineNumber, col: line.search(/\S|$/) + 1, patternId: 'kdoc-continuation', snippet: line.trim(), allowed: false })
    }
    if (line.includes('/**')) inKdoc = true
    if (inKdoc && line.includes('*/')) inKdoc = false

    if (!marker && isCommentCapable(extension) && /^\s*\/\//.test(line)) {
      violations.push({ path: relativePath, line: lineNumber, col: line.search(/\S|$/) + 1, patternId: 'line-comment', snippet: line.trim(), allowed: false })
    }
    if (!marker && isMarkdown(extension) && /<!--/.test(line)) {
      violations.push({ path: relativePath, line: lineNumber, col: line.indexOf('<!--') + 1, patternId: 'html-comment', snippet: line.trim(), allowed: false })
    }
    if (!marker && extension === '.astro' && /<!--/.test(line)) {
      violations.push({ path: relativePath, line: lineNumber, col: line.indexOf('<!--') + 1, patternId: 'html-comment', snippet: line.trim(), allowed: false })
    }
    if (!marker && isHashCommentCapable(extension) && /^\s*#[^!]/.test(line)) {
      violations.push({ path: relativePath, line: lineNumber, col: line.search(/\S|$/) + 1, patternId: 'hash-comment', snippet: line.trim(), allowed: false })
    }
    if (!marker) {
      for (const [patternId, pattern] of POLICY_PATTERNS) {
        const match = pattern.exec(line)
        if (match) violations.push({ path: relativePath, line: lineNumber, col: match.index + 1, patternId: 'policy-token', token: patternId, snippet: line.trim(), allowed: false })
      }
    }
  })

  return violations
}

const run = () => {
  const options = parseArguments(process.argv.slice(2))
  if (options.help) {
    printUsage()
    return 0
  }
  const root = process.cwd()
  const allowlist = loadAllowlist(options.allowlist)
  const files = [...new Set(options.paths.flatMap((entry) => collectFiles(entry, options.all)))]
  const violations = files.flatMap((file) => scanFile(file, root, allowlist))
  const report = { violations }
  if (options.json) {
    process.stdout.write(`${JSON.stringify({ ...report, scannedFiles: files.map((file) => relative(root, file).replaceAll('\\', '/')) }, null, 2)}\n`)
  } else if (violations.length > 0) {
    violations.forEach((violation) => process.stdout.write(`${violation.path}:${violation.line}:${violation.col} ${violation.patternId} ${violation.snippet}\n`))
  } else {
    process.stdout.write('clean\n')
  }
  return violations.length === 0 ? 0 : 1
}

try {
  process.exitCode = run()
} catch (error) {
  process.stderr.write(`${error.message}\n`)
  printUsage()
  process.exitCode = 2
}
