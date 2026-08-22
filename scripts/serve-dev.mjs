#!/usr/bin/env node
import { spawn, spawnSync } from 'node:child_process'

import { getWorktreeContext, prepareBackendEnvironment } from './worktree-context.mjs'
import { removeProcessRecord, writeProcessRecord } from './process-supervisor.mjs'

const force = process.argv[2] || ''
const isWin = process.platform === 'win32'
const context = getWorktreeContext()

if (force && force !== '--force') {
  console.error(`Unknown option: ${force}`)
  console.error('Usage: just serve [--force]')
  process.exit(2)
}

if (force === '--force') {
  const kill = spawnSync(process.execPath, ['scripts/kill-servers.mjs'], {
    stdio: 'inherit',
    shell: isWin,
  })
  if ((kill.status ?? 1) !== 0) {
    process.exit(kill.status ?? 1)
  }
}

const environment = await prepareBackendEnvironment(context)

console.log(
  `Ensure Portless proxy is running for ${context.appUrl} (run \`pnpm exec portless proxy start\` if needed).`,
)
console.log('Starting backend (Spring Boot) + frontend app (Vite)...')

const backend = spawn(
  process.execPath,
  ['scripts/gradle-run.mjs', ':server:smp:bootRun', '--args=--spring.profiles.active=dev'],
  {
    stdio: 'inherit',
    shell: isWin,
    cwd: context.root,
    env: environment,
    detached: !isWin,
  },
)

const frontend = spawn('pnpm', ['dev'], {
  cwd: `${context.root}/apps/web/app`,
  stdio: 'inherit',
  shell: isWin,
  env: environment,
  detached: !isWin,
})

writeProcessRecord(
  'serve',
  [
    { pid: process.pid, detached: false },
    { pid: backend.pid, detached: !isWin },
    { pid: frontend.pid, detached: !isWin },
  ],
  context,
)

let shuttingDown = false
const shutdown = (code = 0) => {
  if (shuttingDown) return
  shuttingDown = true
  for (const child of [backend, frontend]) {
    if (child.pid) {
      try {
        if (isWin) child.kill('SIGTERM')
        else process.kill(-child.pid, 'SIGTERM')
      } catch {}
    }
  }
  removeProcessRecord('serve', context)
  process.exit(code)
}

process.on('SIGINT', () => shutdown())
process.on('SIGTERM', () => shutdown())

frontend.on('exit', (code) => {
  shutdown(code ?? 1)
})

backend.on('exit', (code) => {
  if (!shuttingDown) shutdown(code ?? 1)
})
