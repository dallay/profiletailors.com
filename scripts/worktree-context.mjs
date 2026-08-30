#!/usr/bin/env node
import { execFileSync, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import {
  closeSync,
  existsSync,
  mkdirSync,
  openSync,
  readFileSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs'
import net from 'node:net'
import { tmpdir } from 'node:os'
import { basename, resolve } from 'node:path'

function git(args) {
  return execFileSync('git', args, {
    cwd: process.cwd(),
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  }).trim()
}

function slug(value) {
  return (
    value
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'worktree'
  )
}

function rootPath(value, root) {
  return value.startsWith('/') ? value : resolve(root, value)
}

function envFileValue(root, key, fallback) {
  if (process.env[key] !== undefined) return process.env[key]
  const envFile = resolve(root, '.env')
  if (!existsSync(envFile)) return fallback
  const line = readFileSync(envFile, 'utf8')
    .split(/\r?\n/)
    .find((entry) => entry.trim().startsWith(`${key}=`))
  return line ? line.slice(line.indexOf('=') + 1).trim() : fallback
}

export function getWorktreeContext() {
  const root = git(['rev-parse', '--show-toplevel'])
  const gitDir = rootPath(git(['rev-parse', '--git-dir']), root)
  const commonDir = rootPath(git(['rev-parse', '--git-common-dir']), root)
  const branch = git(['branch', '--show-current']) || basename(root)
  const linked = gitDir !== commonDir
  const branchSlug = slug(branch.split('/').pop())
  const digest = createHash('sha256').update(root).digest('hex').slice(0, 10)
  const worktreeId = `pt-${branchSlug.slice(0, 46)}-${digest}`.slice(0, 63).replace(/-+$/, '')
  const label = linked && !['main', 'master'].includes(branch) ? branchSlug : ''
  const runtimeDir = resolve(root, '.worktree')
  const appName = label ? `${label}.pt-app` : 'pt-app'
  const adminName = label ? `${label}.pt-admin` : 'pt-admin'
  const marketingName = label ? `${label}.profiletailors` : 'profiletailors'
  const appUrl = `https://${appName}.localhost`
  const adminUrl = `https://${adminName}.localhost`
  const marketingUrl = `https://${marketingName}.localhost`

  return {
    root,
    branch,
    linked,
    worktreeId,
    composeProjectName: process.env.COMPOSE_PROJECT_NAME || worktreeId,
    portLeaseFile: resolve(tmpdir(), 'profiletailors-worktree-port-leases.json'),
    runtimeDir,
    runDir: resolve(runtimeDir, 'run'),
    logDir: resolve(runtimeDir, 'logs'),
    storageDir: resolve(runtimeDir, 'storage'),
    appUrl,
    adminUrl,
    marketingUrl,
    portlessNames: {
      app: appName,
      admin: adminName,
      marketing: marketingName,
    },
    corsOrigins: [
      appUrl,
      adminUrl,
      'https://pt-app.localhost',
      'https://pt-admin.localhost',
      'http://localhost:5173',
      'http://localhost:5174',
    ],
  }
}

export function ensureRuntimeDirs(context = getWorktreeContext()) {
  mkdirSync(context.runDir, { recursive: true })
  mkdirSync(context.logDir, { recursive: true })
  mkdirSync(context.storageDir, { recursive: true })
  return context
}

export function getRuntimeEnvironment(context = getWorktreeContext(), overrides = {}) {
  ensureRuntimeDirs(context)
  const backendEnvironment = readActiveBackendEnvironment(context)
  const env = {
    ...process.env,
    WORKTREE_ID: context.worktreeId,
    WORKTREE_ROOT: context.root,
    WORKTREE_RUNTIME_DIR: context.runtimeDir,
    COMPOSE_PROJECT_NAME: context.composeProjectName,
    SMP_CORS_ALLOWED_ORIGINS: envFileValue(
      context.root,
      'SMP_CORS_ALLOWED_ORIGINS',
      context.corsOrigins.join(','),
    ),
    SMP_PUBLIC_APP_URL: process.env.SMP_PUBLIC_APP_URL || context.appUrl,
    SMP_STORAGE_LOCAL_BASE_PATH: process.env.SMP_STORAGE_LOCAL_BASE_PATH || context.storageDir,
    LOGGING_FILE_NAME: process.env.LOGGING_FILE_NAME || resolve(context.logDir, 'smp.log'),
    SMP_BACKEND_PORT: process.env.SMP_BACKEND_PORT || backendEnvironment.SMP_BACKEND_PORT,
    MANAGEMENT_PORT: process.env.MANAGEMENT_PORT || backendEnvironment.MANAGEMENT_PORT,
    ...overrides,
  }
  if (!env.SMP_BACKEND_PORT) delete env.SMP_BACKEND_PORT
  if (!env.MANAGEMENT_PORT) delete env.MANAGEMENT_PORT
  return env
}

function readActiveBackendEnvironment(context) {
  const path = resolve(context.runDir, 'backend.json')
  if (!existsSync(path)) return {}
  try {
    const record = JSON.parse(readFileSync(path, 'utf8'))
    if (!(record.records || []).some(({ pid }) => processIsAlive(pid))) return {}
    return record.environment || {}
  } catch {
    return {}
  }
}

export function getComposeEnvironment(context = getWorktreeContext()) {
  const env = getRuntimeEnvironment(context)
  for (const key of [
    'SMP_POSTGRES_PORT',
    'POSTGRES_PORT',
    'MAILPIT_SMTP_PORT',
    'MAILPIT_UI_PORT',
    'WIREMOCK_HOST_PORT',
    'PROMETHEUS_HOST_PORT',
    'GRAFANA_HOST_PORT',
  ]) {
    if (process.env[key] === undefined) env[key] = '0'
  }
  return env
}

function canBind(port) {
  return new Promise((resolveResult) => {
    const server = net.createServer()
    server.once('error', () => resolveResult(false))
    server.listen(port, '127.0.0.1', () => server.close(() => resolveResult(true)))
  })
}

export async function findAvailablePort(start, attempts = 100) {
  for (let offset = 0; offset < attempts; offset += 1) {
    const port = start + offset
    if (await canBind(port)) return port
  }
  throw new Error(`No available port found in range ${start}-${start + attempts - 1}`)
}

function sleep(milliseconds) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, milliseconds)
}

function processIsAlive(pid) {
  try {
    process.kill(pid, 0)
    return true
  } catch {
    return false
  }
}

function acquireLeaseLock(path) {
  const lockPath = `${path}.lock`
  for (let attempt = 0; attempt < 100; attempt += 1) {
    try {
      const fd = openSync(lockPath, 'wx')
      writeFileSync(fd, String(process.pid))
      return { fd, lockPath }
    } catch (error) {
      if (error.code !== 'EEXIST') throw error
      try {
        const owner = Number.parseInt(readFileSync(lockPath, 'utf8'), 10)
        if (!owner || !processIsAlive(owner)) unlinkSync(lockPath)
      } catch {}
      sleep(10)
    }
  }
  throw new Error(`Unable to acquire worktree port lease lock: ${lockPath}`)
}

function loadLeases(path) {
  if (!existsSync(path)) return {}
  try {
    return JSON.parse(readFileSync(path, 'utf8'))
  } catch {
    return {}
  }
}

async function reserveLeasePorts(context) {
  const lock = acquireLeaseLock(context.portLeaseFile)
  try {
    const leases = loadLeases(context.portLeaseFile)
    for (const [key, lease] of Object.entries(leases)) {
      if (!processIsAlive(lease.pid)) delete leases[key]
    }

    const activePorts = new Set(
      Object.values(leases).flatMap((lease) => [
        lease.backendPort,
        lease.managementPort,
        lease.port,
      ]),
    )
    const lease = leases[context.worktreeId]
    if (lease && lease.pid === process.pid) return lease
    if (lease && lease.pid !== process.pid) {
      throw new Error(
        `Worktree ${context.worktreeId} already owns backend ports ${lease.backendPort}/${lease.managementPort}`,
      )
    }

    const nextAvailable = async (start) => {
      for (let port = start; port < start + 100; port += 1) {
        if (!activePorts.has(port) && (await canBind(port))) return port
      }
      return null
    }
    const backendPort = await nextAvailable(7638)
    if (!backendPort) throw new Error('No isolated backend port available')
    activePorts.add(backendPort)
    const managementPort = await nextAvailable(9091)
    if (!managementPort) throw new Error('No isolated management port available')

    leases[context.worktreeId] = {
      pid: process.pid,
      backendPort,
      managementPort,
      startedAt: new Date().toISOString(),
    }
    writeFileSync(context.portLeaseFile, JSON.stringify(leases, null, 2))
    return leases[context.worktreeId]
  } finally {
    closeSync(lock.fd)
    unlinkSync(lock.lockPath)
  }
}

export async function reserveEphemeralPort(context = getWorktreeContext(), start = 5173) {
  const lock = acquireLeaseLock(context.portLeaseFile)
  const leaseKey = `${context.worktreeId}:ephemeral:${process.pid}`
  try {
    const leases = loadLeases(context.portLeaseFile)
    for (const [key, lease] of Object.entries(leases)) {
      if (!processIsAlive(lease.pid)) delete leases[key]
    }

    const activePorts = new Set(
      Object.values(leases).flatMap((lease) => [
        lease.backendPort,
        lease.managementPort,
        lease.port,
      ]),
    )
    const existing = leases[leaseKey]
    if (existing) return existing

    let port = null
    for (let candidate = start; candidate < start + 100; candidate += 1) {
      if (!activePorts.has(candidate) && (await canBind(candidate))) {
        port = candidate
        break
      }
    }
    if (!port)
      throw new Error(`No isolated ephemeral port available in range ${start}-${start + 99}`)

    leases[leaseKey] = {
      pid: process.pid,
      port,
      startedAt: new Date().toISOString(),
    }
    writeFileSync(context.portLeaseFile, JSON.stringify(leases, null, 2))
    return leases[leaseKey]
  } finally {
    closeSync(lock.fd)
    unlinkSync(lock.lockPath)
  }
}

export function releaseEphemeralPort(context, leaseKey) {
  const lock = acquireLeaseLock(context.portLeaseFile)
  try {
    const leases = loadLeases(context.portLeaseFile)
    if (leases[leaseKey]?.pid === process.pid) {
      delete leases[leaseKey]
      writeFileSync(context.portLeaseFile, JSON.stringify(leases, null, 2))
    }
  } finally {
    closeSync(lock.fd)
    unlinkSync(lock.lockPath)
  }
}

export async function prepareBackendEnvironment(context = getWorktreeContext()) {
  const composeEnvironment = getComposeEnvironment(context)
  if (
    process.env.SPRING_DOCKER_COMPOSE_ENABLED !== 'false' &&
    process.env.WORKTREE_INFRA_READY !== '1'
  ) {
    const up = spawnSync(
      'docker',
      ['compose', '--project-name', context.composeProjectName, 'up', '-d'],
      {
        cwd: context.root,
        env: composeEnvironment,
        stdio: 'inherit',
      },
    )
    if ((up.status ?? 1) !== 0) throw new Error('Unable to start worktree infrastructure')
    composeEnvironment.WORKTREE_INFRA_READY = '1'
  }

  const lease =
    !process.env.SMP_BACKEND_PORT && !process.env.MANAGEMENT_PORT
      ? await reserveLeasePorts(context)
      : null
  const backendPort =
    process.env.SMP_BACKEND_PORT || String(lease?.backendPort || (await findAvailablePort(7638)))
  const managementPort =
    process.env.MANAGEMENT_PORT || String(lease?.managementPort || (await findAvailablePort(9091)))
  const environment = {
    ...composeEnvironment,
    SMP_BACKEND_PORT: backendPort,
    MANAGEMENT_PORT: managementPort,
  }

  if (environment.WORKTREE_INFRA_READY === '1') {
    const mappedPort = (service, containerPort) => {
      const result = spawnSync(
        'docker',
        [
          'compose',
          '--project-name',
          context.composeProjectName,
          'port',
          service,
          String(containerPort),
        ],
        {
          cwd: context.root,
          env: composeEnvironment,
          encoding: 'utf8',
          stdio: ['ignore', 'pipe', 'pipe'],
        },
      )
      if (result.status !== 0)
        throw new Error(`Unable to resolve mapped port for ${service}:${containerPort}`)
      const match = result.stdout.match(/:(\d+)\s*$/m)
      if (!match) throw new Error(`Unable to parse mapped port for ${service}:${containerPort}`)
      return match[1]
    }

    const postgresPort = mappedPort('postgresql', 5432)
    const smtpPort = mappedPort('mailpit', 1025)
    const wiremockPort = mappedPort('linkedin-wiremock', 8080)
    const databaseName = envFileValue(context.root, 'SMP_POSTGRES_DB', 'profiletailors_smp')
    environment.SMP_POSTGRES_PORT = postgresPort
    environment.SMP_SMTP_HOST = '127.0.0.1'
    environment.SMP_SMTP_PORT = smtpPort
    environment.WIREMOCK_HOST_PORT = wiremockPort
    environment.SMP_R2DBC_URL = `r2dbc:postgresql://127.0.0.1:${postgresPort}/${databaseName}`
    environment.SMP_LIQUIBASE_JDBC_URL = `jdbc:postgresql://127.0.0.1:${postgresPort}/${databaseName}`
  }

  return environment
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const context = getWorktreeContext()
  if (process.argv.includes('--json')) {
    process.stdout.write(`${JSON.stringify(context, null, 2)}\n`)
  } else {
    process.stdout.write(
      `WORKTREE_ID=${context.worktreeId}\nCOMPOSE_PROJECT_NAME=${context.composeProjectName}\nAPP_URL=${context.appUrl}\nADMIN_URL=${context.adminUrl}\nMARKETING_URL=${context.marketingUrl}\nRUNTIME_DIR=${context.runtimeDir}\n`,
    )
  }
}
