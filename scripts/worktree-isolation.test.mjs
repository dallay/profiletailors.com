import assert from 'node:assert/strict'
import test from 'node:test'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import {
  findAvailablePort,
  getComposeEnvironment,
  getRuntimeEnvironment,
  getWorktreeContext,
  releaseEphemeralPort,
  reserveEphemeralPort,
} from './worktree-context.mjs'
import { removeProcessRecord, writeProcessRecord } from './process-supervisor.mjs'

test('derives a valid isolated worktree identity', () => {
  const context = getWorktreeContext()

  assert.match(context.worktreeId, /^pt-[a-z0-9-]+$/)
  assert.ok(context.worktreeId.length <= 63)
  assert.equal(context.composeProjectName, process.env.COMPOSE_PROJECT_NAME || context.worktreeId)
  assert.equal(context.runtimeDir, `${context.root}/.worktree`)
  assert.match(context.appUrl, /^https:\/\/.+\.localhost$/)
  assert.match(context.adminUrl, /^https:\/\/.+\.localhost$/)
})

test('exports worktree runtime and dynamic Compose defaults', () => {
  const context = getWorktreeContext()
  const runtime = getRuntimeEnvironment(context)
  const compose = getComposeEnvironment(context)

  assert.equal(runtime.WORKTREE_ROOT, context.root)
  assert.equal(runtime.WORKTREE_ID, context.worktreeId)
  assert.equal(runtime.COMPOSE_PROJECT_NAME, context.composeProjectName)
  assert.equal(compose.COMPOSE_PROJECT_NAME, context.composeProjectName)
  assert.equal(compose.SMP_POSTGRES_PORT, process.env.SMP_POSTGRES_PORT || '0')
  assert.equal(compose.WIREMOCK_HOST_PORT, process.env.WIREMOCK_HOST_PORT || '0')
})

test('exposes the runtime port allocator', () => {
  assert.equal(typeof findAvailablePort, 'function')
  assert.equal(typeof reserveEphemeralPort, 'function')
  assert.equal(typeof releaseEphemeralPort, 'function')
})

test('reuses the active backend port for separately launched frontend tools', () => {
  const runtimeDir = mkdtempSync(join(tmpdir(), 'profiletailors-worktree-test-'))
  const baseContext = getWorktreeContext()
  const context = {
    ...baseContext,
    runtimeDir,
    runDir: join(runtimeDir, 'run'),
    logDir: join(runtimeDir, 'logs'),
    storageDir: join(runtimeDir, 'storage'),
  }

  try {
    writeProcessRecord('backend', [{ pid: process.pid, detached: false }], context, {
      environment: { SMP_BACKEND_PORT: '17638', MANAGEMENT_PORT: '19091' },
    })

    const runtime = getRuntimeEnvironment(context)

    assert.equal(runtime.SMP_BACKEND_PORT, '17638')
    assert.equal(runtime.MANAGEMENT_PORT, '19091')
  } finally {
    removeProcessRecord('backend', context)
    rmSync(runtimeDir, { recursive: true, force: true })
  }
})
