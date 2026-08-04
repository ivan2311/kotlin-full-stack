#!/bin/bash
# SessionStart hook: warm the Gradle build so tests and compilation are ready
# in Claude Code on the web sessions. The container state is cached after this
# completes, so downloading the Gradle distribution, project dependencies, and
# the Kotlin/Compose/Wasm/Node toolchains here makes later commands fast.
set -euo pipefail

# Only run in the remote (Claude Code on the web) environment; a local machine
# already has whatever the developer set up.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# Resolve dependencies and compile every module (main + test) without running
# tests. This downloads the toolchains and warms the build cache; it mirrors what
# CI compiles, so a later `./gradlew test` or Wasm build reuses the cache.
#   - :shared / :server / :web classes cover all Kotlin/JVM/Wasm compilation
#   - the Wasm dev distribution pulls the Node/webpack toolchain
./gradlew --no-daemon \
  :shared:jvmTestClasses \
  :server:testClasses \
  :web:wasmJsBrowserDevelopmentExecutableDistribution \
  --stacktrace
