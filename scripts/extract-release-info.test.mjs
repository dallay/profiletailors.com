import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import {
  extractReleaseInfo,
  resolveReleaseSha,
  shortSha,
  stripScopeFromTag,
} from './extract-release-info.mjs'

const scriptPath = fileURLToPath(new URL('./extract-release-info.mjs', import.meta.url))

function createRepository(t) {
  const cwd = mkdtempSync(join(tmpdir(), 'extract-release-info-'))
  t.after(() => rmSync(cwd, { recursive: true, force: true }))

  execFileSync('git', ['init', '--quiet'], { cwd })
  execFileSync('git', ['config', 'user.name', 'Release Test'], { cwd })
  execFileSync('git', ['config', 'user.email', 'release-test@example.com'], {
    cwd,
  })
  writeFileSync(join(cwd, 'release.txt'), 'release\n')
  execFileSync('git', ['add', 'release.txt'], { cwd })
  execFileSync('git', ['commit', '--quiet', '-m', 'release'], { cwd })

  const sha = execFileSync('git', ['rev-parse', 'HEAD'], {
    cwd,
    encoding: 'utf8',
  }).trim()
  execFileSync('git', ['tag', 'app@1.2.3'], { cwd })

  return { cwd, sha }
}

test('stripScopeFromTag removes only the matching component prefix', () => {
  assert.equal(stripScopeFromTag('app@0.3.9', 'app'), '0.3.9')
  assert.equal(stripScopeFromTag('landing@0.2.13', 'landing'), '0.2.13')
  assert.equal(stripScopeFromTag('admin@v0.0.8', 'admin'), 'v0.0.8')
  assert.equal(stripScopeFromTag('0.3.9', 'app'), '0.3.9')
  assert.equal(stripScopeFromTag('other@1.0.0', 'app'), 'other@1.0.0')
  assert.equal(stripScopeFromTag('app@app@1.0.0', 'app'), 'app@1.0.0')
  assert.equal(stripScopeFromTag('', 'app'), '')
})

test('shortSha normalizes whitespace and limits the result to seven characters', () => {
  assert.equal(shortSha('94f443508f1f2ff469bd4ced602c476e2bbb3819'), '94f4435')
  assert.equal(shortSha('  94f443508f1f2ff469bd4ced602c476e2bbb3819\n'), '94f4435')
  assert.equal(shortSha('abc'), 'abc')
  assert.equal(shortSha(''), 'local')
  assert.equal(shortSha('   '), 'local')
  assert.equal(shortSha(undefined), 'local')
  assert.equal(shortSha(1234567), 'local')
})

test('resolveReleaseSha uses and trims a provided sha without invoking git', () => {
  const sha = '94f443508f1f2ff469bd4ced602c476e2bbb3819'
  const missingDirectory = join(tmpdir(), 'extract-release-info-missing-directory')

  assert.equal(
    resolveReleaseSha({
      provided: `  ${sha}\n`,
      tag: 'app@0.3.9',
      cwd: missingDirectory,
    }),
    sha,
  )
})

test('resolveReleaseSha resolves the tagged release commit', (t) => {
  const { cwd, sha } = createRepository(t)

  assert.equal(resolveReleaseSha({ provided: '', tag: 'app@1.2.3', cwd }), sha)
})

test('resolveReleaseSha falls back to HEAD when the release tag is unavailable', (t) => {
  const { cwd, sha } = createRepository(t)

  assert.equal(resolveReleaseSha({ provided: '', tag: 'app@9.9.9', cwd }), sha)
  assert.equal(resolveReleaseSha({ cwd }), sha)
})

test('resolveReleaseSha returns the zero sha outside a git repository', (t) => {
  const cwd = mkdtempSync(join(tmpdir(), 'extract-release-info-no-git-'))
  t.after(() => rmSync(cwd, { recursive: true, force: true }))

  assert.equal(
    resolveReleaseSha({ provided: '', tag: 'app@1.2.3', cwd }),
    '0000000000000000000000000000000000000000',
  )
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
  assert.deepEqual(info.lines, ['version=0.3.9', `git_sha=${sha}`, 'short_sha=94f4435'])
})

test('extractReleaseInfo derives metadata from the release tag when sha is omitted', (t) => {
  const { cwd, sha } = createRepository(t)

  assert.deepEqual(extractReleaseInfo({ tag: 'app@1.2.3', scope: 'app', cwd }), {
    version: '1.2.3',
    gitSha: sha,
    shortSha: sha.slice(0, 7),
    lines: ['version=1.2.3', `git_sha=${sha}`, `short_sha=${sha.slice(0, 7)}`],
  })
})

test('command line interface prints metadata and appends GitHub outputs', (t) => {
  const cwd = mkdtempSync(join(tmpdir(), 'extract-release-info-cli-'))
  t.after(() => rmSync(cwd, { recursive: true, force: true }))
  const outputPath = join(cwd, 'github-output')
  const sha = '94f443508f1f2ff469bd4ced602c476e2bbb3819'
  writeFileSync(outputPath, 'existing=value\n')

  const result = spawnSync(
    process.execPath,
    [scriptPath, '--tag', 'admin@v1.2.3', '--scope', 'admin', '--provided', sha],
    {
      cwd,
      encoding: 'utf8',
      env: { ...process.env, GITHUB_OUTPUT: outputPath },
    },
  )

  assert.equal(result.status, 0)
  assert.equal(result.stderr, '')
  assert.deepEqual(JSON.parse(result.stdout), {
    version: 'v1.2.3',
    gitSha: sha,
    shortSha: '94f4435',
    lines: ['version=v1.2.3', `git_sha=${sha}`, 'short_sha=94f4435'],
  })
  assert.equal(
    readFileSync(outputPath, 'utf8'),
    ['existing=value', 'version=v1.2.3', `git_sha=${sha}`, 'short_sha=94f4435', ''].join('\n'),
  )
})

test('command line interface handles omitted option values without writing outputs', (t) => {
  const cwd = mkdtempSync(join(tmpdir(), 'extract-release-info-cli-empty-'))
  t.after(() => rmSync(cwd, { recursive: true, force: true }))

  const result = spawnSync(process.execPath, [scriptPath, '--tag', '--scope'], {
    cwd,
    encoding: 'utf8',
    env: { ...process.env, GITHUB_OUTPUT: '' },
  })

  assert.equal(result.status, 0)
  assert.equal(result.stderr, '')
  assert.deepEqual(JSON.parse(result.stdout), {
    version: '',
    gitSha: '0000000000000000000000000000000000000000',
    shortSha: '0000000',
    lines: ['version=', 'git_sha=0000000000000000000000000000000000000000', 'short_sha=0000000'],
  })
})
