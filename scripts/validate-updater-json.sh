#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_FILE="$ROOT_DIR/releases/latest/download/latest.json"
CHECK_URLS="false"
REQUIRE_SIGNATURE="false"

log() {
  printf '[validate-updater-json] %s\n' "$*" >&2
}

fail() {
  printf '[validate-updater-json] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/validate-updater-json.sh [--file <path>] [--check-urls] [--require-signature]

Options:
  --file <path>   Path to updater json (default: releases/latest/download/latest.json)
  --check-urls    Verify platform asset URLs are reachable (HTTP 2xx/3xx)
  --require-signature
                  Enforce non-empty platform signatures.
  -h, --help      Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)
      [[ $# -ge 2 ]] || fail "--file requires a value"
      TARGET_FILE="$2"
      shift 2
      ;;
    --check-urls)
      CHECK_URLS="true"
      shift
      ;;
    --require-signature)
      REQUIRE_SIGNATURE="true"
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

[[ -f "$TARGET_FILE" ]] || fail "File not found: $TARGET_FILE"
require_cmd node

tmp_urls="$(mktemp)"
cleanup() {
  rm -f "$tmp_urls"
}
trap cleanup EXIT

node - "$TARGET_FILE" "$tmp_urls" "$REQUIRE_SIGNATURE" <<'NODE'
const fs = require("fs");

const jsonPath = process.argv[2];
const urlsPath = process.argv[3];
const requireSignature = process.argv[4] === "true";
const text = fs.readFileSync(jsonPath, "utf8");

let root;
try {
  root = JSON.parse(text);
} catch (error) {
  console.error(`[validate-updater-json] ERROR: invalid JSON: ${error.message}`);
  process.exit(1);
}

const requiredPlatforms = ["darwin-aarch64", "darwin-x86_64", "windows-x86_64"];
const semver = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/;

function assert(condition, message) {
  if (!condition) {
    console.error(`[validate-updater-json] ERROR: ${message}`);
    process.exit(1);
  }
}

function nonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function notPlaceholder(value) {
  return !/(placeholder|replace_me|todo|tbd)/i.test(String(value));
}

assert(root && typeof root === "object", "root must be a JSON object");
assert(nonEmptyString(root.version), "version must be a non-empty string");
assert(semver.test(root.version.trim()), `version must be semver, got "${root.version}"`);
assert(nonEmptyString(root.pub_date), "pub_date must be a non-empty string");
assert(root.platforms && typeof root.platforms === "object", "platforms must be an object");

const urls = [];
for (const key of requiredPlatforms) {
  const platform = root.platforms[key];
  assert(platform && typeof platform === "object", `platforms.${key} must exist`);
  assert(nonEmptyString(platform.url), `platforms.${key}.url must be non-empty`);
  assert(notPlaceholder(platform.url), `platforms.${key}.url cannot be placeholder text`);
  assert(/^https?:\/\//i.test(platform.url.trim()), `platforms.${key}.url must start with http:// or https://`);
  if (requireSignature) {
    assert(nonEmptyString(platform.signature), `platforms.${key}.signature must be non-empty`);
    assert(notPlaceholder(platform.signature), `platforms.${key}.signature cannot be placeholder text`);
  }
  urls.push(platform.url.trim());
}

fs.writeFileSync(urlsPath, urls.join("\n") + "\n", "utf8");
console.error("[validate-updater-json] metadata validation passed");
NODE

if [[ "$CHECK_URLS" == "true" ]]; then
  require_cmd curl
  while IFS= read -r url; do
    [[ -n "$url" ]] || continue
    log "checking url: $url"
    curl --fail --silent --show-error --location --head --max-time 20 "$url" >/dev/null
  done < "$tmp_urls"
  log "url reachability check passed"
fi

log "done"
