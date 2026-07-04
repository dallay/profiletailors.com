import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import process from 'node:process';

const colors = {
  reset: '\x1b[0m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  bold: '\x1b[1m',
};

const symbols = {
  success: '✓',
  warning: '○',
  error: '✗',
};

function checkTool(name, command, required = true, minVersion = null) {
  try {
    const output = execSync(command, { stdio: ['pipe', 'pipe', 'ignore'] }).toString().trim();
    const versionMatch = output.match(/(\d+\.\d+(\.\d+)?)/);
    const version = versionMatch ? versionMatch[1] : output;

    if (minVersion && version) {
      if (!isVersionGte(version, minVersion)) {
        console.log(`${colors.red}${symbols.error} ${name} ${version} found, but version >= ${minVersion} is required${colors.reset}`);
        return false;
      }
    }

    console.log(`${colors.green}${symbols.success} ${name} ${version}${colors.reset}`);
    return true;
  } catch (e) {
    if (required) {
      console.log(`${colors.red}${symbols.error} ${name} not found — required${colors.reset}`);
      return false;
    } else {
      console.log(`${colors.yellow}${symbols.warning} ${name} not found — optional${colors.reset}`);
      return true;
    }
  }
}

function isVersionGte(v1, v2) {
  const v1Parts = v1.split('.').map(Number);
  const v2Parts = v2.split('.').map(Number);
  for (let i = 0; i < Math.max(v1Parts.length, v2Parts.length); i++) {
    const v1Part = v1Parts[i] || 0;
    const v2Part = v2Parts[i] || 0;
    if (v1Part > v2Part) return true;
    if (v1Part < v2Part) return false;
  }
  return true;
}

console.log(`${colors.bold}Profile Tailors — Environment Doctor${colors.reset}\n`);

const pkg = JSON.parse(readFileSync(join(process.cwd(), 'package.json'), 'utf8'));
const nodeRequirement = pkg.engines?.node?.match(/(\d+\.\d+\.\d+)/)?.[1] || '22.12.0';
const pnpmRequirement = pkg.engines?.pnpm?.match(/(\d+\.\d+\.\d+)/)?.[1] || '11.8.0';

let success = true;

// Required Tools
success &= checkTool('Git', 'git --version');
success &= checkTool('Node.js', 'node --version', true, nodeRequirement);
success &= checkTool('pnpm', 'pnpm --version', true, pnpmRequirement);
success &= checkTool('Java', 'java -version 2>&1', true, '21.0.0');
success &= checkTool('just', 'just --version', true, '1.30.0');

// Infrastructure Tools
console.log('');
checkTool('Docker', 'docker --version', false);
checkTool('Docker Compose', 'docker compose version', false);

// Optional Tools
console.log('');
checkTool('Codegraph', 'codegraph --version', false);

if (!success) {
  console.log(`\n${colors.red}${symbols.error} Some required tools are missing or incompatible. Please fix them before proceeding.${colors.reset}`);
  process.exit(1);
} else {
  console.log(`\n${colors.green}${symbols.success} Environment is healthy!${colors.reset}`);
}
