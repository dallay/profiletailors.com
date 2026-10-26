import { execFileSync, spawn } from 'node:child_process'
import { existsSync, readdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { ensureRuntimeDirs, getWorktreeContext } from './worktree-context.mjs'

const isWin = process.platform === 'win32'

export function processRecordPath(name, context = getWorktreeContext()) {
  ensureRuntimeDirs(context)
  return resolve(context.runDir, `${name}.json`)
}

export function writeProcessRecord(name, records, context = getWorktreeContext(), metadata = {}) {
  const path = processRecordPath(name, context)
  writeFileSync(
    path,
    JSON.stringify(
      {
        name,
        root: context.root,
        worktreeId: context.worktreeId,
        startedAt: new Date().toISOString(),
        records,
        ...metadata,
      },
      null,
      2,
    ),
  )
  return path
}

export function removeProcessRecord(name, context = getWorktreeContext()) {
  const path = processRecordPath(name, context)
  if (existsSync(path)) unlinkSync(path)
}

export function listProcessRecords(context = getWorktreeContext()) {
  ensureRuntimeDirs(context)
  return readdirSync(context.runDir)
    .filter((name) => name.endsWith('.json'))
    .map((name) => resolve(context.runDir, name))
}

function terminatePid(pid, detached) {
  if (!pid || pid === process.pid) return
  if (isWin) {
    try {
      execFileSync('taskkill', ['/F', '/T', '/PID', String(pid)], { stdio: 'ignore' })
    } catch {}
    return
  }
  try {
    process.kill(detached ? -pid : pid, 'SIGTERM')
  } catch {}
}

export function terminateProcessRecord(path) {
  let record
  try {
    record = JSON.parse(readFileSync(path, 'utf8'))
  } catch {
    if (existsSync(path)) unlinkSync(path)
    return false
  }
  for (const processEntry of record.records || []) {
    terminatePid(processEntry.pid, processEntry.detached)
  }
  if (existsSync(path)) unlinkSync(path)
  return true
}

export function runManagedChild({
  name,
  command,
  args,
  context = getWorktreeContext(),
  spawnOptions = {},
  metadata = {},
}) {
  const child = spawn(command, args, {
    ...spawnOptions,
    detached: !isWin,
  })
  writeProcessRecord(
    name,
    [
      { pid: process.pid, detached: false },
      { pid: child.pid, detached: !isWin },
    ],
    context,
    metadata,
  )

  let shuttingDown = false
  const shutdown = async (code = 0) => {
    if (shuttingDown) return
    shuttingDown = true
    if (child.exitCode === null && child.signalCode === null && child.pid) {
      terminatePid(child.pid, true)
      let timeout
      const exited = await Promise.race([
        new Promise((resolveExit) => child.once('exit', () => resolveExit(true))),
        new Promise((resolveTimeout) => {
          timeout = setTimeout(() => resolveTimeout(false), 5000)
        }),
      ])
      clearTimeout(timeout)
      if (!exited && child.exitCode === null && child.signalCode === null) {
        if (isWin) {
          try {
            execFileSync('taskkill', ['/F', '/T', '/PID', String(child.pid)], { stdio: 'ignore' })
          } catch {}
        } else {
          terminatePid(child.pid, true)
          try {
            process.kill(-child.pid, 'SIGKILL')
          } catch {}
        }
      }
    }
    removeProcessRecord(name, context)
    process.exit(code)
  }

  process.on('SIGINT', () => void shutdown())
  process.on('SIGTERM', () => void shutdown())
  child.on('error', () => void shutdown(1))
  child.on('exit', (code) => void shutdown(code ?? 1))

  return { child, shutdown }
}
