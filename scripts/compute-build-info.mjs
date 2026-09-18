#!/usr/bin/env node
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");

function gitShortSha() {
  if (process.env.GIT_SHA) {
    return process.env.GIT_SHA;
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
