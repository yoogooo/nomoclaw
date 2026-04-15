#!/usr/bin/env bash
set -euo pipefail

# Unified desktop build for macOS (Tauri shell + embedded Spring Boot backend).
#
# Default behavior:
# - Builds Vue frontend and syncs to src/main/resources/static/nomoclaw
# - Packages Spring Boot fat jar
# - Builds minimal Java runtime via jlink
# - Copies jar/runtime into desktop/tauri/src-tauri/resources/backend
# - Runs tauri build to produce .app/.dmg
#
# Optional env:
#   TARGET_ARCH=arm64|x64|auto      (default: auto)
#   SKIP_TESTS=true|false           (default: true)
#   SKIP_WEB_BUILD=true|false       (default: false)
#   MAVEN_PROFILE=prod-lite         (default: prod-lite)
#   BUILD_TARGET_DMG=true|false     (default: true)
#   TAURI_BUILD_CI=true|false       (default: true)
#   TAURI_UPDATER_PUBKEY=<pubkey>   (optional; when set, updater artifacts are generated)
#   APP_VERSION=1.0.0               (default: 1.0.0)
#   DESKTOP_VERSION=1.0.0           (alias of APP_VERSION, if set takes precedence)

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web"
WEB_DIST_DIR="$WEB_DIR/dist"
STATIC_DIR="$ROOT_DIR/src/main/resources/static/nomoclaw"
DESKTOP_DIR="$ROOT_DIR/desktop/tauri"
DESKTOP_RES_DIR="$DESKTOP_DIR/src-tauri/resources/backend"
DESKTOP_TAURI_DIR="$DESKTOP_DIR/src-tauri"
TARGET_DIR="$ROOT_DIR/target"
BUILD_DIR="$ROOT_DIR/build/desktop"
DIST_DIR="$ROOT_DIR/dist"

TARGET_ARCH="${TARGET_ARCH:-auto}"
SKIP_TESTS="${SKIP_TESTS:-true}"
SKIP_WEB_BUILD="${SKIP_WEB_BUILD:-false}"
MAVEN_PROFILE="${MAVEN_PROFILE:-prod-lite}"
BUILD_TARGET_DMG="${BUILD_TARGET_DMG:-true}"
TAURI_BUILD_CI="${TAURI_BUILD_CI:-true}"
TAURI_UPDATER_PUBKEY="${TAURI_UPDATER_PUBKEY:-}"
APP_VERSION="${APP_VERSION:-1.0.0}"
DESKTOP_VERSION="${DESKTOP_VERSION:-${APP_VERSION}}"
DESKTOP_NAME_PREFIX="${DESKTOP_NAME_PREFIX:-NomoClaw}"

ARCH_PREFIX=()
JAVA_HOME_SELECTED=""
RUST_TARGET=""
DESKTOP_PRODUCT_NAME=""
TAURI_CONFIG_OVERRIDE_PATH=""
BUILD_NO=""
FULL_VERSION=""

log() {
  printf '[build-desktop] %s\n' "$*" >&2
}

fail() {
  printf '[build-desktop] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

run_arch() {
  if [[ ${#ARCH_PREFIX[@]} -gt 0 ]]; then
    "${ARCH_PREFIX[@]}" "$@"
  else
    "$@"
  fi
}

normalize_target_arch() {
  local host_arch
  host_arch="$(uname -m)"

  case "$TARGET_ARCH" in
    arm64)
      TARGET_ARCH="arm64"
      ;;
    x64|x86_64)
      TARGET_ARCH="x64"
      ;;
    auto)
      if [[ "$host_arch" == "arm64" ]]; then
        TARGET_ARCH="arm64"
      elif [[ "$host_arch" == "x86_64" ]]; then
        TARGET_ARCH="x64"
      else
        fail "Unsupported host arch: $host_arch"
      fi
      ;;
    *)
      fail "Unsupported TARGET_ARCH=$TARGET_ARCH (expected arm64/x64/auto)"
      ;;
  esac

  if [[ "$TARGET_ARCH" == "arm64" ]]; then
    RUST_TARGET="aarch64-apple-darwin"
  else
    RUST_TARGET="x86_64-apple-darwin"
  fi
}

setup_desktop_naming() {
  if [[ -z "$DESKTOP_VERSION" ]]; then
    DESKTOP_VERSION="1.0.0"
  fi

  if [[ ! "$DESKTOP_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    fail "Invalid DESKTOP_VERSION=$DESKTOP_VERSION (expected x.y.z, e.g. 1.0.0)"
  fi

  DESKTOP_PRODUCT_NAME="${DESKTOP_NAME_PREFIX}"
  [[ "$DESKTOP_PRODUCT_NAME" =~ ^[A-Z][A-Za-z0-9]*$ ]] \
    || fail "Invalid DESKTOP_NAME_PREFIX=$DESKTOP_PRODUCT_NAME (expected PascalCase, letters/digits only)"
}

setup_version_metadata() {
  local build_date hour minute second seconds_of_day
  build_date="$(date +%y%m%d)"
  hour="$(date +%H)"
  minute="$(date +%M)"
  second="$(date +%S)"
  seconds_of_day=$((10#$hour * 3600 + 10#$minute * 60 + 10#$second))

  BUILD_NO="${build_date}.${seconds_of_day}"
  FULL_VERSION="${DESKTOP_VERSION}+${BUILD_NO}"
}

write_version_file() {
  local version_file
  version_file="$ROOT_DIR/build/version.txt"
  mkdir -p "$(dirname "$version_file")"
  printf '%s\n' "$FULL_VERSION" > "$version_file"
  log "Build Version: $FULL_VERSION"
  log "Version file: $version_file"
}

setup_arch_prefix() {
  local host_arch
  host_arch="$(uname -m)"

  if [[ "$TARGET_ARCH" == "x64" && "$host_arch" == "arm64" ]]; then
    require_cmd arch
    arch -x86_64 /usr/bin/true >/dev/null 2>&1 || fail "Rosetta is required for x64 build on Apple Silicon"
    ARCH_PREFIX=(arch -x86_64)
  else
    ARCH_PREFIX=()
  fi
}

setup_java_toolchain() {
  local java_arch java_home
  if [[ "$TARGET_ARCH" == "arm64" ]]; then
    java_arch="arm64"
  else
    java_arch="x86_64"
  fi

  java_home="$(/usr/libexec/java_home -v 21 -a "$java_arch" 2>/dev/null || true)"
  [[ -n "$java_home" ]] || fail "JDK 21 ($java_arch) not found"

  JAVA_HOME_SELECTED="$java_home"
  export JAVA_HOME="$JAVA_HOME_SELECTED"
  export PATH="$JAVA_HOME/bin:$PATH"

  run_arch "$JAVA_HOME/bin/java" -version >/dev/null 2>&1 || fail "Java toolchain check failed for $java_arch"
  require_cmd jdeps
  require_cmd jlink
}

setup_rust_toolchain() {
  if ! command -v cargo >/dev/null 2>&1 && [[ -x "$HOME/.cargo/bin/cargo" ]]; then
    export PATH="$HOME/.cargo/bin:$PATH"
  fi

  if ! command -v cargo >/dev/null 2>&1; then
    fail "Missing command: cargo. Install Rust: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
  fi

  if ! command -v rustup >/dev/null 2>&1 && [[ -x "$HOME/.cargo/bin/rustup" ]]; then
    export PATH="$HOME/.cargo/bin:$PATH"
  fi
  command -v rustup >/dev/null 2>&1 || fail "Missing command: rustup. Please reinstall Rust via rustup."

  # rustup binary is usually host-arch only (arm64 on Apple Silicon). Running it under
  # `arch -x86_64` can fail with "Bad CPU type in executable".
  # Adding a Rust target does not require process arch emulation.
  rustup target add "$RUST_TARGET" >/dev/null
}

build_frontend_assets() {
  require_cmd pnpm

  if [[ "$SKIP_WEB_BUILD" == "true" ]]; then
    log "Skipping web build (SKIP_WEB_BUILD=true)"
    return
  fi

  [[ -d "$WEB_DIR" ]] || fail "web directory not found: $WEB_DIR"

  if [[ ! -d "$WEB_DIR/node_modules" ]]; then
    log "Installing web dependencies"
    pnpm --dir "$WEB_DIR" install --frozen-lockfile
  fi

  log "Building web frontend"
  VITE_BASE=nomoclaw pnpm --dir "$WEB_DIR" build

  [[ -d "$WEB_DIST_DIR" ]] || fail "web dist missing: $WEB_DIST_DIR"
  log "Syncing web dist to static resources"
  rm -rf "$STATIC_DIR"
  mkdir -p "$STATIC_DIR"
  cp -R "$WEB_DIST_DIR"/. "$STATIC_DIR"/
}

build_backend_jar() {
  log "Building Spring Boot jar"
  cd "$ROOT_DIR"

  if [[ -x "$ROOT_DIR/mvnw" ]]; then
    if [[ "$SKIP_TESTS" == "true" ]]; then
      run_arch "$ROOT_DIR/mvnw" -P"$MAVEN_PROFILE" -Dmaven.test.skip=true clean package
    else
      run_arch "$ROOT_DIR/mvnw" -P"$MAVEN_PROFILE" clean package
    fi
  else
    require_cmd mvn
    if [[ "$SKIP_TESTS" == "true" ]]; then
      run_arch mvn -P"$MAVEN_PROFILE" -Dmaven.test.skip=true clean package
    else
      run_arch mvn -P"$MAVEN_PROFILE" clean package
    fi
  fi
}

resolve_main_jar() {
  local candidates candidate
  candidates=()
  while IFS= read -r candidate; do
    candidates+=("$candidate")
  done < <(find "$TARGET_DIR" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | sort)

  [[ ${#candidates[@]} -gt 0 ]] || fail "No executable jar found in $TARGET_DIR"

  for candidate in "${candidates[@]}"; do
    if jar tf "$candidate" | grep -q '^BOOT-INF/'; then
      printf '%s' "$candidate"
      return
    fi
  done

  fail "No Spring Boot fat jar found in $TARGET_DIR"
}

build_runtime() {
  local runtime_dir runtime_bin_dir modules
  runtime_dir="$BUILD_DIR/runtime-$TARGET_ARCH"
  runtime_bin_dir="$runtime_dir/bin"

  rm -rf "$runtime_dir"
  mkdir -p "$BUILD_DIR"

  # Conservative module set compatible with current backend dependencies.
  modules="java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.rmi,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.zipfs"

  log "Creating jlink runtime ($TARGET_ARCH)"
  run_arch jlink \
    --add-modules "$modules" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output "$runtime_dir"

  if [[ -x "$runtime_dir/bin/java" ]]; then
    :
  elif [[ -x "$runtime_dir/Contents/Home/bin/java" ]]; then
    mkdir -p "$runtime_bin_dir"
    ln -sf "../Contents/Home/bin/java" "$runtime_bin_dir/java"
  else
    fail "Runtime java binary missing: $runtime_dir/bin/java"
  fi

  printf '%s' "$runtime_dir"
}

prepare_tauri_resources() {
  local jar_path runtime_dir
  jar_path="$1"
  runtime_dir="$2"

  rm -rf "$DESKTOP_RES_DIR"
  mkdir -p "$DESKTOP_RES_DIR"

  cp "$jar_path" "$DESKTOP_RES_DIR/nomoclaw.jar"
  # Use -L to dereference symlinks in jlink runtime (notably under legal/),
  # which avoids tauri-bundler permission issues when walking packaged resources.
  cp -RL "$runtime_dir" "$DESKTOP_RES_DIR/runtime"
  rm -rf "$DESKTOP_RES_DIR/runtime/legal"
  chmod -R u+rwX,go+rX "$DESKTOP_RES_DIR/runtime"

  [[ -f "$DESKTOP_RES_DIR/nomoclaw.jar" ]] || fail "Embedded jar copy failed"
  [[ -x "$DESKTOP_RES_DIR/runtime/bin/java" ]] || fail "Embedded runtime copy failed"
}

build_tauri_config_override() {
  require_cmd node

  local base_config_path
  base_config_path="$DESKTOP_TAURI_DIR/tauri.conf.json"
  TAURI_CONFIG_OVERRIDE_PATH="$BUILD_DIR/tauri.conf.$TARGET_ARCH.generated.json"

  node -e '
const fs = require("fs");
const [basePath, outPath, productName, version, updaterPubkey] = process.argv.slice(1);
const cfg = JSON.parse(fs.readFileSync(basePath, "utf8"));
cfg.productName = productName;
cfg.version = version;
if (cfg.app && Array.isArray(cfg.app.windows)) {
  cfg.app.windows = cfg.app.windows.map((w) => ({
    ...w,
    title: productName
  }));
}
cfg.bundle = cfg.bundle || {};
cfg.bundle.createUpdaterArtifacts = Boolean(updaterPubkey);
if (updaterPubkey && cfg.plugins && cfg.plugins.updater) {
  cfg.plugins.updater.pubkey = updaterPubkey;
}
fs.writeFileSync(outPath, JSON.stringify(cfg, null, 2) + "\n");
' "$base_config_path" "$TAURI_CONFIG_OVERRIDE_PATH" "$DESKTOP_PRODUCT_NAME" "$DESKTOP_VERSION" "$TAURI_UPDATER_PUBKEY"
}

build_tauri_bundle() {
  require_cmd pnpm
  require_cmd cargo

  if [[ ! -d "$DESKTOP_DIR/node_modules" ]]; then
    log "Installing desktop dependencies"
    pnpm --dir "$DESKTOP_DIR" install --frozen-lockfile
  fi

  local bundles_flag=""
  if [[ "$BUILD_TARGET_DMG" == "true" ]]; then
    bundles_flag="--bundles app,dmg"
  else
    bundles_flag="--bundles app"
  fi

  build_tauri_config_override

  if [[ "$TAURI_BUILD_CI" == "true" ]]; then
    log "Tauri build will run with CI=true"
    log "Building tauri bundle target=$RUST_TARGET productName=$DESKTOP_PRODUCT_NAME version=$DESKTOP_VERSION"
    (cd "$DESKTOP_DIR" && CI=true run_arch pnpm tauri build --target "$RUST_TARGET" --config "$TAURI_CONFIG_OVERRIDE_PATH" $bundles_flag)
  else
    log "Building tauri bundle target=$RUST_TARGET productName=$DESKTOP_PRODUCT_NAME version=$DESKTOP_VERSION"
    (cd "$DESKTOP_DIR" && run_arch pnpm tauri build --target "$RUST_TARGET" --config "$TAURI_CONFIG_OVERRIDE_PATH" $bundles_flag)
  fi

  log "Bundle output: $DESKTOP_TAURI_DIR/target/$RUST_TARGET/release/bundle"
}

collect_dmg_artifact() {
  local bundle_dir latest_dmg output_dmg platform
  bundle_dir="$DESKTOP_TAURI_DIR/target/$RUST_TARGET/release/bundle/dmg"
  [[ -d "$bundle_dir" ]] || fail "DMG bundle directory not found: $bundle_dir"

  latest_dmg="$(find "$bundle_dir" -maxdepth 1 -type f -name '*.dmg' | sort | tail -n 1)"
  [[ -n "$latest_dmg" ]] || fail "No DMG found under: $bundle_dir"

  mkdir -p "$DIST_DIR"
  if [[ "$TARGET_ARCH" == "arm64" ]]; then
    platform="macos-arm64"
  else
    platform="macos-x64"
  fi
  output_dmg="$DIST_DIR/${DESKTOP_PRODUCT_NAME}-${DESKTOP_VERSION}-${platform}.dmg"
  cp -f "$latest_dmg" "$output_dmg"
  log "DMG output: $output_dmg"
}

main() {
  [[ "$(uname -s)" == "Darwin" ]] || fail "This script only supports macOS"

  normalize_target_arch
  setup_desktop_naming
  setup_version_metadata
  write_version_file
  setup_arch_prefix
  setup_java_toolchain
  setup_rust_toolchain

  mkdir -p "$BUILD_DIR" "$DIST_DIR"

  build_frontend_assets
  build_backend_jar

  local main_jar runtime_dir
  main_jar="$(resolve_main_jar)"
  runtime_dir="$(build_runtime)"

  log "Resolved backend jar: $main_jar"
  prepare_tauri_resources "$main_jar" "$runtime_dir"

  build_tauri_bundle
  if [[ "$BUILD_TARGET_DMG" == "true" ]]; then
    collect_dmg_artifact
  else
    log "BUILD_TARGET_DMG=false; skipping dist DMG copy"
  fi
  log "Desktop build complete"
}

main "$@"
