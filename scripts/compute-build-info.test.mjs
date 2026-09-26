import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { computeBuildInfo } from "./compute-build-info.mjs";

function withTempDir(fn) {
  const dir = mkdtempSync(join(tmpdir(), "build-info-test-"));
  try {
    return fn(dir);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test("computeBuildInfo extracts version from requested package.json", () => {
  withTempDir((dir) => {
    const pkgPath = join(dir, "package.json");
    writeFileSync(pkgPath, JSON.stringify({ version: "2.4.6" }));

    const info = computeBuildInfo(pkgPath);
    assert.equal(info.version, "2.4.6");
  });
});

test("computeBuildInfo uses shortened GIT_SHA environment variable when provided", () => {
  withTempDir((dir) => {
    const pkgPath = join(dir, "package.json");
    writeFileSync(pkgPath, JSON.stringify({ version: "1.0.0" }));

    const originalGitSha = process.env.GIT_SHA;
    try {
      process.env.GIT_SHA = "94f443508f1f2ff469bd4ced602c476e2bbb3819";
      const info = computeBuildInfo(pkgPath);
      assert.equal(info.gitSha, "94f4435");
    } finally {
      if (originalGitSha === undefined) {
        delete process.env.GIT_SHA;
      } else {
        process.env.GIT_SHA = originalGitSha;
      }
    }
  });
});

test("computeBuildInfo generates a valid ISO 8601 buildTime timestamp", () => {
  withTempDir((dir) => {
    const pkgPath = join(dir, "package.json");
    writeFileSync(pkgPath, JSON.stringify({ version: "1.0.0" }));

    const info = computeBuildInfo(pkgPath);
    assert.equal(typeof info.buildTime, "string");
    assert.notEqual(Date.parse(info.buildTime), NaN);
    assert.equal(info.buildTime, new Date(info.buildTime).toISOString());
  });
});

test("computeBuildInfo falls back to 0.0.0 for non-existent, invalid, or versionless package.json", () => {
  withTempDir((dir) => {
    const missingPkgPath = join(dir, "missing-package.json");
    assert.equal(computeBuildInfo(missingPkgPath).version, "0.0.0");

    const invalidPkgPath = join(dir, "invalid-package.json");
    writeFileSync(invalidPkgPath, "{ invalid json");
    assert.equal(computeBuildInfo(invalidPkgPath).version, "0.0.0");

    const noVersionPkgPath = join(dir, "no-version-package.json");
    writeFileSync(noVersionPkgPath, JSON.stringify({ name: "test-package" }));
    assert.equal(computeBuildInfo(noVersionPkgPath).version, "0.0.0");
  });
});
