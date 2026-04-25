#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_FILE="$ROOT_DIR/releases/latest/download/latest.json"
VALIDATE_SCRIPT="$ROOT_DIR/scripts/validate-updater-json.sh"

VERSION=""
NOTES=""
PUB_DATE=""
DARWIN_ARM64_URL=""
DARWIN_ARM64_SIG=""
DARWIN_X64_URL=""
DARWIN_X64_SIG=""
WINDOWS_X64_URL=""
WINDOWS_X64_SIG=""
CHECK_URLS="false"
SKIP_VALIDATE="false"

log() {
  printf '[update-latest-json] %s\n' "$*" >&2
}

fail() {
  printf '[update-latest-json] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

require_non_empty() {
  local name value
  name="$1"
  value="$2"
  [[ -n "${value//[[:space:]]/}" ]] || fail "Missing required argument: $name"
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/update-latest-json.sh \
    --version <x.y.z> \
    [--darwin-arm64-url <url> --darwin-arm64-signature <sig>] \
    [--darwin-x64-url <url> --darwin-x64-signature <sig>] \
    [--windows-x64-url <url> --windows-x64-signature <sig>] \
    [--notes <text>] [--pub-date <ISO8601>] [--file <path>] [--check-urls] [--skip-validate]

Options:
  --version <x.y.z>                  Release version.
  --notes <text>                     Optional release notes text.
  --pub-date <ISO8601>               Optional publish date; default is current UTC timestamp.
  --darwin-arm64-url <url>           Optional macOS arm64 asset URL.
  --darwin-arm64-signature <sig>     Optional macOS arm64 signature text.
  --darwin-x64-url <url>             Optional macOS x64 asset URL.
  --darwin-x64-signature <sig>       Optional macOS x64 signature text.
  --windows-x64-url <url>            Optional Windows x64 asset URL.
  --windows-x64-signature <sig>      Optional Windows x64 signature text.
  --file <path>                      Output JSON path (default: releases/latest/download/latest.json).
  --check-urls                       Also check URL reachability during validation.
  --skip-validate                    Skip validate-updater-json.sh after writing.
  -h, --help                         Show help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      [[ $# -ge 2 ]] || fail "--version requires a value"
      VERSION="$2"
      shift 2
      ;;
    --notes)
      [[ $# -ge 2 ]] || fail "--notes requires a value"
      NOTES="$2"
      shift 2
      ;;
    --pub-date)
      [[ $# -ge 2 ]] || fail "--pub-date requires a value"
      PUB_DATE="$2"
      shift 2
      ;;
    --darwin-arm64-url)
      [[ $# -ge 2 ]] || fail "--darwin-arm64-url requires a value"
      DARWIN_ARM64_URL="$2"
      shift 2
      ;;
    --darwin-arm64-signature)
      [[ $# -ge 2 ]] || fail "--darwin-arm64-signature requires a value"
      DARWIN_ARM64_SIG="$2"
      shift 2
      ;;
    --darwin-x64-url)
      [[ $# -ge 2 ]] || fail "--darwin-x64-url requires a value"
      DARWIN_X64_URL="$2"
      shift 2
      ;;
    --darwin-x64-signature)
      [[ $# -ge 2 ]] || fail "--darwin-x64-signature requires a value"
      DARWIN_X64_SIG="$2"
      shift 2
      ;;
    --windows-x64-url)
      [[ $# -ge 2 ]] || fail "--windows-x64-url requires a value"
      WINDOWS_X64_URL="$2"
      shift 2
      ;;
    --windows-x64-signature)
      [[ $# -ge 2 ]] || fail "--windows-x64-signature requires a value"
      WINDOWS_X64_SIG="$2"
      shift 2
      ;;
    --file)
      [[ $# -ge 2 ]] || fail "--file requires a value"
      TARGET_FILE="$2"
      shift 2
      ;;
    --check-urls)
      CHECK_URLS="true"
      shift
      ;;
    --skip-validate)
      SKIP_VALIDATE="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

require_non_empty "--version" "$VERSION"
if [[ -z "${PUB_DATE//[[:space:]]/}" ]]; then
  PUB_DATE="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
fi

require_cmd node

mkdir -p "$(dirname "$TARGET_FILE")"

node - "$TARGET_FILE" "$VERSION" "$NOTES" "$PUB_DATE" "$DARWIN_ARM64_URL" "$DARWIN_ARM64_SIG" "$DARWIN_X64_URL" "$DARWIN_X64_SIG" "$WINDOWS_X64_URL" "$WINDOWS_X64_SIG" <<'NODE'
const fs = require("fs");

const [
  targetFile,
  version,
  notes,
  pubDate,
  darwinArm64Url,
  darwinArm64Sig,
  darwinX64Url,
  darwinX64Sig,
  windowsX64Url,
  windowsX64Sig
] = process.argv.slice(2);

function nonEmpty(value) {
  return typeof value === "string" && value.trim().length > 0;
}

const platforms = {};
if (nonEmpty(darwinArm64Url)) {
  platforms["darwin-aarch64"] = {
    signature: (darwinArm64Sig || "").trim(),
    url: darwinArm64Url.trim()
  };
}
if (nonEmpty(darwinX64Url)) {
  platforms["darwin-x86_64"] = {
    signature: (darwinX64Sig || "").trim(),
    url: darwinX64Url.trim()
  };
}
if (nonEmpty(windowsX64Url)) {
  platforms["windows-x86_64"] = {
    signature: (windowsX64Sig || "").trim(),
    url: windowsX64Url.trim()
  };
}
if (Object.keys(platforms).length === 0) {
  console.error("[update-latest-json] ERROR: at least one platform URL must be provided");
  process.exit(1);
}

const payload = {
  version: version.trim(),
  notes: notes || "",
  pub_date: pubDate.trim(),
  platforms
};

fs.writeFileSync(targetFile, JSON.stringify(payload, null, 2) + "\n", "utf8");
NODE

log "written: $TARGET_FILE"

if [[ "$SKIP_VALIDATE" != "true" ]]; then
  [[ -x "$VALIDATE_SCRIPT" ]] || fail "validate script not found or not executable: $VALIDATE_SCRIPT"
  validate_args=(--file "$TARGET_FILE")
  if [[ "$CHECK_URLS" == "true" ]]; then
    validate_args+=(--check-urls)
  fi
  "$VALIDATE_SCRIPT" "${validate_args[@]}"
fi

log "done"
