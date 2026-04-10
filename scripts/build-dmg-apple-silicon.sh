#!/usr/bin/env bash
set -euo pipefail

# Build a macOS DMG for Apple Silicon (arm64) from a Spring Boot fat jar.
# Usage:
#   ./scripts/build-dmg-apple-silicon.sh
# Optional env:
#   APP_NAME=NomoClaw APP_VERSION=2026.406.31014 JAVA_OPTIONS='-Xms256m -Xmx1024m -Dspring.profiles.active=h2 -Dserver.port=18080' ./scripts/build-dmg-apple-silicon.sh
#   LOG_FILE_PATH='/tmp/NomoClaw.log' ./scripts/build-dmg-apple-silicon.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/target"
DIST_DIR="$ROOT_DIR/dist"
BUILD_DIR="$ROOT_DIR/build/macos"
RUNTIME_DIR="$BUILD_DIR/runtime-arm64"
APP_INPUT_DIR="$BUILD_DIR/app-input"
WEB_DIR="$ROOT_DIR/web"
WEB_DIST_DIR="$WEB_DIR/dist"
STATIC_DIR="$ROOT_DIR/src/main/resources/static/nomoclaw"

APP_NAME="${APP_NAME:-NomoClaw}"
APP_VERSION="${APP_VERSION:-}"
ICON_FILE="${ICON_FILE:-}"
# Default profile for customer local install: embedded H2 (no external MySQL needed).
JAVA_OPTIONS="${JAVA_OPTIONS:--Xms256m -Xmx1024m -Dspring.profiles.active=h2 -Dserver.port=18080}"
# Log file used when users launch app from Finder icon (no visible console).
# Check logs with: tail -f /tmp/NomoClaw.log
# Use a plain absolute path (no ${...} placeholders) to avoid jpackage cfg parser issues.
LOG_FILE_PATH="${LOG_FILE_PATH:-/tmp/NomoClaw.log}"
SKIP_TESTS="${SKIP_TESTS:-true}"
MAVEN_PROFILE="${MAVEN_PROFILE:-prod-lite}"
SKIP_WEB_BUILD="${SKIP_WEB_BUILD:-false}"
MAC_UI_ELEMENT="${MAC_UI_ELEMENT:-false}"

log() {
  printf '[build-dmg] %s\n' "$*"
}

fail() {
  printf '[build-dmg] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

set_lsui_element() {
  local app_image="$1"
  local ui_element="$2"
  local plist_file="$app_image/Contents/Info.plist"
  [[ -f "$plist_file" ]] || fail "Info.plist not found: $plist_file"
  if [[ "$ui_element" == "true" ]]; then
    /usr/libexec/PlistBuddy -c "Set :LSUIElement true" "$plist_file" >/dev/null 2>&1 \
      || /usr/libexec/PlistBuddy -c "Add :LSUIElement bool true" "$plist_file" >/dev/null 2>&1 \
      || fail "Failed to set LSUIElement in $plist_file"
  else
    /usr/libexec/PlistBuddy -c "Delete :LSUIElement" "$plist_file" >/dev/null 2>&1 || true
  fi
}

generate_default_app_version() {
  local year month_day hour minute second second_of_day
  year="$(date +%Y)"
  month_day="$(date +%-m%d)"
  hour=$((10#$(date +%H)))
  minute=$((10#$(date +%M)))
  second=$((10#$(date +%S)))
  second_of_day=$((hour * 3600 + minute * 60 + second))

  printf '%s.%s.%05d' "$year" "$month_day" "$second_of_day"
}

normalize_app_version() {
  local input="$1"
  local v="${input//[^0-9.]/}"
  local major minor patch

  IFS='.' read -r major minor patch _ <<< "$v"
  major="${major:-1}"
  minor="${minor:-0}"
  patch="${patch:-0}"

  [[ "$major" =~ ^[0-9]+$ ]] || major=1
  [[ "$minor" =~ ^[0-9]+$ ]] || minor=0
  [[ "$patch" =~ ^[0-9]+$ ]] || patch=0

  if [[ "$major" -le 0 ]]; then
    major=1
  fi

  printf '%s.%s.%s' "$major" "$minor" "$patch"
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

ensure_java21_arm64() {
  local current_major=""
  local desired_home=""

  if command -v java >/dev/null 2>&1; then
    current_major="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{if ($1=="1") print $2; else print $1}')"
  fi

  if [[ "$current_major" == "21" ]]; then
    return
  fi

  desired_home="$(/usr/libexec/java_home -v 21 -a arm64 2>/dev/null || true)"
  [[ -n "$desired_home" ]] || fail "JDK 21 (arm64) not found. Please install Temurin 21 arm64 first."

  export JAVA_HOME="$desired_home"
  export PATH="$JAVA_HOME/bin:$PATH"
  log "Switched JAVA_HOME to: $JAVA_HOME"

  current_major="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{if ($1=="1") print $2; else print $1}')"
  [[ "$current_major" == "21" ]] || fail "Active java is not 21 after switching JAVA_HOME."
}

build_frontend_assets() {
  [[ -d "$WEB_DIR" ]] || fail "Web directory not found: $WEB_DIR"
  require_cmd pnpm

  log "Building web frontend"
  if [[ ! -d "$WEB_DIR/node_modules" ]]; then
    log "Installing web dependencies"
    pnpm --dir "$WEB_DIR" install --frozen-lockfile
  fi

  VITE_BASE=/nomoclaw/ pnpm --dir "$WEB_DIR" build
  [[ -d "$WEB_DIST_DIR" ]] || fail "Web dist directory not found after build: $WEB_DIST_DIR"

  log "Syncing web dist to Spring static path"
  rm -rf "$STATIC_DIR"
  mkdir -p "$STATIC_DIR"
  cp -R "$WEB_DIST_DIR"/. "$STATIC_DIR"/
}

if [[ "$(uname -s)" != "Darwin" ]]; then
  fail "This script only supports macOS."
fi

if [[ "$(uname -m)" != "arm64" ]]; then
  fail "Current machine is not Apple Silicon (arm64). Please run this script on arm64 macOS."
fi

if [[ -z "$APP_VERSION" ]]; then
  APP_VERSION="$(generate_default_app_version)"
fi

case "$(printf '%s' "$MAC_UI_ELEMENT" | tr '[:upper:]' '[:lower:]')" in
  true|1|yes|y)
    MAC_UI_ELEMENT="true"
    ;;
  *)
    MAC_UI_ELEMENT="false"
    ;;
esac

APP_VERSION="$(normalize_app_version "$APP_VERSION")"
log "Using app version: $APP_VERSION"

ensure_java21_arm64
require_cmd jdeps
require_cmd jlink
require_cmd jpackage
require_cmd /usr/libexec/PlistBuddy

mkdir -p "$DIST_DIR" "$BUILD_DIR"

if [[ "$SKIP_WEB_BUILD" != "true" ]]; then
  build_frontend_assets
else
  log "Skipping web build (SKIP_WEB_BUILD=true)"
fi

log "Building Spring Boot jar"
cd "$ROOT_DIR"
if [[ -x "$ROOT_DIR/mvnw" ]]; then
  if [[ "$SKIP_TESTS" == "true" ]]; then
    "$ROOT_DIR/mvnw" -P"$MAVEN_PROFILE" -Dmaven.test.skip=true clean package
  else
    "$ROOT_DIR/mvnw" -P"$MAVEN_PROFILE" clean package
  fi
else
  require_cmd mvn
  if [[ "$SKIP_TESTS" == "true" ]]; then
    mvn -P"$MAVEN_PROFILE" -Dmaven.test.skip=true clean package
  else
    mvn -P"$MAVEN_PROFILE" clean package
  fi
fi

# Find an executable jar produced by Spring Boot (exclude original-*.jar).
# Use a bash 3.2-compatible array fill (mapfile is not available on macOS default bash).
CANDIDATES=()
while IFS= read -r candidate; do
  CANDIDATES+=("$candidate")
done < <(find "$TARGET_DIR" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | sort)
[[ ${#CANDIDATES[@]} -gt 0 ]] || fail "No jar found in $TARGET_DIR"

MAIN_JAR=""
for candidate in "${CANDIDATES[@]}"; do
  if jar tf "$candidate" | grep -q '^BOOT-INF/'; then
    MAIN_JAR="$candidate"
    break
  fi
done

if [[ -z "$MAIN_JAR" ]]; then
  MAIN_JAR="${CANDIDATES[0]}"
  log "No BOOT-INF jar detected, fallback to: $(basename "$MAIN_JAR")"
fi

JAR_NAME="$(basename "$MAIN_JAR")"
log "Using jar: $JAR_NAME"

# Derive required JDK modules for jlink.
log "Resolving JDK modules with jdeps"
set +e
MODULES="$(jdeps \
  --multi-release 21 \
  --ignore-missing-deps \
  --recursive \
  --print-module-deps \
  "$MAIN_JAR" 2>/dev/null)"
JDEPS_EXIT=$?
set -e

if [[ $JDEPS_EXIT -ne 0 || -z "$MODULES" ]]; then
  log "jdeps auto-detection failed, using fallback modules"
  MODULES="java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.rmi,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported"
fi
MODULES="$(ensure_required_modules "$MODULES")"
log "Using JDK modules: $MODULES"

log "Creating runtime image"
rm -rf "$RUNTIME_DIR"
jlink \
  --add-modules "$MODULES" \
  --compress=2 \
  --strip-native-commands \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --output "$RUNTIME_DIR"

log "Preparing minimal app input directory"
rm -rf "$APP_INPUT_DIR"
mkdir -p "$APP_INPUT_DIR"
cp "$MAIN_JAR" "$APP_INPUT_DIR/$JAR_NAME"

log "Packaging app image (arm64)"
JPACKAGE_APP_ARGS=(
  --type app-image
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --input "$APP_INPUT_DIR"
  --main-jar "$JAR_NAME"
  --runtime-image "$RUNTIME_DIR"
  --dest "$DIST_DIR"
)

for opt in $JAVA_OPTIONS; do
  JPACKAGE_APP_ARGS+=(--java-options "$opt")
done

# Persist runtime logs to file so double-click launch can still be diagnosed.
JPACKAGE_APP_ARGS+=(--java-options "-Dlogging.file.name=$LOG_FILE_PATH")
# Enable AWT lifecycle integration on macOS so Dock quit events can be handled.
JPACKAGE_APP_ARGS+=(--java-options "-Djava.awt.headless=false")
# Allow reflective access to macOS dock integration API in java.desktop/com.apple.eawt.
JPACKAGE_APP_ARGS+=(--java-options "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED")
# Prefer immediate shutdown for packaged desktop app to avoid long Dock-exit waits.
JPACKAGE_APP_ARGS+=(--java-options "-Dserver.shutdown=immediate")
# Keep a short shutdown phase timeout as a fallback when graceful components exist.
JPACKAGE_APP_ARGS+=(--java-options "-Dspring.lifecycle.timeout-per-shutdown-phase=2s")
# Auto-open local web console when launching app from Finder.
JPACKAGE_APP_ARGS+=(--java-options "-Dnomoclaw.desktop.open-browser-on-startup=true")
JPACKAGE_APP_ARGS+=(--java-options "-Dnomoclaw.desktop.open-browser-url=http://localhost:18080/nomoclaw/#/")

if [[ -n "$ICON_FILE" ]]; then
  if [[ -f "$ICON_FILE" ]]; then
    JPACKAGE_APP_ARGS+=(--icon "$ICON_FILE")
  elif [[ -f "$ROOT_DIR/$ICON_FILE" ]]; then
    JPACKAGE_APP_ARGS+=(--icon "$ROOT_DIR/$ICON_FILE")
  else
    fail "ICON_FILE set but not found: $ICON_FILE"
  fi
fi

APP_IMAGE="$DIST_DIR/${APP_NAME}.app"
rm -rf "$APP_IMAGE"
jpackage "${JPACKAGE_APP_ARGS[@]}"
[[ -d "$APP_IMAGE" ]] || fail "App image not found at expected path: $APP_IMAGE"
set_lsui_element "$APP_IMAGE" "$MAC_UI_ELEMENT"

log "Packaging DMG (arm64)"
jpackage \
  --type dmg \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --app-image "$APP_IMAGE" \
  --dest "$DIST_DIR"

DEFAULT_DMG="$DIST_DIR/${APP_NAME}-${APP_VERSION}.dmg"
ARCH_DMG="$DIST_DIR/${APP_NAME}-${APP_VERSION}-macos-aarch64.dmg"
if [[ -f "$DEFAULT_DMG" ]]; then
  mv -f "$DEFAULT_DMG" "$ARCH_DMG"
fi

[[ -f "$ARCH_DMG" ]] || fail "DMG not found at expected path: $ARCH_DMG"

log "Done: $ARCH_DMG"
