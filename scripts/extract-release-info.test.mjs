import assert from 'node:assert/strict'
import test from 'node:test'
import {
  extractReleaseInfo,
  resolveReleaseSha,
  shortSha,
  stripScopeFromTag,
} from './extract-release-info.mjs'

test('stripScopeFromTag strips component scope prefix', () => {
  assert.equal(stripScopeFromTag('app@0.3.9', 'app'), '0.3.9')
  assert.equal(stripScopeFromTag('landing@0.2.13', 'landing'), '0.2.13')
  assert.equal(stripScopeFromTag('admin@v0.0.8', 'admin'), 'v0.0.8')
  assert.equal(stripScopeFromTag('0.3.9', 'app'), '0.3.9')
  assert.equal(stripScopeFromTag('other@1.0.0', 'app'), 'other@1.0.0')
})

test('shortSha trims to 7 characters or returns fallback', () => {
  assert.equal(shortSha('94f443508f1f2ff469bd4ced602c476e2bbb3819'), '94f4435')
  assert.equal(shortSha('abc'), 'abc')
  assert.equal(shortSha(''), 'local')
  assert.equal(shortSha(undefined), 'local')
})

test('resolveReleaseSha uses provided sha when present', () => {
  const sha = '94f443508f1f2ff469bd4ced602c476e2bbb3819'
  assert.equal(resolveReleaseSha({ provided: sha, tag: 'app@0.3.9' }), sha)
})

test('resolveReleaseSha falls back to git rev-parse when provided is empty', () => {
  const resolved = resolveReleaseSha({ provided: '', tag: 'HEAD' })
  assert.match(resolved, /^[0-9a-f]{40}$/)
})

test('extractReleaseInfo returns structured object and github output lines', () => {
  const sha = '94f443508f1f2ff469bd4ced602c476e2bbb3819'
  const info = extractReleaseInfo({
    tag: 'app@0.3.9',
    scope: 'app',
    provided: sha,
  })

  assert.equal(info.version, '0.3.9')
  assert.equal(info.gitSha, sha)
  assert.equal(info.shortSha, '94f4435')
  assert.deepEqual(info.lines, [
    'version=0.3.9',
    `git_sha=${sha}`,
    'short_sha=94f4435',
  ])
})
