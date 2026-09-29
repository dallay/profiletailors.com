import fs from 'node:fs'
import path from 'node:path'

const [type, reportPath] = process.argv.slice(2)

if (!type || !reportPath) {
  console.error('Usage: node scripts/verify-coverage-artifact.mjs <lcov|jacoco> <report-path>')
  process.exit(1)
}

const absolutePath = path.resolve(process.cwd(), reportPath)

if (!fs.existsSync(absolutePath)) {
  console.error(`❌ Coverage report not found: ${reportPath}`)
  process.exit(1)
}

const stat = fs.statSync(absolutePath)
if (stat.size === 0) {
  console.error(`❌ Coverage report is empty: ${reportPath}`)
  process.exit(1)
}

const content = fs.readFileSync(absolutePath, 'utf8')

if (type === 'lcov') {
  const sfMatches = [...content.matchAll(/^SF:(.+)$/gm)]
  if (sfMatches.length === 0) {
    console.error(`❌ LCOV report contains no SF (Source File) entries: ${reportPath}`)
    process.exit(1)
  }

  let validFiles = 0
  for (const match of sfMatches) {
    const filePath = match[1].trim()
    const resolvedPath = path.resolve(process.cwd(), filePath)
    if (fs.existsSync(resolvedPath)) {
      validFiles++
    }
  }

  if (validFiles === 0) {
    console.error(`❌ LCOV report contains source paths, but none exist on disk: ${reportPath}`)
    process.exit(1)
  }

  console.log(`✅ LCOV report verified (${sfMatches.length} source entries, ${validFiles} verified on disk): ${reportPath}`)
} else if (type === 'jacoco') {
  const classMatches = [...content.matchAll(/<class name="([^"]+)"/g)]
  const sourceFileMatches = [...content.matchAll(/<sourcefile name="([^"]+)"/g)]

  if (classMatches.length === 0 && sourceFileMatches.length === 0) {
    console.error(`❌ JaCoCo XML report contains no class or sourcefile entries: ${reportPath}`)
    process.exit(1)
  }

  console.log(`✅ JaCoCo XML report verified (${classMatches.length} classes, ${sourceFileMatches.length} source files): ${reportPath}`)
} else {
  console.error(`❌ Unknown coverage type: ${type}`)
  process.exit(1)
}
