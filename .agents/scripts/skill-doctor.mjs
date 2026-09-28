#!/usr/bin/env node
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import process from 'node:process'

const CONTAMINATION = [
  'CVIX',
  'profiletailors.resume',
  'apps/portfolio',
  'apps/blog',
  'packages/testing-e2e',
  'ResumeExceptionHandler',
  'ResumeRequestMapper',
  'CreateResumeRequest',
  'InvalidResumeDataException',
]
const CATEGORIES = new Set(['backend-platform', 'frontend-platform', 'testing', 'governance', 'languages-typing', 'design', 'devops', 'design-pattern', 'knowledge-format', 'none'])
const FAMILIES = new Set(['spring-boot', 'vue', 'playwright', 'vitest', 'typescript', 'kotlin', 'css', 'markdown', 'none'])
const SOURCES = new Set(['local', 'upstream-adapted'])
const VERSION_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const CHECK_KINDS = new Set(['contamination', 'missing-frontmatter', 'missing-metadata', 'broken-paths', 'identity', 'invalid-values'])

const parseArguments = (args) => {
  const options = { failOn: [], json: false, skillsDir: '.agents/skills' }
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index]
    if (argument === '--skills-dir') {
      const value = args[index + 1]
      if (!value) throw new Error('--skills-dir requires a directory')
      options.skillsDir = value
      index += 1
    } else if (argument === '--fail-on') {
      const value = args[index + 1]
      if (!value) throw new Error('--fail-on requires a check kind')
      if (!CHECK_KINDS.has(value)) throw new Error(`Unknown check kind: ${value}`)
      options.failOn.push(value)
      index += 1
    } else if (argument === '--json') {
      options.json = true
    } else if (argument === '--help') {
      options.help = true
    } else {
      throw new Error(`Unknown argument: ${argument}`)
    }
  }
  if (!options.help && options.failOn.length === 0) {
    options.failOn = ['contamination', 'missing-frontmatter', 'missing-metadata', 'broken-paths', 'identity']
  }
  return options
}

const usage = () => process.stdout.write('Usage: skill-doctor.mjs --skills-dir <dir> [--fail-on <kind>] [--json]\n')
const scalarValue = (value) => String(value || '').replace(/^['"]|['"]$/g, '').trim()

const parseFrontmatter = (content) => {
  if (!content.startsWith('---\n')) return null
  const closing = content.indexOf('\n---', 4)
  if (closing < 0) return null
  const values = {}
  let metadata
  for (const line of content.slice(4, closing).split(/\r?\n/)) {
    const scalar = line.match(/^([\w-]+):\s*(.*)$/)
    const nested = line.match(/^\s{2}([\w-]+):\s*(.*)$/)
    if (scalar) values[scalar[1]] = scalarValue(scalar[2])
    if (nested) {
      metadata ||= {}
      metadata[nested[1]] = scalarValue(nested[2])
    }
  }
  return { ...values, metadata: metadata || {} }
}

const extractReferences = (body) => [...body.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)]
  .map((match) => match[1].split('#')[0])
  .filter((reference) => reference && !reference.startsWith('http://') && !reference.startsWith('https://') && !reference.startsWith('#'))

const getSkillFiles = (skillsDir) => readdirSync(skillsDir, { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .flatMap((entry) => {
    const folder = entry.name
    const topLevelFile = resolve(skillsDir, folder, 'SKILL.md')
    const nestedFiles = readdirSync(resolve(skillsDir, folder), { withFileTypes: true })
      .filter((child) => child.isDirectory())
      .map((child) => resolve(skillsDir, folder, child.name, 'SKILL.md'))
      .filter((file) => existsSync(file))
      .map((file) => ({ folder: `${folder}/${file.split('/').at(-2)}`, file, nested: true }))
    return existsSync(topLevelFile) ? [{ folder, file: topLevelFile }] : nestedFiles
  })

const add = (checks, kind, severity, message) => checks.push({ kind, severity, message })

const inspectSkill = ({ folder, file }, repoRoot) => {
  const checks = []
  const content = readFileSync(file, 'utf8')
  const frontmatter = parseFrontmatter(content)
  add(checks, 'missing-frontmatter', frontmatter ? 'pass' : 'block', frontmatter ? 'frontmatter present' : 'frontmatter missing or malformed')

  if (!frontmatter) return { path: relative(repoRoot, file), checks }

  const nameMatches = frontmatter.name === folder
  add(checks, 'identity', nameMatches ? 'pass' : 'block', nameMatches ? `name matches ${folder}` : `name does not match ${folder}`)

  const category = frontmatter.metadata.category
  const family = frontmatter.metadata.family
  const source = frontmatter.metadata.source
  const version = frontmatter.metadata.version
  const metadataValid = CATEGORIES.has(category) && FAMILIES.has(family) && SOURCES.has(source) && VERSION_PATTERN.test(version || '')
  add(checks, 'missing-metadata', metadataValid ? 'pass' : 'block', metadataValid ? 'metadata is complete' : 'metadata is missing or invalid')
  add(checks, 'invalid-values', metadataValid ? 'pass' : 'block', metadataValid ? 'metadata values are valid' : 'metadata values are invalid')

  const body = content.slice(content.indexOf('\n---', 4) + 4)
  const references = extractReferences(body)
  const broken = references.filter((reference) => !existsSync(resolve(repoRoot, reference)) && !existsSync(resolve(file, '..', reference)) && !existsSync(resolve(file, '..', '..', reference)))
  add(checks, 'broken-paths', broken.length === 0 ? 'pass' : 'block', broken.length === 0 ? 'referenced paths resolve' : `broken paths: ${broken.join(', ')}`)

  const contamination = CONTAMINATION.filter((token) => content.includes(token))
  add(checks, 'contamination', contamination.length === 0 ? 'pass' : 'block', contamination.length === 0 ? 'no known contamination' : `known contamination: ${contamination.join(', ')}`)
  return { path: relative(repoRoot, file), checks }
}

const main = () => {
  const options = parseArguments(process.argv.slice(2))
  if (options.help) {
    usage()
    return 0
  }

  const skillsDir = resolve(options.skillsDir)
  if (!existsSync(skillsDir)) throw new Error(`Skills directory does not exist: ${options.skillsDir}`)
  const repoRoot = resolve(skillsDir, '..', '..')
  const skills = getSkillFiles(skillsDir).map((skill) => inspectSkill(skill, repoRoot))
  const selectedFailures = skills.flatMap(({ path, checks }) => checks
    .filter(({ kind, severity }) => options.failOn.includes(kind) && severity === 'block')
    .map(({ kind, message }) => ({ path, kind, message })))
  const report = {
    skills,
    inspected: skills.length,
    failOn: options.failOn,
    failures: selectedFailures,
    passed: selectedFailures.length === 0,
  }
  if (options.json) process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
  else {
    process.stdout.write(`Inspected ${report.inspected} skills\n`)
    for (const failure of selectedFailures) process.stdout.write(`BLOCK ${failure.kind} ${failure.path}: ${failure.message}\n`)
    process.stdout.write(report.passed ? 'PASS\n' : `FAIL ${selectedFailures.length} selected findings\n`)
  }
  return report.passed ? 0 : 1
}

try {
  process.exitCode = main()
} catch (error) {
  process.stderr.write(`${error.message}\n`)
  process.exitCode = 2
}
