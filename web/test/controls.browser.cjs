// Optional A05 browser checks. Existing Playwright + Chrome; no production debug hooks.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const http = require('node:http');
const root = path.resolve(__dirname, '..');
const out = process.env.A05_EVIDENCE_DIR || path.resolve(root, '../build/a05-evidence');
const results = [];
const server = http.createServer(async (req, res) => {
  try {
    const file = path.resolve(root, '.' + (new URL(req.url, 'http://localhost').pathname === '/' ? '/index.html' : decodeURIComponent(new URL(req.url, 'http://localhost').pathname)));
    if (!file.startsWith(root + path.sep)) return res.writeHead(403).end();
    res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' })[path.extname(file)] || 'application/octet-stream');
    res.end(await fs.readFile(file));
  } catch { res.writeHead(404).end(); }
});
(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const introVersion = Number((await fs.readFile(path.join(root, 'app.js'), 'utf8')).match(/const INTRO_V = (\d+)/)[1]);
  await fs.mkdir(out, { recursive: true });
  try {
    for (const width of [1440, 390]) {
      const p = await browser.newPage({ viewport: { width, height: 900 } });
      const errors = [];
      p.on('pageerror', e => { errors.push(e.message); console.error('Browser pageerror:', e.message); });
      p.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
      await p.addInitScript(introVersion => localStorage.setItem('chiilkkabwa.web.v1', JSON.stringify({ introSeen: true, introV: introVersion, mode: 'coach', sound: false, voiceOn: false })), introVersion);
      await p.goto(`http://127.0.0.1:${server.address().port}/`, { waitUntil: 'networkidle' });
      await p.waitForFunction(() => document.body.dataset.phase === 'READY');
      if (width === 390) await p.locator('#consoleFab').click();
      const set = async (id, value) => p.locator(id).evaluate((el, value) => { el.value = String(value); el.dispatchEvent(new Event('input', { bubbles: true })); }, value);
      const val = async id => Number(await p.locator(id).inputValue());
      const waitVal = async (id, value) => p.waitForFunction(({ id, value }) => Math.abs(Number(document.querySelector(id).value) - value) < 1, { id, value });
      const clearFocus = async () => p.evaluate(() => document.activeElement?.blur());
      const hold = async (id, ms, cancel = false) => {
        await p.locator(id).scrollIntoViewIfNeeded(); const b = await p.locator(id).boundingBox();
        await p.mouse.move(b.x + b.width / 2, b.y + b.height / 2); await p.mouse.down(); await p.waitForTimeout(ms);
        const value = await val(id.includes('brake') ? '#c-brake' : '#c-accel');
        if (cancel) await p.locator(id).dispatchEvent('pointercancel', { pointerId: 1, pointerType: 'mouse' });
        await p.mouse.up(); return value;
      };
      assert.equal(await p.locator('#wasdHint').isVisible(), false);
      await p.locator('#controlHelp').focus(); await p.keyboard.press('Enter');
      await p.locator('#wasdHint').scrollIntoViewIfNeeded();
      await p.waitForTimeout(350); // mobile console transition must finish before visual evidence
      assert.equal(await p.locator('#wasdHint').isVisible(), true);
      assert.match(await p.locator('#wasdHint').innerText(), /모바일/);
      await p.screenshot({ path: path.join(out, `help-${width}.png`) });
      await p.locator('#wasdHintX').click(); assert.equal(await p.locator('#controlHelp').getAttribute('aria-expanded'), 'false');
      for (const gear of ['R', 'N', 'D', 'P']) {
        await p.locator(`#c-gear [data-v="${gear}"]`).click();
        assert.match(await p.locator('#c-gearState').innerText(), new RegExp(gear + ' ·'));
        assert.equal(await p.locator('#c-gear [aria-checked="true"]').getAttribute('data-v'), gear);
      }
      await set('#c-steer', 270); assert.equal(await p.locator('#c-wheel').getAttribute('aria-valuenow'), '270');
      await p.locator('#c-steerCenter').click();
      const wheel = p.locator('#c-wheel'); await wheel.scrollIntoViewIfNeeded(); const w = await wheel.boundingBox();
      await p.mouse.move(w.x + w.width / 2, w.y + 5); await p.mouse.down();
      await p.mouse.move(w.x + w.width - 5, w.y + w.height / 2, { steps: 6 }); await p.mouse.up();
      assert.ok(await val('#c-steer') > 45); assert.equal(await p.locator('#wasdHint').isVisible(), false);
      await set('#c-steer', -300); assert.equal(await wheel.getAttribute('aria-valuenow'), '-300'); await p.locator('#c-steerCenter').click();
      const light = await hold('#c-accelHold', 150); await p.waitForTimeout(400);
      const deep = await hold('#c-accelHold', 800); assert.ok(deep > light + 10); await p.waitForTimeout(400);
      await hold('#c-brakeHold', 400, true); assert.equal(await val('#c-brake'), 0);
      await hold('#c-brakeHold', 400); await set('#c-brake', 55); await p.waitForTimeout(200); assert.equal(await val('#c-brake'), 55);
      await set('#c-brake', 0); await set('#c-accel', 0);
      if (width === 390) await p.locator('#consoleFab').evaluate(el => el.click());
      await p.locator('#startBtn').click();
      if (width === 390 && !(await p.locator('#console').evaluate(el => el.classList.contains('open')))) await p.locator('#consoleFab').click();
      await p.locator('#c-pb').click(); await p.locator('#c-gear [data-v="D"]').click();
      await p.waitForFunction(() => document.body.dataset.phase === 'DRIVING_LOCK');
      await p.locator('#c-wheel').focus(); // W remains usable after pointer steering.
      await p.keyboard.down('w'); await p.waitForTimeout(750); assert.ok(await val('#c-accel') >= 50);
      await p.locator('#controlHelp').click(); await p.locator('#wasdHintX').click(); assert.ok(await val('#c-accel') >= 50, 'help close preserves held input');
      await p.keyboard.up('w'); await waitVal('#c-accel', 0);
      await clearFocus(); await p.keyboard.down('a'); await p.waitForTimeout(250); assert.equal(await val('#c-steer'), -180);
      await p.keyboard.down('d'); await waitVal('#c-steer', 0);
      await p.keyboard.up('a'); await waitVal('#c-steer', 180); await p.keyboard.up('d'); await waitVal('#c-steer', 0);
      await clearFocus(); await p.keyboard.down('ArrowUp'); await p.waitForTimeout(700); await set('#c-accel', 23);
      await p.waitForTimeout(200); assert.equal(await val('#c-accel'), 23); await p.keyboard.up('ArrowUp'); assert.equal(await val('#c-accel'), 23);
      await set('#c-accel', 0); await clearFocus(); await p.keyboard.down('ArrowLeft'); await p.waitForTimeout(250);
      await p.locator('#c-brake').focus(); await p.keyboard.up('ArrowLeft'); await waitVal('#c-steer', 0);
      await clearFocus(); await p.keyboard.down('w'); await p.waitForTimeout(200);
      await p.evaluate(() => dispatchEvent(new Event('blur'))); await waitVal('#c-accel', 0); await p.keyboard.up('w');
      if (width === 1440) {
        await set('#c-accel', 100); await p.waitForFunction(() => Number(document.querySelector('#v-speed').textContent) > 18);
        await p.locator('#stopBtn').click();
        await p.waitForFunction(() => document.querySelector('#stopBtn').dataset.k === 'DECELERATING');
        await p.waitForFunction(() => document.querySelector('#stopBtn').dataset.k === 'STOPPED');
        await p.locator('#stopBtn').click(); await p.waitForFunction(() => document.querySelector('#stopBtn').dataset.k === 'RELEASED');
      }
      await p.locator('#controlHelp').scrollIntoViewIfNeeded();
      const b = await p.locator('#c-gear').boundingBox();
      assert.ok(b.x >= 0 && b.x + b.width <= width + 1);
      assert.equal(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      assert.deepEqual(errors, []);
      results.push({ width, height: 900, controls: 'PASS', errors });
      console.log(`PASS A05 ${width}px controls`); await p.close();
    }
    const p = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const errors = []; p.on('pageerror', e => errors.push(e.message));
    p.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    await p.goto(`http://127.0.0.1:${server.address().port}/`, { waitUntil: 'networkidle' });
    await p.locator('[data-ob="next"]').waitFor();
    for (let i = 0; i < 4; i++) await p.locator('[data-ob="next"]').click();
    await p.locator('[data-ob="done"]').click();
    await p.locator('#coachMark').waitFor();
    await p.locator('.gnb-app[data-ico="road"]').click();
    assert.match(await p.locator('#gnbTip').innerText(), /데모/);
    assert.equal(await p.locator('#appBody').getAttribute('data-screen'), 'gate');
    assert.deepEqual(errors, []);
    results.push({ test: 'existing onboarding / coach mark / GNB', errors });
    await p.close();
    await fs.writeFile(path.join(out, 'controls-results.json'), JSON.stringify({ browser: browser.version(), results }, null, 2));
  } finally { await browser.close(); await new Promise(r => server.close(r)); }
})().catch(e => { console.error(e); server.close(); process.exitCode = 1; });
