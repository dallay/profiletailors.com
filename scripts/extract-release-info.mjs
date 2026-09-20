#!/usr/bin/env node
import { execSync } from 'node:child_process'
import { appendFileSync } from 'node:fs'

export function stripScopeFromTag(tag, scope) {
  if (!tag) return ''
  const prefix = `${scope}@`
  if (tag.startsWith(prefix)) {
    return tag.slice(prefix.length)
  }
  return tag
}

export function shortSha(sha) {
  if (!sha || typeof sha !== 'string') return 'local'
  const trimmed = sha.trim()
  if (!trimmed) return 'local'
  return trimmed.slice(0, 7)
}

export function resolveReleaseSha({ provided, tag, cwd = process.cwd() } = {}) {
  const trimmedProvided = provided?.trim()
  if (trimmedProvided) {
    return trimmedProvided
  }

  const targetRef = tag ? `${tag}^{commit}` : 'HEAD'
  try {
    return execSync(`git rev-parse "${targetRef}"`, {
      cwd,
      stdio: ['ignore', 'pipe', 'ignore'],
    })
      .toString()
      .trim()
  } catch {
    try {
      return execSync('git rev-parse HEAD', {
        cwd,
        stdio: ['ignore', 'pipe', 'ignore'],
      })
        .toString()
        .trim()
    } catch {
      return '0000000000000000000000000000000000000000'
    }
  }
}

export function extractReleaseInfo({ tag = '', scope = '', provided = '', cwd } = {}) {
  const version = stripScopeFromTag(tag, scope)
  const gitSha = resolveReleaseSha({ provided, tag, cwd })
  const short = shortSha(gitSha)

  return {
    version,
    gitSha,
    shortSha: short,
    lines: [
      `version=${version}`,
      `git_sha=${gitSha}`,
      `short_sha=${short}`,
    ],
  }
}

function parseArgs(argv) {
  const args = {}
  for (let i = 0; i < argv.length; i += 1) {
    const current = argv[i]
    if (current.startsWith('--')) {
      const key = current.slice(2)
      const next = argv[i + 1]
      if (next && !next.startsWith('--')) {
        args[key] = next
        i += 1
      } else {
        args[key] = true
      }
    }
  }
  return args
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const args = parseArgs(process.argv.slice(2))
  const tag = typeof args.tag === 'string' ? args.tag : ''
  const scope = typeof args.scope === 'string' ? args.scope : ''
  const provided = typeof args.provided === 'string' ? args.provided : ''

  const info = extractReleaseInfo({ tag, scope, provided })

  const githubOutput = process.env.GITHUB_OUTPUT
  if (githubOutput) {
    appendFileSync(githubOutput, `${info.lines.join('\n')}\n`, 'utf8')
  }

  process.stdout.write(`${JSON.stringify(info, null, 2)}\n`)
}
