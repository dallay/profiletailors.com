#!/usr/bin/env node
import { spawnSync } from 'node:child_process'

import {
  getRuntimeEnvironment,
  getWorktreeContext,
  releaseEphemeralPort,
  reserveEphemeralPort,
} from './worktree-context.mjs'

const args = process.argv.slice(2)
if (args[0] === '--') args.shift()

const context = getWorktreeContext()
const portLease = process.env.PLAYWRIGHT_PORT ? null : await reserveEphemeralPort(context)
const port = process.env.PLAYWRIGHT_PORT || String(portLease.port)
const env = getRuntimeEnvironment(context, {
  PLAYWRIGHT_PORT: port,
  PLAYWRIGHT_REUSE_EXISTING_SERVER: 'false',
})

let result
try {
  result = spawnSync('pnpm', ['exec', 'playwright', ...args], {
    cwd: process.cwd(),
    env,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  })
} finally {
  if (portLease) releaseEphemeralPort(context, `${context.worktreeId}:ephemeral:${process.pid}`)
}

if (result.error) {
  console.error(result.error.message)
  process.exit(1)
}

process.exit(result.status ?? 1)
