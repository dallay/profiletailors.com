#!/usr/bin/env bash
# tools/bazel/run_in_package.sh
# ─────────────────────────────────────────────────────────────────────────────
# Helper used by sh_test / sh_binary rules to run a command inside a specific
# package directory relative to the workspace root.
#
# Usage (from a BUILD rule):
#   sh_test(
#     name = "my_test",
#     srcs = ["//tools/bazel:run_in_package.sh"],
#     args = ["path/to/package", "command", "arg1", "arg2"],
#   )
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PACKAGE_DIR="$1"
shift
COMMAND=("$@")

# Resolve workspace root: Bazel sets BUILD_WORKSPACE_DIRECTORY when using
# `bazel run`; fall back to the script's own location otherwise.
WORKSPACE_ROOT="${BUILD_WORKSPACE_DIRECTORY:-$(cd "$(dirname "$0")/../.." && pwd)}"

TARGET_DIR="$WORKSPACE_ROOT/$PACKAGE_DIR"

if [ ! -d "$TARGET_DIR" ]; then
    echo "ERROR: Package directory not found: $TARGET_DIR" >&2
    exit 1
fi

cd "$TARGET_DIR"
exec "${COMMAND[@]}"
