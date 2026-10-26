#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'

import {
  getRuntimeEnvironment,
  getWorktreeContext,
  prepareBackendEnvironment,
} from './worktree-context.mjs'
import { runManagedChild } from './process-supervisor.mjs'

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
  runManagedChild({
    name: 'backend',
    command: wrapper,
    args,
    context,
    spawnOptions: {
      stdio: 'inherit',
      shell: isWin,
      cwd: context.root,
      env: environment,
    },
    metadata: {
      environment: {
        SMP_BACKEND_PORT: environment.SMP_BACKEND_PORT,
        MANAGEMENT_PORT: environment.MANAGEMENT_PORT,
      },
    },
  })
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
