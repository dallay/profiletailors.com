import assert from 'node:assert/strict'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { test } from 'node:test'

const doctor = path.resolve('.agents/scripts/skill-doctor.mjs')

const runDoctor = (skillsDir, args = []) => new Promise((resolve, reject) => {
  const child = spawn(process.execPath, [doctor, '--skills-dir', skillsDir, ...args, '--json'], { stdio: ['ignore', 'pipe', 'pipe'] })
  let stdout = ''
  let stderr = ''
  child.stdout.on('data', (chunk) => { stdout += chunk })
  child.stderr.on('data', (chunk) => { stderr += chunk })
  child.on('error', reject)
  child.on('close', (code) => resolve({ code, report: JSON.parse(stdout), stderr }))
})

const createSkill = async (root, name, frontmatter = {}) => {
  const folder = path.join(root, name)
  await import('node:fs/promises').then(({ mkdir }) => mkdir(folder, { recursive: true }))
  const metadata = frontmatter.metadata || {
    category: 'governance',
    family: 'none',
    source: 'local',
    version: '2026-09-28',
  }
  await writeFile(path.join(folder, 'SKILL.md'), [
    '---',
    `name: ${frontmatter.name || name}`,
    'description: Test skill',
    'metadata:',
    ...Object.entries(metadata).map(([key, value]) => `  ${key}: ${value}`),
    '---',
    '# Test skill',
    frontmatter.body || 'Use [the local reference](reference.md).',
  ].join('\n'))
}

test('passes a canonical skill and checks its local references', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'skill-doctor-'))
  await import('node:fs/promises').then(({ mkdir }) => mkdir(path.join(root, 'valid-skill'), { recursive: true }))
  await writeFile(path.join(root, 'valid-skill', 'reference.md'), 'reference')
  await createSkill(root, 'valid-skill')
  const result = await runDoctor(root)
  assert.equal(result.code, 0)
  assert.equal(result.report.inspected, 1)
  assert.deepEqual(result.report.failures, [])
  await rm(root, { recursive: true, force: true })
})

test('reports metadata, identity, broken paths, and contamination', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'skill-doctor-'))
  await createSkill(root, 'bad-skill', {
    name: 'wrong-name',
    metadata: { category: 'unknown', family: '', source: 'other', version: '1.0' },
    body: 'CVIX [missing](missing.md)',
  })
  const result = await runDoctor(root)
  assert.equal(result.code, 1)
  assert.deepEqual(new Set(result.report.failures.map(({ kind }) => kind)), new Set([
    'identity',
    'missing-metadata',
    'broken-paths',
    'contamination',
  ]))
  await rm(root, { recursive: true, force: true })
})

test('fail-on selects only the requested check kind', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'skill-doctor-'))
  await createSkill(root, 'bad-skill', { name: 'wrong-name', body: 'CVIX' })
  const result = await runDoctor(root, ['--fail-on', 'identity'])
  assert.equal(result.code, 1)
  assert.deepEqual(new Set(result.report.failures.map(({ kind }) => kind)), new Set(['identity']))
  await rm(root, { recursive: true, force: true })
})

test('detects every legacy external-contamination token', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'skill-doctor-'))
  const tokens = [
    'ResumeExceptionHandler',
    'ResumeRequestMapper',
    'CreateResumeRequest',
    'InvalidResumeDataException',
  ]
  await createSkill(root, 'contaminated-skill', { body: tokens.join(' ') })
  const result = await runDoctor(root)
  assert.equal(result.code, 1)
  const contamination = result.report.failures.find(({ kind }) => kind === 'contamination')
  assert.ok(contamination)
  for (const token of tokens) assert.match(contamination.message, new RegExp(token))
  await rm(root, { recursive: true, force: true })
})

test('rejects unknown check kinds', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'skill-doctor-'))
  const result = await new Promise((resolve) => {
    const child = spawn(process.execPath, [doctor, '--skills-dir', root, '--fail-on', 'unknown'])
    let stderr = ''
    child.stderr.on('data', (chunk) => { stderr += chunk })
    child.on('close', (code) => resolve({ code, stderr }))
  })
  assert.equal(result.code, 2)
  assert.match(result.stderr, /Unknown check kind/)
  await rm(root, { recursive: true, force: true })
})
