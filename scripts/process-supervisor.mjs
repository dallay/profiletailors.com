import { execFileSync } from 'node:child_process'
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
