#!/usr/bin/env bash
#
# ci-local.sh: run the CI checks locally before pushing.
#
# Mirrors the `.github/workflows/pull-request.yml` jobs that can be executed on
# a developer machine without paid external services. Catches failures like
# MaxLineLength, ReturnCount, lint errors, or unit-test regressions without
# burning CI minutes.
#
# Stages (fast -> slow):
#   1. shell tests  -> release tooling tests
#   2. detekt       -> static_analysis_detekt
#   3. ktlint       -> static_analysis_ktlint
#   4. rust         -> test_rust_unit
#   5. deny         -> check_native_licenses
#   6. unit tests   -> test_android_modules_unit
#   7. android lint -> static_analysis_android_lint
#   8. demo app     -> demo_app_release_build
#   9. Wcash AAR    -> wcash_release_boundaries
#  10. androidTest  -> (approximation of) test_android_modules_emulator
#
# Stage 10 uses a Gradle Managed Device (pixel2Target, SDK 36). It downloads an
# AVD on first run (~1.5 GB) and is the slowest stage.
#
# Usage:
#   ./scripts/ci-local.sh             # run every stage in sequence
#   ./scripts/ci-local.sh fast        # stages 1-3 only (shell + lint + style)
#   ./scripts/ci-local.sh quick       # stages 1-6 (fast + rust + deny + unit tests)
#   ./scripts/ci-local.sh full        # all stages including androidTest (default)
#   ./scripts/ci-local.sh shell       # run release tooling tests
#   ./scripts/ci-local.sh detekt      # run one named stage
#
# Requirements:
#   - JDK 17 or 21 (Android Gradle Plugin 8.13.x does not support JDK 25+).
#     Set JAVA_HOME if your default `java` is a different version.
#   - Android SDK installed at ANDROID_HOME or $HOME/Library/Android/sdk.
#   - For stage 4, a Rust toolchain matching rust-toolchain.toml (rustup installs
#     it automatically on first cargo invocation). The first run is slow because
#     it builds ~640 crates; later runs are incremental.
#   - For stage 5, cargo-deny (`cargo install cargo-deny --locked`). The stage fails with
#     that hint when it is missing, the same way CI would.
#   - For stage 10, an Apple Silicon Mac needs the `aosp` SDK-36 system image.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

GRADLE="./gradlew"

stage_shell() {
    echo "==> [1/10] shell tests (release tooling tests)"
    ./scripts/tests/run-tests.sh
}

stage_detekt() {
    echo "==> [2/10] detekt (static_analysis_detekt)"
    "${GRADLE}" detektAll
}

stage_ktlint() {
    echo "==> [3/10] ktlint (static_analysis_ktlint)"
    "${GRADLE}" ktlint
}

# `backend-lib` remains unlocked because its Cargo.lock currently cannot satisfy Cargo.toml.
# The Wcash crate has an independently verified lockfile and the Make target enforces it.
stage_rust() {
    echo "==> [4/10] rust (test_rust_unit)"
    make test-rust
}

stage_deny() {
    echo "==> [5/10] cargo deny (check_native_licenses)"
    if ! cargo --list | grep -q '^    deny\b'; then
        echo "error: cargo-deny is not installed. Install it with:" >&2
        echo "    cargo install cargo-deny --locked" >&2
        return 1
    fi
    make deny-rust
}

stage_unit() {
    echo "==> [6/10] unit tests (test_android_modules_unit)"
    "${GRADLE}" test
}

stage_lint() {
    echo "==> [7/10] android lint (static_analysis_android_lint)"
    "${GRADLE}" :sdk-lib:lintRelease :wcash-android-sdk:lintRelease :demo-app:lintZcashmainnetRelease
}

stage_demoapp() {
    echo "==> [8/10] demo app release build (demo_app_release_build)"
    "${GRADLE}" assembleRelease
}

stage_wcash_release() {
    echo "==> [9/10] Wcash minified release boundary checks"
    make verify-wcash-release
}

stage_androidtest() {
    echo "==> [10/10] android instrumentation tests (test_android_modules_emulator)"
    echo "    Runs the same tests on a Gradle managed Pixel 2 (SDK 36) virtual device."
    "${GRADLE}" \
        :sdk-incubator-lib:pixel2TargetDebugAndroidTest \
        :sdk-lib:pixel2TargetDebugAndroidTest \
        :lightwallet-client-lib:pixel2TargetDebugAndroidTest \
        :backend-lib:pixel2TargetDebugAndroidTest \
        :wcash-android-sdk:pixel2TargetDebugAndroidTest \
        :wcash-sdk-consumer-test:pixel2TargetReleaseAndroidTest
}

run_all() {
    stage_shell
    stage_detekt
    stage_ktlint
    stage_rust
    stage_deny
    stage_unit
    stage_lint
    stage_demoapp
    stage_wcash_release
    stage_androidtest
}

run_fast() {
    stage_shell
    stage_detekt
    stage_ktlint
}

run_quick() {
    run_fast
    stage_rust
    stage_deny
    stage_unit
}

case "${1:-full}" in
    fast)         run_fast ;;
    quick)        run_quick ;;
    full)         run_all ;;
    shell)        stage_shell ;;
    detekt)       stage_detekt ;;
    ktlint)       stage_ktlint ;;
    rust)         stage_rust ;;
    deny)         stage_deny ;;
    unit)         stage_unit ;;
    lint)         stage_lint ;;
    demoapp)      stage_demoapp ;;
    wcash-release) stage_wcash_release ;;
    androidtest)  stage_androidtest ;;
    -h|--help|help)
        grep -E '^# ' "$0" | sed 's/^# \{0,1\}//'
        exit 0
        ;;
    *)
        echo "error: unknown stage '$1'" >&2
        echo "run '$0 help' for usage" >&2
        exit 2
        ;;
esac

echo
echo "==> ci-local.sh: all requested stages passed"
