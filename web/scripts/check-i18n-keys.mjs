import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const ROOT = process.cwd();
const LOCALES_DIR = path.join(ROOT, "src", "locales");
const zhPath = path.join(LOCALES_DIR, "zh-CN.ts");
const enPath = path.join(LOCALES_DIR, "en-US.ts");

function readLocaleObject(filePath) {
  const source = fs.readFileSync(filePath, "utf8");
  const match = source.match(/const\s+\w+\s*=\s*(\{[\s\S]*\})\s*as const;?/);
  if (!match) {
    throw new Error(`Cannot parse locale file: ${filePath}`);
  }
  return vm.runInNewContext(`(${match[1]})`, {});
}

function flattenKeys(input, prefix = "") {
  const keys = new Set();
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    return keys;
  }
  for (const [key, value] of Object.entries(input)) {
    const next = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === "object" && !Array.isArray(value)) {
      const nested = flattenKeys(value, next);
      if (nested.size === 0) {
        keys.add(next);
      } else {
        for (const item of nested) keys.add(item);
      }
      continue;
    }
    keys.add(next);
  }
  return keys;
}

function sortedDiff(left, right) {
  return [...left].filter((item) => !right.has(item)).sort();
}

function run() {
  const zh = readLocaleObject(zhPath);
  const en = readLocaleObject(enPath);

  const zhKeys = flattenKeys(zh);
  const enKeys = flattenKeys(en);

  const missingInEn = sortedDiff(zhKeys, enKeys);
  const missingInZh = sortedDiff(enKeys, zhKeys);

  if (!missingInEn.length && !missingInZh.length) {
    console.log("[i18n] locale keys are aligned (zh-CN <-> en-US)");
    return;
  }

  console.error("[i18n] locale key mismatch detected");
  if (missingInEn.length) {
    console.error("\nMissing in en-US:");
    missingInEn.forEach((key) => console.error(`  - ${key}`));
  }
  if (missingInZh.length) {
    console.error("\nMissing in zh-CN:");
    missingInZh.forEach((key) => console.error(`  - ${key}`));
  }
  process.exit(1);
}

run();
