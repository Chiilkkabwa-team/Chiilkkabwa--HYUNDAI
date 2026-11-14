// Optional browser regression: NODE_PATH must resolve an installed Playwright.
// node web/test/junction.browser.cjs (Chrome; no app state or production hooks exposed)
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..');
const evidence = process.env.A04_EVIDENCE_DIR;
const results = [];
const server = http.createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    const file = path.resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname));
    if (!file.startsWith(root + path.sep)) { res.writeHead(403).end(); return; }
    const mime = { '.js': 'text/javascript', '.html': 'text/html', '.css': 'text/css', '.svg': 'image/svg+xml' };
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
    res.end(await fs.readFile(file));
  } catch { res.writeHead(404).end(); }
});

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const introVersion = Number((await fs.readFile(path.join(root, 'app.js'), 'utf8')).match(/const INTRO_V = (\d+)/)[1]);
  try {
    if (evidence) await fs.mkdir(evidence, { recursive: true });
    async function pageFor(mode, width) {
      const page = await browser.newPage({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
      const errors = [], externalFailures = [];
      page.on('pageerror', e => errors.push(e.message));
      page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
      page.on('requestfailed', req => externalFailures.push({ url: req.url(), error: req.failure()?.errorText }));
      await page.addInitScript(({ mode, introVersion }) => localStorage.setItem('chiilkkabwa.web.v1', JSON.stringify({
        introSeen: true, introV: introVersion, mode, sound: false, voiceOn: false, wasdHintSeen: true,
      })), { mode, introVersion });
      await page.goto(url, { waitUntil: 'networkidle' });
      await page.waitForFunction(() => document.body.dataset.phase === 'READY');
      return { page, errors, externalFailures };
    }

    for (const width of [1440, 390]) {
      const { page, errors, externalFailures } = await pageFor('eval', width);
      if (width === 390) await page.locator('#consoleFab').click();
      const group = page.locator('#c-maneuver');
      await group.scrollIntoViewIfNeeded();
      for (const dir of ['LEFT', 'RIGHT', 'STRAIGHT']) {
        await group.locator(`[data-v="${dir}"]`).click();
        assert.equal(await group.locator('[aria-checked="true"]').getAttribute('data-v'), dir);
        assert.equal(await page.locator('#c-turn [aria-checked="true"]').getAttribute('data-v'), 'NONE');
      }
      await page.locator('#c-turn [data-v="LEFT"]').click();
      assert.equal(await group.locator('[aria-checked="true"]').getAttribute('data-v'), 'STRAIGHT');
      await group.scrollIntoViewIfNeeded();
      const bounds = await group.boundingBox();
      assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width + 1, 'maneuver buttons fit viewport');
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      assert.match(await page.locator('#simGroup').innerText(), /2D Mock/);
      if (evidence) await page.screenshot({ path: path.join(evidence, `controls-${width}.png`) });
      results.push({ test: `controls-${width}`, errors, externalFailures, bounds });
      assert.deepEqual(errors, [], 'browser console and page errors');
      await page.close();
    }

    const modes = (process.env.A04_DEMO_MODES || 'eval,coach,practice').split(',').filter(Boolean);
    for (const mode of modes) {
      const { page, errors, externalFailures } = await pageFor(mode, 1440);
      await page.locator('#demoSpeed').selectOption('3');
      await page.locator('#demoBtn').click();
      console.log(`Started existing ${mode} automatic demo`);
      try {
        await page.waitForFunction((mode) => {
          const saved = JSON.parse(localStorage.getItem('chiilkkabwa.web.v1') || '{}');
          return mode === 'practice' ? saved.reviewsSaved > 0 : saved.simReports?.length > 0;
        }, mode, { timeout: 180000 });
        if (mode !== 'practice') {
          assert.ok(await page.locator('.ev-list').innerText().then(t => t.includes('교차로 회전 잘함')),
            'automatic demo reaches and records a good virtual turn');
          assert.equal(await page.locator('#c-maneuver [aria-checked="true"]').getAttribute('data-v'), 'STRAIGHT');
        }
        if (evidence) await page.screenshot({ path: path.join(evidence, `demo-${mode}.png`) });
        results.push({ test: `demo-${mode}`, screen: await page.locator('#appBody').getAttribute('data-screen'), errors, externalFailures });
        assert.deepEqual(errors, [], 'browser console and page errors');
        console.log(`PASS existing ${mode} automatic demo`);
      } catch (error) {
        const state = await page.evaluate(() => ({ screen: document.querySelector('#appBody')?.dataset.screen,
          banner: document.querySelector('#demoBanner')?.innerText, body: document.querySelector('#appBody')?.innerText }));
        console.error(JSON.stringify({ mode, state, errors, externalFailures }));
        if (evidence) await page.screenshot({ path: path.join(evidence, `failed-${mode}.png`) });
        throw error;
      } finally { await page.close(); }
    }
    console.log(JSON.stringify({ browser: browser.version(), results }, null, 2));
    if (evidence) await fs.writeFile(path.join(evidence, 'browser-results.json'), JSON.stringify({ browser: browser.version(), results }, null, 2));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
