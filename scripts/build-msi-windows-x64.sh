#!/usr/bin/env bash
set -euo pipefail

# Build a Windows MSI (x64) from a Spring Boot fat jar.
# Run this script in Git Bash on Windows.
#
# Usage:
#   ./scripts/build-msi-windows-x64.sh
#
# Optional env:
#   APP_NAME=NomoClaw APP_VERSION=2026.406.31014 JAVA_OPTIONS='-Xms256m -Xmx1024m -Dspring.profiles.active=h2 -Dserver.port=18080' ./scripts/build-msi-windows-x64.sh
#   ICON_FILE=build/windows/NomoClaw.ico ./scripts/build-msi-windows-x64.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/target"
DIST_DIR="$ROOT_DIR/dist"
BUILD_DIR="$ROOT_DIR/build/windows"
RUNTIME_DIR="$BUILD_DIR/runtime-x64"
APP_INPUT_DIR="$BUILD_DIR/app-input"
WEB_DIR="$ROOT_DIR/web"
WEB_DIST_DIR="$WEB_DIR/dist"
STATIC_DIR="$ROOT_DIR/src/main/resources/static/nomoclaw"

APP_NAME="${APP_NAME:-NomoClaw}"
APP_VERSION="${APP_VERSION:-}"
ICON_FILE="${ICON_FILE:-}"
JAVA_OPTIONS="${JAVA_OPTIONS:--Xms256m -Xmx1024m -Dspring.profiles.active=h2 -Dserver.port=18080}"
LOG_FILE_PATH="${LOG_FILE_PATH:-NomoClaw.log}"
SKIP_TESTS="${SKIP_TESTS:-true}"
MAVEN_PROFILE="${MAVEN_PROFILE:-prod-lite}"
SKIP_WEB_BUILD="${SKIP_WEB_BUILD:-false}"
WIN_MENU_GROUP="${WIN_MENU_GROUP:-NomoClaw}"
WIN_PER_USER_INSTALL="${WIN_PER_USER_INSTALL:-true}"

log() {
  printf '[build-msi] %s\n' "$*"
}

fail() {
  printf '[build-msi] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
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

ensure_windows_x64() {
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
      ;;
    *)
      fail "This script only supports Windows (Git Bash / MSYS / Cygwin)."
      ;;
  esac
}

ensure_java21_x64() {
  require_cmd java
  local major arch
  major="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{if ($1=="1") print $2; else print $1}')"
  [[ "$major" == "21" ]] || fail "Active Java major version is not 21."

  arch="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/os.arch/ {print $2}' | tr -d '\r' | head -n 1)"
  case "$arch" in
    amd64|x86_64)
      ;;
    *)
      fail "Active Java is not x64 (os.arch=$arch). Please use JDK 21 x64."
      ;;
  esac
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

if [[ -z "$APP_VERSION" ]]; then
  APP_VERSION="$(generate_default_app_version)"
fi
APP_VERSION="$(normalize_app_version "$APP_VERSION")"
log "Using app version: $APP_VERSION"

case "$(printf '%s' "$WIN_PER_USER_INSTALL" | tr '[:upper:]' '[:lower:]')" in
  true|1|yes|y)
    WIN_PER_USER_INSTALL="true"
    ;;
  *)
    WIN_PER_USER_INSTALL="false"
    ;;
esac

ensure_windows_x64
ensure_java21_x64
require_cmd jdeps
require_cmd jlink
require_cmd jpackage

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
  MODULES="java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.rmi,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.zipfs"
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

log "Packaging MSI (x64)"
JPACKAGE_ARGS=(
  --type msi
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --input "$APP_INPUT_DIR"
  --main-jar "$JAR_NAME"
  --runtime-image "$RUNTIME_DIR"
  --dest "$DIST_DIR"
  --win-dir-chooser
  --win-menu
  --win-menu-group "$WIN_MENU_GROUP"
  --win-shortcut
)

if [[ "$WIN_PER_USER_INSTALL" == "true" ]]; then
  JPACKAGE_ARGS+=(--win-per-user-install)
fi

for opt in $JAVA_OPTIONS; do
  JPACKAGE_ARGS+=(--java-options "$opt")
done
JPACKAGE_ARGS+=(--java-options "-Dlogging.file.name=$LOG_FILE_PATH")
JPACKAGE_ARGS+=(--java-options "-Djava.awt.headless=false")
JPACKAGE_ARGS+=(--java-options "-Dnomoclaw.desktop.open-browser-on-startup=true")
JPACKAGE_ARGS+=(--java-options "-Dnomoclaw.desktop.open-browser-url=http://localhost:18080/nomoclaw/#/")

if [[ -n "$ICON_FILE" ]]; then
  if [[ -f "$ICON_FILE" ]]; then
    JPACKAGE_ARGS+=(--icon "$ICON_FILE")
  elif [[ -f "$ROOT_DIR/$ICON_FILE" ]]; then
    JPACKAGE_ARGS+=(--icon "$ROOT_DIR/$ICON_FILE")
  else
    fail "ICON_FILE set but not found: $ICON_FILE"
  fi
fi

jpackage "${JPACKAGE_ARGS[@]}"

DEFAULT_MSI="$DIST_DIR/${APP_NAME}-${APP_VERSION}.msi"
ARCH_MSI="$DIST_DIR/${APP_NAME}-${APP_VERSION}-windows-x64.msi"
if [[ -f "$DEFAULT_MSI" ]]; then
  mv -f "$DEFAULT_MSI" "$ARCH_MSI"
fi
[[ -f "$ARCH_MSI" ]] || fail "MSI not found at expected path: $ARCH_MSI"

log "Done: $ARCH_MSI"
