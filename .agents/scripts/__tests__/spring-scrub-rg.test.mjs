import assert from 'node:assert/strict'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { test } from 'node:test'

const scrubber = path.resolve('.agents/scripts/spring-scrub-rg.mjs')

const runScrub = (target, args = []) => new Promise((resolve, reject) => {
  const child = spawn(process.execPath, [scrubber, ...args, target], { stdio: ['ignore', 'pipe', 'pipe'] })
  let stdout = ''
  let stderr = ''
  child.stdout.on('data', (chunk) => { stdout += chunk })
  child.stderr.on('data', (chunk) => { stderr += chunk })
  child.on('error', reject)
  child.on('close', (code) => resolve({ code, stdout, stderr }))
})

test('passes a reference file with no incompatible tokens', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'spring-scrub-'))
  await writeFile(path.join(root, 'clean.md'), '# Clean\n\n```kotlin\nfun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http.build()\n```\n')
  const result = await runScrub(root, [])
  assert.equal(result.code, 0)
  assert.match(result.stdout, /PASS/)
  await rm(root, { recursive: true, force: true })
})

test('flags MockMvc outside any legacy marker', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'spring-scrub-'))
  await writeFile(path.join(root, 'dirty.md'), '# Active\n\nUse `MockMvc` for tests.\n')
  const result = await runScrub(root, [])
  assert.equal(result.code, 1)
  assert.match(result.stdout, /FAIL: incompatible tokens present/)
  assert.match(result.stdout, /MockMvc/)
  await rm(root, { recursive: true, force: true })
})

test('ignores MockMvc when wrapped in a legacy:servlet marker', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'spring-scrub-'))
  await writeFile(
    path.join(root, 'migration.md'),
    '# Migration\n\n<!-- legacy:servlet -->\n```kotlin\nclass Cfg { fun bean(): SecurityFilterChain = TODO() }\n```\n<!-- /legacy:servlet -->\n',
  )
  const result = await runScrub(root, [])
  assert.equal(result.code, 0, `expected PASS but got ${result.code}\nstdout:${result.stdout}\nstderr:${result.stderr}`)
  await rm(root, { recursive: true, force: true })
})

test('does not bleed the marker scope across separate blocks', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'spring-scrub-'))
  await writeFile(
    path.join(root, 'mixed.md'),
    '# Mixed\n\n<!-- legacy:servlet -->\n```kotlin\nclass Legacy { fun bean(): SecurityFilterChain = TODO() }\n```\n<!-- /legacy:servlet -->\n\n```kotlin\nclass Active { fun bean(): MockMvc = TODO() }\n```\n',
  )
  const result = await runScrub(root, [])
  assert.equal(result.code, 1)
  assert.match(result.stdout, /MockMvc/)
  assert.doesNotMatch(result.stdout, /SecurityFilterChain/)
  await rm(root, { recursive: true, force: true })
})
