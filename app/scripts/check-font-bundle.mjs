// Kiểm tra sau `npm run webapp:prod`: font Be Vietnam Pro (gói npm @fontsource/be-vietnam-pro) đã được Vite đóng gói
// và phục vụ từ chính máy chủ (CSP font-src 'self'): CSS có @font-face trỏ tới file .woff2 có thật trong thư mục build,
// không tham chiếu font ở máy chủ ngoài. Chạy tự động qua script npm "postwebapp:prod".
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const outDir = process.argv[2] ?? fileURLToPath(new URL('../target/classes/static/', import.meta.url));
const WEIGHTS = ['400', '500', '600', '700', '800'];

const walk = dir =>
  readdirSync(dir).flatMap(name => {
    const full = path.join(dir, name);
    return statSync(full).isDirectory() ? walk(full) : [full];
  });

const fail = message => {
  console.error(`check-font-bundle: ${message}`);
  process.exit(1);
};

if (!existsSync(outDir)) fail(`không thấy thư mục build ${outDir}`);

const files = walk(outDir);
const cssFiles = files.filter(f => f.endsWith('.css') && !f.includes(`${path.sep}swagger-ui${path.sep}`));
const faces = [];
for (const css of cssFiles) {
  const text = readFileSync(css, 'utf8');
  for (const block of text.match(/@font-face\s*{[^}]*}/g) ?? []) {
    if (/font-family:\s*["']?Be Vietnam Pro/.test(block)) faces.push({ css, block });
  }
}
if (faces.length === 0) fail('không có @font-face "Be Vietnam Pro" trong CSS đã build');

for (const weight of WEIGHTS) {
  if (!faces.some(f => new RegExp(`font-weight:\\s*${weight}\\b`).test(f.block))) fail(`thiếu độ đậm ${weight}`);
}
if (!faces.some(f => /unicode-range:[^;]*U\+1EA0-1EF9/i.test(f.block))) fail('thiếu bộ chữ tiếng Việt (U+1EA0-1EF9)');

let checked = 0;
for (const { css, block } of faces) {
  for (const [, raw] of block.matchAll(/url\(\s*["']?([^"')]+)["']?\s*\)/g)) {
    if (/^(https?:)?\/\//.test(raw)) fail(`font tải từ máy chủ ngoài: ${raw}`);
    if (raw.startsWith('data:')) continue;
    const clean = raw.split(/[?#]/)[0];
    const target = clean.startsWith('/') ? path.join(outDir, clean) : path.resolve(path.dirname(css), clean);
    if (!existsSync(target)) fail(`${path.relative(outDir, css)} tham chiếu ${raw} nhưng không có file trong build`);
    checked++;
  }
}
const woff2 = files.filter(f => /be-vietnam-pro.*\.woff2$/.test(path.basename(f)));
if (woff2.length === 0) fail('không có file be-vietnam-pro*.woff2 trong build');

console.log(`check-font-bundle: OK — ${faces.length} @font-face, ${checked} url() hợp lệ, ${woff2.length} file woff2`);
