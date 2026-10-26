// Opens every MillionOS screen on phones, tablets, PC and Mac sizes, in French and English,
// and fails when anything runs off the screen, is cut off, or is too small to tap.
// Usage: node scripts/check-screens.mjs [site dir] [screenshot dir]   (needs Playwright)
import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { join, extname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkZoomRotationSettings, checkRotationAndPinch } from './zoom-rotation.mjs';

const dir = resolve(process.argv[2] || '.');
const shots = process.argv[3];
const sizes = {
  'small-phone': [320, 568], phone: [390, 844], 'phone-landscape': [844, 390],
  tablet: [820, 1180], 'tablet-landscape': [1180, 820], pc: [1440, 900],
};
const types = { '.html': 'text/html', '.ico': 'image/x-icon', '.js': 'text/javascript', '.mjs': 'text/javascript', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };
const server = createServer(async (req, res) => {
  const path = join(dir, decodeURIComponent(req.url.split('?')[0]).replace(/\/$/, '/index.html'));
  try { res.writeHead(200, { 'Content-Type': types[extname(path)] || 'application/octet-stream' }); res.end(await readFile(path)); }
  catch { res.writeHead(404); res.end(); }
}).listen(0);
const base = `http://127.0.0.1:${server.address().port}/`;
if (shots) await mkdir(shots, { recursive: true });

const audit = () => {
  const out = [];
  const W = document.documentElement.clientWidth;
  if (document.documentElement.scrollWidth > W + 1) out.push(`page scrolls sideways (${document.documentElement.scrollWidth} > ${W})`);
  const scrollsX = el => { for (let p = el.parentElement; p; p = p.parentElement) { const o = getComputedStyle(p).overflowX; if ((o === 'auto' || o === 'scroll') && p !== document.documentElement && p !== document.body) return true; } return false; };
  for (const el of document.querySelectorAll('button, a.btn, input, select, textarea, h1, h2, h3, img, table, iframe, video')) {
    const r = el.getBoundingClientRect();
    const cs = getComputedStyle(el);
    if (!r.width || !r.height || cs.visibility === 'hidden' || el.closest('[hidden],[aria-hidden="true"],dialog:not([open])')) continue;
    if (scrollsX(el)) continue; // inside a sideways-scrolling strip (tabs, wide tables): reachable
    const label = (el.getAttribute('aria-label') || el.textContent || el.getAttribute('alt') || el.tagName).trim().replace(/\s+/g, ' ').slice(0, 50);
    if (r.left < -1 || r.right > W + 1) out.push(`${el.tagName.toLowerCase()} "${label}" runs off the side (${Math.round(r.left)}..${Math.round(r.right)} of ${W})`);
    if (el.matches('button, a.btn') && !el.disabled && (r.height < 30 || r.width < 30)) out.push(`button "${label}" is too small to tap (${Math.round(r.width)}x${Math.round(r.height)})`);
  }
  return [...new Set(out)];
};


// Each step opens one screen the way a player gets there.
const steps = [
  ['enlist', () => { show('scrHome'); setTab('career'); }],
  ['training', () => setTab('cats')],
  ['honour roll', () => setTab('honour')],
  ['settings', () => { $('btnSettings').click(); }],
  ['music', () => { $('ovSettings').hidden = true; renderStyles(); $('ovMusic').hidden = false; }],
  ['career', () => { $('ovMusic').hidden = true; setTab('career'); $('btnEnlist').click(); }],
  ['question', () => { startLadder(); }],
  ['answer picked', () => { document.querySelector('#answers button').click(); }],
  ['ladder', () => { $('drawer').hidden = false; }],
  ['game over', () => { $('drawer').hidden = true; finish('walk'); }],
  ['promotion board', () => { show('scrHome'); showPromo(1); }],
  ['practice', () => { $('ovPromo').hidden = true; show('scrHome'); setTab('career'); document.querySelector('#playCats button').click(); }],
  ['ask claude', () => { show('scrHome'); openCopilot(); }],
];

const browser = await chromium.launch();
const failures = [];
let pages = 0;
for (const [label, [width, height]] of Object.entries(sizes)) {
  for (const lang of ['fr', 'en']) {
    const touch = width < 1000;
    const ctx = await browser.newContext({ viewport: { width, height }, locale: lang === 'fr' ? 'fr-CA' : 'en-CA', isMobile: touch, hasTouch: touch, serviceWorkers: 'block' });
    const page = await ctx.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(base, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(600);
    await page.evaluate(l => { ST.lang = l; save(); applyLang(); }, lang);
    for (const [name, step] of steps) {
      const ok = await page.evaluate(`(() => { try { (${step.toString()})(); return true; } catch (e) { return String(e); } })()`);
      if (ok !== true) { failures.push(`${label} ${lang} ${name}: screen did not open: ${ok}`); continue; }
      await page.waitForTimeout(250);
      pages++;
      for (const i of await page.evaluate(audit)) failures.push(`${label} ${width}x${height} ${lang} ${name}: ${i}`);
      if (shots && (label === 'small-phone' || label === 'phone-landscape' || label === 'pc')) await page.screenshot({ path: `${shots}/${label}-${lang}-${name.replace(/ /g, '-')}.png` });
    }
    for (const e of errors) failures.push(`${label} ${lang}: script error: ${e}`);
    await ctx.close();
  }
}
// Zoom and rotation, required in every app: nothing blocks pinch zoom or locks the orientation,
// and a phone and a tablet turned sideways and back re-fit every time.
failures.push(...checkZoomRotationSettings(fileURLToPath(new URL('..', import.meta.url))));
const turned = await checkRotationAndPinch(browser, base, { prepare: async page => { await page.evaluate(() => { ST.lang = 'fr'; save(); applyLang(); show('scrHome'); setTab('career'); $('btnEnlist').click(); startLadder(); }); await page.waitForTimeout(300); } });
failures.push(...turned.problems);
await browser.close();
server.close();
if (failures.length) { console.error([...new Set(failures)].join('\n')); console.error(`\n${failures.length} problems on ${pages} screens.`); process.exit(1); }
console.log(`All screens fit: ${pages} screens (${Object.keys(sizes).length} sizes x 2 languages), and pages re-fit when a phone or tablet turns (${turned.turns} turns) and pinch zooms.`);
