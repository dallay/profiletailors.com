#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, basename } from 'node:path';
import process from 'node:process';

const TOKENS = [
  ['HttpSecurity', '(?<!Server)HttpSecurity\\b'],
  ['SecurityFilterChain', '(?<!Web)SecurityFilterChain\\b'],
  ['OncePerRequestFilter', 'OncePerRequestFilter\\b'],
  ['MockMvc', 'MockMvc\\b'],
  ['AutoConfigureMockMvc', 'AutoConfigureMockMvc\\b'],
  ['@WebMvcTest', '@WebMvcTest\\b'],
  ['JpaRepository', 'JpaRepository\\b'],
  ['spring.datasource', 'spring\\.datasource'],
  ['spring.jpa', 'spring\\.jpa'],
  ['starter-data-jpa', 'spring-boot-starter-data-jpa(?!.*r2dbc)'],
  ['starter-web', 'spring-boot-starter-web\\b(?!.*flux)'],
  ['@RequiredArgsConstructor', '@RequiredArgsConstructor\\b'],
  ['lombok', 'lombok'],
  ['MockitoExtension', 'MockitoExtension\\b'],
  ['@MockBean', '@MockBean\\b'],
];

function findRipgrep() {
  for (const candidate of ['rg', 'rg.exe']) {
    try {
      const version = execFileSync(candidate, ['--version'], { stdio: 'pipe' }).toString();
      if (/ripgrep/.test(version)) return candidate;
    } catch {
      // try next
    }
  }
  return null;
}

function collectFiles(target) {
  if (existsSync(target) && statSync(target).isFile()) return [target];
  const files = [];
  for (const entry of readdirSync(target)) {
    const p = join(target, entry);
    if (statSync(p).isFile() && p.endsWith('.md')) files.push(p);
  }
  return files;
}

function isInsideLegacySection(lineNumber, lines) {
  let inLegacy = false;
  let depth = 0;
  for (let i = 0; i < lines.length && i < lineNumber; i += 1) {
    const line = lines[i];
    if (line.includes('<!-- pre-migration') || line.includes('<!-- legacy:')) {
      inLegacy = true;
      depth = 0;
    }
    if (inLegacy) {
      depth += (line.match(/<!--/g) ?? []).length;
      depth -= (line.match(/-->/g) ?? []).length;
      if (depth <= 0) inLegacy = false;
    }
  }
  return inLegacy;
}

function isLineInLegacySection(lineNumber, contents) {
  let inLegacy = false;
  const openerRegex = /^[^>]*<!--\s*(pre-migration|legacy:[\w-]+)/;
  const closerRegex = /<!--\s*\/(?:pre-migration|legacy:[\w-]+)\s*-->$/;
  for (let i = 0; i < lineNumber && i < contents.length; i += 1) {
    const line = contents[i];
    if (openerRegex.test(line)) {
      inLegacy = true;
    }
    if (inLegacy && closerRegex.test(line)) {
      inLegacy = false;
    }
  }
  return inLegacy;
}

function scanFile(ripgrep, filePath) {
  const contents = readFileSync(filePath, 'utf8').split('\n');
  const pattern = TOKENS.map(([, rx]) => rx).join('|');
  try {
    const output = execFileSync(ripgrep, ['-n', '--pcre2', pattern, filePath], {
      stdio: ['ignore', 'pipe', 'pipe'],
    }).toString();
    return { ok: false, output: filterLegacy(output, contents) };
  } catch (err) {
    if (err.status === 1) return { ok: true, output: '' };
    return { ok: false, output: err.stderr?.toString() ?? err.stdout?.toString() ?? '' };
  }
}

function extractLineNumber(line) {
  const startsWithNumber = line.match(/^(\d+):/);
  if (startsWithNumber) return Number.parseInt(startsWithNumber[1], 10) - 1;
  const absoluteMatch = line.match(/\s(\d+):\s/);
  if (absoluteMatch) return Number.parseInt(absoluteMatch[1], 10) - 1;
  return -1;
}

function filterLegacy(output, contents) {
  if (!output) return '';
  const filtered = output
    .split('\n')
    .filter((line) => {
      const idx = extractLineNumber(line);
      if (!Number.isFinite(idx) || idx < 0) return true;
      return !isLineInLegacySection(idx, contents);
    })
    .join('\n');
  return filtered;
}

function scan(ripgrep, target) {
  const files = collectFiles(target);
  const allOutput = [];
  for (const file of files) {
    const isMigrationFile = basename(file).includes('migration');
    if (isMigrationFile) continue;
    const result = scanFile(ripgrep, file);
    if (!result.ok && result.output) {
      allOutput.push(result.output);
    }
  }
  if (allOutput.length === 0) return { ok: true, output: '' };
  return { ok: false, output: allOutput.join('\n') };
}

function summarize(ripgrep, target) {
  const files = collectFiles(target);
  const lines = [];
  let total = 0;
  for (const [label, rx] of TOKENS) {
    let count = 0;
    for (const file of files) {
      try {
        const out = execFileSync(ripgrep, ['-c', '--pcre2', rx, file], {
          stdio: ['ignore', 'pipe', 'pipe'],
        }).toString().trim();
        count += Number.parseInt(out, 10) || 0;
      } catch {
        // 0 if no match
      }
    }
    if (count > 0) {
      lines.push(`  ${label}: ${count}`);
      total += count;
    }
  }
  return { total, lines };
}

function listLegacyFiles(target) {
  let ripgrep;
  try {
    ripgrep = findRipgrep();
  } catch {
    ripgrep = null;
  }
  if (!ripgrep) return [];
  try {
    const out = execFileSync(ripgrep, ['-l', '--pcre2', 'legacy-|<!-- legacy:', target], {
      stdio: ['ignore', 'pipe', 'pipe'],
    }).toString();
    return out.split('\n').filter(Boolean);
  } catch {
    return [];
  }
}

function help() {
  process.stdout.write(
    'Usage:\n' +
      '  node spring-scrub-rg.mjs <subservice-dir>           # PASS/FAIL exit-code gate\n' +
      '  node spring-scrub-rg.mjs --check <subservice-dir>   # show per-token counts\n' +
      '  node spring-scrub-rg.mjs --list-legacy <subservice-dir>  # list legacy-* files\n' +
      'Subservice dir is relative to .agents/skills/spring-boot/, e.g. "security".\n',
  );
}

function main() {
  const args = process.argv.slice(2);
  if (args.length === 0 || args[0] === '-h' || args[0] === '--help') {
    help();
    process.exit(0);
  }

  let mode = 'gate';
  let target;
  if (args[0] === '--check') {
    mode = 'check';
    target = args[1];
  } else if (args[0] === '--list-legacy') {
    mode = 'legacy';
    target = args[1];
  } else {
    target = args[0];
  }

  if (!target) {
    help();
    process.exit(2);
  }

  const repoRoot = process.cwd();
  const absTarget = target.startsWith('/')
    ? target
    : target.startsWith('.')
      ? `${repoRoot}/${target}`
      : `${repoRoot}/.agents/skills/spring-boot/${target}/references`;
  if (!existsSync(absTarget)) {
    process.stderr.write(`ERROR: target not found: ${absTarget}\n`);
    process.exit(2);
  }

  const ripgrep = findRipgrep();
  if (!ripgrep) {
    process.stderr.write('ERROR: ripgrep (rg) not found in PATH\n');
    process.exit(2);
  }

  if (mode === 'legacy') {
    const legacy = listLegacyFiles(absTarget);
    process.stdout.write(`${legacy.length}\n`);
    legacy.forEach((line) => process.stdout.write(`${line}\n`));
    process.exit(0);
  }

  const result = scan(ripgrep, absTarget);
  if (mode === 'check') {
    process.stdout.write(`Target: ${absTarget}\n`);
    if (result.ok) {
      process.stdout.write('PASS: clean (0 incompatible tokens)\n');
      const { total, lines } = summarize(ripgrep, absTarget);
      process.stdout.write(`Total counted (sanity): ${total}\n`);
      if (lines.length > 0) process.stdout.write(`${lines.join('\n')}\n`);
      process.exit(0);
    }
    process.stdout.write(`FAIL: incompatible tokens present\n${result.output}`);
    process.exit(1);
  }

  if (result.ok) {
    process.stdout.write('PASS: clean\n');
    process.exit(0);
  }
  process.stdout.write('FAIL: incompatible tokens present\n');
  process.stdout.write(result.output);
  process.exit(1);
}

main();
