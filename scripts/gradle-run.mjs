#!/usr/bin/env node
import { spawn, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'

import {
  getRuntimeEnvironment,
  getWorktreeContext,
  prepareBackendEnvironment,
} from './worktree-context.mjs'
import { removeProcessRecord, writeProcessRecord } from './process-supervisor.mjs'

const args = process.argv.slice(2)
const isWin = process.platform === 'win32'
const wrapper = isWin ? 'gradlew.bat' : './gradlew'

if (!existsSync(wrapper)) {
  console.error(`Gradle wrapper not found: ${wrapper}`)
  process.exit(1)
}

const context = getWorktreeContext()
const isBootRun = args.some((arg) => arg === ':server:smp:bootRun')
const environment = isBootRun
  ? await prepareBackendEnvironment(context)
  : getRuntimeEnvironment(context)

if (isBootRun) {
  const child = spawn(wrapper, args, {
    stdio: 'inherit',
    shell: isWin,
    cwd: context.root,
    env: environment,
    detached: !isWin,
  })
  writeProcessRecord(
    'backend',
    [
      { pid: process.pid, detached: false },
      { pid: child.pid, detached: !isWin },
    ],
    context,
    {
      environment: {
        SMP_BACKEND_PORT: environment.SMP_BACKEND_PORT,
        MANAGEMENT_PORT: environment.MANAGEMENT_PORT,
      },
    },
  )

  let shuttingDown = false
  const shutdown = (code = 0) => {
    if (shuttingDown) return
    shuttingDown = true
    if (child.pid) {
      try {
        if (isWin) child.kill('SIGTERM')
        else process.kill(-child.pid, 'SIGTERM')
      } catch {}
    }
    removeProcessRecord('backend', context)
    process.exit(code)
  }

  process.on('SIGINT', () => shutdown())
  process.on('SIGTERM', () => shutdown())
  child.on('error', () => shutdown(1))
  child.on('exit', (code) => shutdown(code ?? 1))
} else {
  const result = spawnSync(wrapper, args, {
    stdio: 'inherit',
    shell: isWin,
    cwd: context.root,
    env: environment,
  })

  if (result.error) {
    console.error(result.error.message)
    process.exit(1)
  }

  process.exit(result.status ?? 1)
}
