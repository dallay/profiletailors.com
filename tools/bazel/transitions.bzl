# tools/bazel/transitions.bzl
# ─────────────────────────────────────────────────────────────────────────────
# Starlark configuration transitions for cross-compilation targets.
#
# These are PLACEHOLDERS — they compile but don't do anything until the
# corresponding platform rules (rules_android, rules_apple) are enabled
# in MODULE.bazel.
#
# Usage pattern (future):
#   load("//tools/bazel:transitions.bzl", "android_arm64_transition")
#   kt_android_library(
#       name = "shared",
#       cfg = android_arm64_transition,
#       ...
#   )
# ─────────────────────────────────────────────────────────────────────────────

def _android_arm64_transition_impl(settings, attr):
    """Force the build to target Android arm64-v8a."""
    return {
        "//command_line_option:platforms": ["//tools/bazel:android_arm64"],
        "//command_line_option:cpu": "arm64-v8a",
    }

android_arm64_transition = transition(
    implementation = _android_arm64_transition_impl,
    inputs = [],
    outputs = [
        "//command_line_option:platforms",
        "//command_line_option:cpu",
    ],
)

def _ios_arm64_transition_impl(settings, attr):
    """Force the build to target iOS arm64."""
    return {
        "//command_line_option:platforms": ["//tools/bazel:ios_arm64"],
        "//command_line_option:cpu": "arm64",
    }

ios_arm64_transition = transition(
    implementation = _ios_arm64_transition_impl,
    inputs = [],
    outputs = [
        "//command_line_option:platforms",
        "//command_line_option:cpu",
    ],
)

def _kmp_transitions_impl(settings, attr):
    """Multi-platform transition for KMP targets (JVM + Android + iOS)."""

    # Returns one configuration per target platform.
    return [
        # JVM (server-side Kotlin)
        {"//command_line_option:platforms": ["//tools/bazel:linux_x86_64"]},
        # Android (when enabled)
        # {"//command_line_option:platforms": ["//tools/bazel:android_arm64"]},
        # iOS (when enabled)
        # {"//command_line_option:platforms": ["//tools/bazel:ios_arm64"]},
    ]

kmp_multi_transition = transition(
    implementation = _kmp_transitions_impl,
    inputs = [],
    outputs = ["//command_line_option:platforms"],
)
