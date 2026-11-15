#!/usr/bin/env node
// 웹 체험판 브라우저 점검 — 단위 테스트(node --test)로 못 보는 것: 자동 시연 끝까지, 접근성(axe), 성능.
//
// 준비(한 번): npm i --no-save playwright axe-core@4.10.2 && npx playwright install chromium
//   (Claude Code 클라우드 컨테이너에는 playwright·chromium이 이미 있다. axe-core만 받으면 된다)
// 실행(저장소 루트):
//   node tools/web-check.cjs demo   # 연습·평가 자동 시연이 복기/리포트까지 가는지, 브라우저 오류 0인지
//   node tools/web-check.cjs axe    # WCAG 2 A·AA, 라이트·다크 × 안전 확인·소개·온보딩 + 평가 리포트·장면 재현 창
//   node tools/web-check.cjs perf   # 상태별 메인 스레드 점유율·JS 힙(데스크톱 Chromium 기준 — 차량 기준과 직접 비교 불가)
// 결과는 콘솔에 찍힌다. 기록은 docs/VERIFICATION_LOG.md에 사람이 옮긴다.

const http = require('http'); const fs = require('fs'); const path = require('path');
const { chromium } = require('playwright');

const ROOT = path.join(__dirname, '..', 'web');
const PORT = 8765;
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };
const STORE = 'chiilkkabwa.web.v1';
const mode = process.argv[2] || 'demo';

const server = http.createServer((q, r) => {
  let p = path.join(ROOT, decodeURIComponent(q.url.split('?')[0]));
  if (!p.startsWith(ROOT)) { r.writeHead(403); return r.end(); }
  if (p.endsWith('/')) p += 'index.html';
  fs.readFile(p, (e, d) => { if (e) { r.writeHead(404); return r.end(); } r.writeHead(200, { 'content-type': TYPES[path.extname(p)] || 'application/octet-stream' }); r.end(d); });
});

const URL0 = `http://127.0.0.1:${PORT}/`;
async function open(pg, appMode, { demo = false, theme = null } = {}) {
  await pg.goto(URL0);
  await pg.evaluate(([k, m, t]) => {
    localStorage.clear();
    localStorage.setItem(k, JSON.stringify({ mode: m, introSeen: true, introV: 99, onboardingV: 99 }));
    if (t) localStorage.setItem('chiilkkabwa.theme', t);
  }, [STORE, appMode, theme]);
  await pg.goto(URL0 + (demo ? '?demo=1' : '')); await pg.waitForTimeout(1800); await pg.keyboard.press('Escape');
  if (theme && await pg.evaluate(() => document.documentElement.dataset.theme) !== theme) await pg.click('#themeBtn');
}
const until = async (pg, fn, ms = 180000) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (await fn()) return true; await pg.waitForTimeout(250); } return false; };

async function demo(pg) {
  await open(pg, 'practice', { demo: true }); await pg.selectOption('#demoSpeed', '3');
  console.log('연습 → 리플레이:', await until(pg, async () => /오늘 주행 돌아보기/.test(await pg.evaluate(() => document.body.innerText))) ? 'OK' : 'FAIL');
  await open(pg, 'eval', { demo: true }); await pg.selectOption('#demoSpeed', '3');
  console.log('평가 → 리포트 시간표:', await until(pg, async () => (await pg.locator('.report .tl').count()) > 0) ? 'OK' : 'FAIL');
}

async function axe(pg) {
  const src = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
  const run = async (label) => {
    await pg.addScriptTag({ content: src });
    const v = await pg.evaluate(async () => (await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa'] })).violations.map((x) => `${x.id}(${x.nodes.length}) ${x.nodes[0]?.target?.join(' ')}`));
    console.log(label.padEnd(24), v.length ? v.join(' | ') : 'OK');
  };
  for (const theme of ['light', 'dark']) {
    await open(pg, 'practice', { theme }); await run(`[${theme}] 안전 확인`);
    await pg.click('#aboutBtn'); await pg.waitForTimeout(400); await run(`[${theme}] 소개`); await pg.keyboard.press('Escape');
    await pg.click('#helpBtn'); await pg.waitForTimeout(400); await run(`[${theme}] 온보딩`); await pg.keyboard.press('Escape');
  }
  await open(pg, 'eval', { demo: true }); await pg.selectOption('#demoSpeed', '3');
  await until(pg, async () => (await pg.locator('.report .tl').count()) > 0); await pg.waitForTimeout(1500); await run('[eval] 리포트·시간표');
  if (await pg.locator('[data-scene]').count()) { await pg.locator('[data-scene]').first().click(); await pg.waitForTimeout(800); await run('[eval] 장면 재현 창'); }
}

async function perf(pg) {
  const cdp = await pg.context().newCDPSession(pg); await cdp.send('Performance.enable');
  const m = async () => Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((x) => [x.name, x.value]));
  const measure = async (label, ms = 8000) => {
    const a = await m(); await pg.waitForTimeout(ms); const b = await m();
    console.log(`${label.padEnd(24)} 메인 스레드 ${(((b.TaskDuration - a.TaskDuration) / (b.Timestamp - a.Timestamp)) * 100).toFixed(1)}%  JS 힙 ${(b.JSHeapUsedSize / 1048576).toFixed(1)}MB`);
  };
  const phase = () => pg.evaluate(() => document.body.dataset.phase);
  await open(pg, 'practice'); await measure('연습·정차');
  await open(pg, 'practice', { demo: true }); await until(pg, async () => (await phase()) === 'DRIVING_LOCK'); await pg.click('#demoBtn'); await measure('연습·주행 잠금');
  await open(pg, 'eval', { demo: true }); await until(pg, async () => (await phase()) === 'DRIVING_LOCK'); await pg.waitForTimeout(2000); await measure('평가·가상 주행', 6000);
}

(async () => {
  await new Promise((ok) => server.listen(PORT, ok));
  const browser = await chromium.launch();
  const pg = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const errors = []; pg.on('pageerror', (e) => errors.push(String(e))); pg.on('console', (x) => { if (x.type() === 'error') errors.push(x.text()); });
  try { await ({ demo, axe, perf }[mode] || demo)(pg); } finally {
    console.log('브라우저 오류:', errors.length ? errors : 0);
    await browser.close(); server.close();
  }
  process.exit(errors.length ? 1 : 0);
})();
