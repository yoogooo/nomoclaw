#!/usr/bin/env bash
set -euo pipefail

# Unified desktop build for Windows x64 (Tauri shell + embedded Spring Boot backend).
#
# Default behavior:
# - Builds Vue frontend and syncs to src/main/resources/static/nomoclaw
# - Packages Spring Boot fat jar
# - Builds minimal Java runtime via jlink
# - Copies jar/runtime into desktop/tauri/src-tauri/resources/backend
# - Runs tauri build for x86_64-pc-windows-msvc to produce MSI
#
# Optional env:
#   SKIP_TESTS=true|false             (default: true)
#   SKIP_WEB_BUILD=true|false         (default: false)
#   MAVEN_PROFILE=prod-lite           (default: prod-lite, 推荐桌面发版使用；该 profile 使用 H2 并精简 MySQL 相关依赖)
#   TAURI_BUILD_CI=true|false         (default: true)
#   TAURI_BUNDLES=msi|nsis|msi,nsis   (default: msi)
#   SKIP_RUST_CHECK=true|false        (default: false)
#   TAURI_UPDATER_PUBKEY=<pubkey>     (optional; updater verification key embedded into app)
#   APP_VERSION=1.0.0                 (default: 1.0.0)
#   DESKTOP_VERSION=1.0.0             (alias of APP_VERSION, if set takes precedence)
#   DESKTOP_NAME_PREFIX=NomoClaw      (default: NomoClaw)
#   WINDOWS_ICON_FILE=build/windows/NomoClaw.ico (optional)

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web"
WEB_DIST_DIR="$WEB_DIR/dist"
STATIC_DIR="$ROOT_DIR/src/main/resources/static/nomoclaw"
DESKTOP_DIR="$ROOT_DIR/desktop/tauri"
DESKTOP_RES_DIR="$DESKTOP_DIR/src-tauri/resources/backend"
DESKTOP_TAURI_DIR="$DESKTOP_DIR/src-tauri"
TARGET_DIR="$ROOT_DIR/target"
BUILD_DIR="$ROOT_DIR/build/desktop-windows-x64"
DIST_DIR="$ROOT_DIR/dist"

RUST_TARGET="x86_64-pc-windows-msvc"

SKIP_TESTS="${SKIP_TESTS:-true}"
SKIP_WEB_BUILD="${SKIP_WEB_BUILD:-false}"
MAVEN_PROFILE="${MAVEN_PROFILE:-prod-lite}"
TAURI_BUILD_CI="${TAURI_BUILD_CI:-true}"
TAURI_BUNDLES="${TAURI_BUNDLES:-msi}"
SKIP_RUST_CHECK="${SKIP_RUST_CHECK:-false}"
TAURI_UPDATER_PUBKEY="${TAURI_UPDATER_PUBKEY:-}"
APP_VERSION="${APP_VERSION:-1.0.0}"
DESKTOP_VERSION="${DESKTOP_VERSION:-${APP_VERSION}}"
DESKTOP_NAME_PREFIX="${DESKTOP_NAME_PREFIX:-NomoClaw}"
WINDOWS_ICON_FILE="${WINDOWS_ICON_FILE:-}"

DESKTOP_PRODUCT_NAME=""
TAURI_CONFIG_OVERRIDE_PATH=""
BUILD_NO=""
FULL_VERSION=""
MSI_BUILD_MARKER_FILE=""

log() {
  printf '[build-desktop-win] %s\n' "$*" >&2
}

fail() {
  printf '[build-desktop-win] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

normalize_bool() {
  case "$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')" in
    true|1|yes|y) printf 'true' ;;
    *) printf 'false' ;;
  esac
}

ensure_windows_x64() {
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) ;;
    *) fail "This script only supports Windows (Git Bash / MSYS / Cygwin)." ;;
  esac

  local arch
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64) ;;
    *) fail "This script requires x64 host, current arch=$arch" ;;
  esac
}

ensure_java21_x64() {
  require_cmd java
  local major arch
  major="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{if ($1=="1") print $2; else print $1}')"
  [[ "$major" == "21" ]] || fail "Active Java major version is not 21."

  arch="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/os.arch/ {print $2}' | tr -d '\r' | head -n 1)"
  case "$arch" in
    amd64|x86_64) ;;
    *) fail "Active Java is not x64 (os.arch=$arch). Please use JDK 21 x64." ;;
  esac
}

setup_desktop_naming() {
  local v_major v_minor v_patch
  if [[ -z "$DESKTOP_VERSION" ]]; then
    DESKTOP_VERSION="1.0.0"
  fi

  [[ "$DESKTOP_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
    || fail "Invalid DESKTOP_VERSION=$DESKTOP_VERSION (expected x.y.z, e.g. 1.0.0)"

  DESKTOP_PRODUCT_NAME="$DESKTOP_NAME_PREFIX"
  [[ "$DESKTOP_PRODUCT_NAME" =~ ^[A-Z][A-Za-z0-9]*$ ]] \
    || fail "Invalid DESKTOP_NAME_PREFIX=$DESKTOP_PRODUCT_NAME (expected PascalCase, letters/digits only)"

  IFS='.' read -r v_major v_minor v_patch <<< "$DESKTOP_VERSION"
  ((v_major >= 0 && v_major <= 255)) || fail "Invalid major=$v_major (MSI requires 0..255)"
  ((v_minor >= 0 && v_minor <= 255)) || fail "Invalid minor=$v_minor (MSI requires 0..255)"
  ((v_patch >= 0 && v_patch <= 65535)) || fail "Invalid patch=$v_patch (MSI requires 0..65535)"
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

setup_rust_toolchain() {
  if ! command -v cargo >/dev/null 2>&1 && [[ -x "$HOME/.cargo/bin/cargo" ]]; then
    export PATH="$HOME/.cargo/bin:$PATH"
  fi

  if ! command -v rustup >/dev/null 2>&1 && [[ -x "$HOME/.cargo/bin/rustup" ]]; then
    export PATH="$HOME/.cargo/bin:$PATH"
  fi

  require_cmd cargo
  require_cmd rustup
  rustup target add "$RUST_TARGET" >/dev/null
}

check_rust_target_build() {
  if [[ "$(normalize_bool "$SKIP_RUST_CHECK")" == "true" ]]; then
    log "Skipping Rust target pre-check (SKIP_RUST_CHECK=true)"
    return
  fi

  log "Running Rust pre-check target=$RUST_TARGET"
  (cd "$DESKTOP_TAURI_DIR" && cargo check --target "$RUST_TARGET")
}

build_frontend_assets() {
  require_cmd pnpm
  [[ -d "$WEB_DIR" ]] || fail "web directory not found: $WEB_DIR"

  if [[ "$(normalize_bool "$SKIP_WEB_BUILD")" == "true" ]]; then
    log "Skipping web build (SKIP_WEB_BUILD=true)"
    return
  fi

  if [[ ! -d "$WEB_DIR/node_modules" ]]; then
    log "Installing web dependencies"
    pnpm --dir "$WEB_DIR" install --frozen-lockfile
  fi

  log "Building web frontend"
  # Keep base path as a plain segment to avoid Git Bash/MSYS path conversion
  # turning "/nomoclaw/" into "/Program Files/Git/nomoclaw/" on Windows.
  VITE_BASE=nomoclaw pnpm --dir "$WEB_DIR" build
  [[ -d "$WEB_DIST_DIR" ]] || fail "web dist missing: $WEB_DIST_DIR"

  log "Syncing web dist to static resources"
  rm -rf "$STATIC_DIR"
  mkdir -p "$STATIC_DIR"
  cp -R "$WEB_DIST_DIR"/. "$STATIC_DIR"/
}

build_backend_jar() {
  log "Building Spring Boot jar"
  log "Using MAVEN_PROFILE=$MAVEN_PROFILE (desktop release recommends prod-lite: H2 + smaller jar)"
  cd "$ROOT_DIR"

  if [[ -x "$ROOT_DIR/mvnw" ]]; then
    if [[ "$(normalize_bool "$SKIP_TESTS")" == "true" ]]; then
      "$ROOT_DIR/mvnw" -P"$MAVEN_PROFILE" -Dmaven.test.skip=true clean package
    else
      "$ROOT_DIR/mvnw" -P"$MAVEN_PROFILE" clean package
    fi
  else
    require_cmd mvn
    if [[ "$(normalize_bool "$SKIP_TESTS")" == "true" ]]; then
      mvn -P"$MAVEN_PROFILE" -Dmaven.test.skip=true clean package
    else
      mvn -P"$MAVEN_PROFILE" clean package
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

ensure_required_modules() {
  local modules="$1"
  local required=(
    java.base
    java.desktop
    java.instrument
    java.logging
    java.management
    java.naming
    java.net.http
    java.rmi
    java.security.jgss
    java.sql
    java.xml
    jdk.crypto.ec
    jdk.unsupported
    jdk.zipfs
  )
  local m
  for m in "${required[@]}"; do
    case ",$modules," in
      *",$m,"*) ;;
      *)
        if [[ -z "$modules" ]]; then
          modules="$m"
        else
          modules="$modules,$m"
        fi
        ;;
    esac
  done
  printf '%s' "$modules"
}

build_runtime() {
  local runtime_dir modules
  runtime_dir="$BUILD_DIR/runtime-x64"

  rm -rf "$runtime_dir"
  mkdir -p "$BUILD_DIR"

  log "Resolving JDK modules with jdeps"
  set +e
  modules="$(jdeps \
    --multi-release 21 \
    --ignore-missing-deps \
    --recursive \
    --print-module-deps \
    "$1" 2>/dev/null)"
  local jdeps_exit=$?
  set -e

  if [[ $jdeps_exit -ne 0 || -z "$modules" ]]; then
    log "jdeps auto-detection failed, using fallback modules"
    modules="java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.rmi,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.zipfs"
  fi
  modules="$(ensure_required_modules "$modules")"

  log "Creating jlink runtime (x64)"
  jlink \
    --add-modules "$modules" \
    --compress=zip-6 \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --output "$runtime_dir"

  [[ -x "$runtime_dir/bin/java.exe" ]] || fail "Runtime java binary missing: $runtime_dir/bin/java.exe"
  printf '%s' "$runtime_dir"
}

prepare_tauri_resources() {
  local jar_path runtime_dir
  jar_path="$1"
  runtime_dir="$2"

  rm -rf "$DESKTOP_RES_DIR"
  mkdir -p "$DESKTOP_RES_DIR"

  cp "$jar_path" "$DESKTOP_RES_DIR/nomoclaw.jar"
  cp -R "$runtime_dir" "$DESKTOP_RES_DIR/runtime"

  jar tf "$DESKTOP_RES_DIR/nomoclaw.jar" | grep -q '^BOOT-INF/' \
    || fail "Embedded jar validation failed: missing BOOT-INF in $DESKTOP_RES_DIR/nomoclaw.jar"
  jar tf "$DESKTOP_RES_DIR/nomoclaw.jar" | grep -q '^org/springframework/boot/loader/launch/JarLauncher.class$' \
    || fail "Embedded jar validation failed: missing JarLauncher in $DESKTOP_RES_DIR/nomoclaw.jar"
  [[ -f "$DESKTOP_RES_DIR/nomoclaw.jar" ]] || fail "Embedded jar copy failed"
  [[ -x "$DESKTOP_RES_DIR/runtime/bin/java.exe" ]] || fail "Embedded runtime copy failed"
}

build_tauri_config_override() {
  require_cmd node

  local base_config_path icon_path
  base_config_path="$DESKTOP_TAURI_DIR/tauri.conf.json"
  TAURI_CONFIG_OVERRIDE_PATH="$BUILD_DIR/tauri.conf.windows-x64.generated.json"
  icon_path=""

  if [[ -n "$WINDOWS_ICON_FILE" ]]; then
    if [[ -f "$WINDOWS_ICON_FILE" ]]; then
      icon_path="$WINDOWS_ICON_FILE"
    elif [[ -f "$ROOT_DIR/$WINDOWS_ICON_FILE" ]]; then
      icon_path="$ROOT_DIR/$WINDOWS_ICON_FILE"
    else
      fail "WINDOWS_ICON_FILE set but not found: $WINDOWS_ICON_FILE"
    fi
  elif [[ -f "$ROOT_DIR/build/windows/NomoClaw.ico" ]]; then
    icon_path="$ROOT_DIR/build/windows/NomoClaw.ico"
  elif [[ -f "$DESKTOP_TAURI_DIR/icons/icon.ico" ]]; then
    icon_path="$DESKTOP_TAURI_DIR/icons/icon.ico"
  fi

  node -e '
const fs = require("fs");
const [basePath, outPath, productName, version, updaterPubkey, iconPath, bundlesRaw] = process.argv.slice(1);
const cfg = JSON.parse(fs.readFileSync(basePath, "utf8"));
cfg.productName = productName;
cfg.version = version;
if (cfg.app && Array.isArray(cfg.app.windows)) {
  cfg.app.windows = cfg.app.windows.map((w) => ({ ...w, title: productName }));
}
cfg.bundle = cfg.bundle || {};
const bundles = String(bundlesRaw || "msi")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
cfg.bundle.targets = bundles.length ? bundles : ["msi"];
if (iconPath) {
  cfg.bundle.icon = [iconPath];
}
cfg.bundle.createUpdaterArtifacts = Boolean(updaterPubkey);
if (updaterPubkey && cfg.plugins && cfg.plugins.updater) {
  cfg.plugins.updater.pubkey = updaterPubkey;
}
fs.writeFileSync(outPath, JSON.stringify(cfg, null, 2) + "\n");
' "$base_config_path" "$TAURI_CONFIG_OVERRIDE_PATH" "$DESKTOP_PRODUCT_NAME" "$DESKTOP_VERSION" "$TAURI_UPDATER_PUBKEY" "$icon_path" "$TAURI_BUNDLES"
}

build_tauri_bundle() {
  require_cmd pnpm
  require_cmd cargo

  if [[ ! -d "$DESKTOP_DIR/node_modules" ]]; then
    log "Installing desktop dependencies"
    pnpm --dir "$DESKTOP_DIR" install --frozen-lockfile
  fi

  build_tauri_config_override

  MSI_BUILD_MARKER_FILE="$BUILD_DIR/.msi-build-start.marker"
  : > "$MSI_BUILD_MARKER_FILE"

  log "Building tauri bundle target=$RUST_TARGET productName=$DESKTOP_PRODUCT_NAME version=$DESKTOP_VERSION bundles=$TAURI_BUNDLES"
  if [[ "$(normalize_bool "$TAURI_BUILD_CI")" == "true" ]]; then
    (cd "$DESKTOP_DIR" && CI=true pnpm tauri build --target "$RUST_TARGET" --config "$TAURI_CONFIG_OVERRIDE_PATH" --bundles "$TAURI_BUNDLES")
  else
    (cd "$DESKTOP_DIR" && pnpm tauri build --target "$RUST_TARGET" --config "$TAURI_CONFIG_OVERRIDE_PATH" --bundles "$TAURI_BUNDLES")
  fi
}

validate_staged_backend_runtime() {
  local staged_backend_dir staged_java staged_jar
  staged_backend_dir="$DESKTOP_TAURI_DIR/target/$RUST_TARGET/release/resources/backend"
  staged_java="$staged_backend_dir/runtime/bin/java.exe"
  staged_jar="$staged_backend_dir/nomoclaw.jar"

  [[ -x "$staged_java" ]] || fail "Staged runtime missing: $staged_java"
  [[ -f "$staged_jar" ]] || fail "Staged backend jar missing: $staged_jar"

  jar tf "$staged_jar" | grep -q '^org/springframework/boot/loader/launch/JarLauncher.class$' \
    || fail "Staged backend jar missing JarLauncher: $staged_jar"

  "$staged_java" -Djarmode=tools -jar "$staged_jar" list-layers >/dev/null \
    || fail "Staged backend jar failed runtime probe (-Djarmode=tools): $staged_jar"

  if command -v sha256sum >/dev/null 2>&1; then
    log "Staged backend jar sha256: $(sha256sum "$staged_jar" | awk '{print $1}')"
  fi
}

collect_msi_artifact() {
  local bundle_dir output_msi selected_msi
  local msi_path msi_name
  local -a new_msis version_msis
  bundle_dir="$DESKTOP_TAURI_DIR/target/$RUST_TARGET/release/bundle/msi"
  [[ -d "$bundle_dir" ]] || fail "MSI bundle directory not found: $bundle_dir"

  [[ -n "$MSI_BUILD_MARKER_FILE" && -f "$MSI_BUILD_MARKER_FILE" ]] \
    || fail "MSI build marker missing. Build bundle step may not have run."

  new_msis=()
  while IFS= read -r -d '' msi_path; do
    new_msis+=("$msi_path")
  done < <(find "$bundle_dir" -maxdepth 1 -type f -name '*.msi' -newer "$MSI_BUILD_MARKER_FILE" -print0)

  [[ ${#new_msis[@]} -gt 0 ]] || fail "No newly generated MSI found after build marker ($MSI_BUILD_MARKER_FILE). Please clean $bundle_dir and retry."

  selected_msi=""
  if [[ ${#new_msis[@]} -eq 1 ]]; then
    selected_msi="${new_msis[0]}"
  else
    version_msis=()
    for msi_path in "${new_msis[@]}"; do
      msi_name="$(basename "$msi_path")"
      if [[ "$msi_name" == *"_${DESKTOP_VERSION}_"* || "$msi_name" == *"-${DESKTOP_VERSION}-"* ]]; then
        version_msis+=("$msi_path")
      fi
    done

    if [[ ${#version_msis[@]} -eq 1 ]]; then
      selected_msi="${version_msis[0]}"
    elif [[ ${#version_msis[@]} -gt 1 ]]; then
      selected_msi="$(ls -1t "${version_msis[@]}" | head -n 1)"
      log "Multiple new MSI matched DESKTOP_VERSION=$DESKTOP_VERSION; selected latest by mtime: $selected_msi"
    else
      selected_msi="$(ls -1t "${new_msis[@]}" | head -n 1)"
      log "No new MSI filename matched DESKTOP_VERSION=$DESKTOP_VERSION; selected latest by mtime: $selected_msi"
    fi
  fi

  [[ -n "$selected_msi" ]] || fail "Failed to resolve MSI artifact from newly generated files."

  mkdir -p "$DIST_DIR"
  output_msi="$DIST_DIR/${DESKTOP_PRODUCT_NAME}-${DESKTOP_VERSION}-windows-x64.msi"
  cp -f "$selected_msi" "$output_msi"
  log "Resolved MSI source: $selected_msi"
  log "MSI output: $output_msi"
}

main() {
  ensure_windows_x64
  ensure_java21_x64
  require_cmd jdeps
  require_cmd jlink
  setup_desktop_naming
  setup_version_metadata
  write_version_file
  setup_rust_toolchain
  check_rust_target_build

  mkdir -p "$BUILD_DIR" "$DIST_DIR"

  build_frontend_assets
  build_backend_jar

  local main_jar runtime_dir
  main_jar="$(resolve_main_jar)"
  runtime_dir="$(build_runtime "$main_jar")"
  log "Resolved backend jar: $main_jar"

  prepare_tauri_resources "$main_jar" "$runtime_dir"
  build_tauri_bundle
  validate_staged_backend_runtime

  if [[ ",$TAURI_BUNDLES," == *",msi,"* ]]; then
    collect_msi_artifact
  else
    log "TAURI_BUNDLES does not include msi; skipping dist MSI copy"
  fi

  log "Desktop Windows x64 build complete"
}

main "$@"
