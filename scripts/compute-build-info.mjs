#!/usr/bin/env node
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");

function gitShortSha() {
  const suppliedSha = process.env.GIT_SHA?.trim();
  if (suppliedSha) {
    return suppliedSha.slice(0, 7);
  }
  try {
    return execSync("git rev-parse --short HEAD", { cwd: root }).toString().trim();
  } catch {
    return "local";
  }
}

function packageVersion(pkgJsonPath) {
  try {
    const pkg = JSON.parse(readFileSync(pkgJsonPath, "utf8"));
    return pkg.version ?? "0.0.0";
  } catch {
    return "0.0.0";
  }
}

/**
 * Computes frontend build metadata for a package.
 *
 * The package version falls back to `0.0.0` when the file cannot be read or
 * parsed, or when its version is missing. The Git revision uses the first seven
 * characters of `GIT_SHA`, then the repository's current revision, and finally
 * `local`. The build time is generated at each call in ISO 8601 format.
 */
export function computeBuildInfo(pkgJsonPath) {
  return {
    version: packageVersion(pkgJsonPath),
    gitSha: gitShortSha(),
    buildTime: new Date().toISOString(),
  };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const info = computeBuildInfo(resolve(root, "package.json"));
  process.stdout.write(JSON.stringify(info));
}
