import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { checkFiles, isHeaderStale, wholeDaysBetween } from './check-doc-last-updated.mjs'

const scriptPath = fileURLToPath(new URL('./check-doc-last-updated.mjs', import.meta.url))
const MS_PER_DAY = 24 * 60 * 60 * 1000

function isoDaysAgo(days) {
  return new Date(Date.now() - days * MS_PER_DAY).toISOString().slice(0, 10)
}

function createDocRepo(t) {
  const cwd = mkdtempSync(join(tmpdir(), 'check-doc-last-updated-'))
  t.after(() => rmSync(cwd, { recursive: true, force: true }))
  execFileSync('git', ['init', '--quiet'], { cwd })
  execFileSync('git', ['config', 'user.name', 'Doc Test'], { cwd })
  execFileSync('git', ['config', 'user.email', 'doc-test@example.com'], { cwd })
  mkdirSync(join(cwd, 'docs'), { recursive: true })
  return cwd
}

function commitDoc(cwd, headerDate, authorIso, committerIso = authorIso) {
  writeFileSync(join(cwd, 'docs', 'note.md'), `# Note\n\n**Last Updated: ${headerDate}\n`)
  execFileSync('git', ['add', 'docs/note.md'], { cwd })
  execFileSync('git', ['commit', '--quiet', '-m', 'docs: update note'], {
    cwd,
    env: {
      ...process.env,
      GIT_AUTHOR_DATE: authorIso,
      GIT_COMMITTER_DATE: committerIso,
    },
  })
}

function runScript(cwd) {
  return spawnSync('node', [scriptPath], { cwd, encoding: 'utf-8' })
}

test('wholeDaysBetween counts calendar days', () => {
  assert.equal(wholeDaysBetween('2026-09-28', '2026-09-28'), 0)
  assert.equal(wholeDaysBetween('2026-09-28', '2026-09-29'), 1)
  assert.equal(wholeDaysBetween('2026-09-29', '2026-09-28'), -1)
})

test('isHeaderStale forgives same-day and next-day headers', () => {
  assert.equal(isHeaderStale('2026-09-29', '2026-09-29'), false)
  assert.equal(isHeaderStale('2026-09-28', '2026-09-29'), false)
  assert.equal(isHeaderStale('2026-09-26', '2026-09-29'), false)
})

test('isHeaderStale flags headers older than the grace window', () => {
  assert.equal(isHeaderStale('2026-09-25', '2026-09-29'), true)
  assert.equal(isHeaderStale('2026-09-19', '2026-09-29'), true)
})

test('checkFiles skips files without header or without git history', () => {
  const stale = checkFiles(['missing-header.md', 'untracked.md'], {
    getHeaderDate: (file) => (file === 'untracked.md' ? '2020-01-01' : null),
    getAuthorDate: () => '',
  })
  assert.deepEqual(stale, [])
})

test('header edited late at night and committed after midnight passes', (t) => {
  const cwd = createDocRepo(t)
  commitDoc(cwd, isoDaysAgo(1), new Date().toISOString())
  const result = runScript(cwd)
  assert.equal(result.status, 0)
  assert.match(result.stdout, /valid and up to date/)
})

test('rebase that rewrites the committer date does not fail the check', (t) => {
  const cwd = createDocRepo(t)
  const yesterday = new Date(Date.now() - MS_PER_DAY).toISOString()
  commitDoc(cwd, isoDaysAgo(1), yesterday, new Date().toISOString())
  const result = runScript(cwd)
  assert.equal(result.status, 0)
})

test('header lagging ten days behind the change fails the check', (t) => {
  const cwd = createDocRepo(t)
  commitDoc(cwd, isoDaysAgo(10), new Date().toISOString())
  const result = runScript(cwd)
  assert.equal(result.status, 1)
  assert.match(result.stderr, /Stale 'Last Updated' date in/)
})
