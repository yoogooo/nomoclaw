import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();
const TARGET_DIRS = ['src/components', 'src/pages'];
const INCLUDE_EXT = new Set(['.vue', '.ts', '.css']);
const EXCLUDE_FILES = new Set([
  path.normalize('src/pages/DesignSystemPage.vue')
]);

const rules = [
  {
    key: 'hardcoded-color',
    regex: /#[0-9a-fA-F]{3,8}\b|rgba?\([^\)]+\)|hsla?\([^\)]+\)/g,
    message: 'hardcoded color'
  },
  {
    key: 'hardcoded-px',
    regex: /\b\d+(?:\.\d+)?px\b/g,
    message: 'hardcoded px size'
  }
];

function walk(dir, out = []) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const abs = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(abs, out);
      continue;
    }
    const ext = path.extname(entry.name);
    if (INCLUDE_EXT.has(ext)) out.push(abs);
  }
  return out;
}

function lineAndColumn(content, index) {
  const prior = content.slice(0, index);
  const lines = prior.split('\n');
  const line = lines.length;
  const col = lines[lines.length - 1].length + 1;
  return { line, col };
}

const files = TARGET_DIRS
  .map((p) => path.join(ROOT, p))
  .filter((p) => fs.existsSync(p))
  .flatMap((p) => walk(p))
  .filter((abs) => !EXCLUDE_FILES.has(path.normalize(path.relative(ROOT, abs))));

const violations = [];
for (const abs of files) {
  const rel = path.normalize(path.relative(ROOT, abs));
  const content = fs.readFileSync(abs, 'utf8');
  for (const rule of rules) {
    rule.regex.lastIndex = 0;
    for (const match of content.matchAll(rule.regex)) {
      const idx = match.index ?? 0;
      const { line, col } = lineAndColumn(content, idx);
      const snippet = match[0].replace(/\s+/g, ' ').slice(0, 120);
      violations.push({ file: rel, line, col, rule: rule.key, snippet });
    }
  }
}

violations.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line || a.col - b.col);

if (!violations.length) {
  console.log('PASS: no hardcoded design values found in business files.');
  process.exit(0);
}

const byFile = new Map();
for (const v of violations) {
  byFile.set(v.file, (byFile.get(v.file) || 0) + 1);
}

console.log(`FAIL: found ${violations.length} hardcoded design values in ${byFile.size} files.\n`);
console.log('Top files:');
const top = [...byFile.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12);
for (const [file, count] of top) {
  console.log(`- ${file}: ${count}`);
}

console.log('\nFirst 120 violations:');
for (const v of violations.slice(0, 120)) {
  console.log(`${v.file}:${v.line}:${v.col} [${v.rule}] ${v.snippet}`);
}

process.exit(1);
