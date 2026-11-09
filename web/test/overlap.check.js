// 차량 디스플레이 영역에서 서로 겹치는 UI 요소를 찾아낸다.
//
// 2026-11-09에 설명·HUD·표시등·계기가 1024~1920px에서 19건 겹쳐 있었다.
// 숫자(top/bottom)로 맞추던 레이아웃을 일반 흐름(flex)으로 바꿔 고쳤고,
// 다시 그렇게 되지 않도록 이 검사기를 남긴다.
//
// 쓰는 법:
//   cd web && python3 -m http.server 8765 &
//   node web/test/overlap.check.js        # 겹침이 있으면 0이 아닌 수를 출력
//
// 드라이빙 뷰에 요소를 새로 넣으면 아래 SEL에 선택자를 추가한다.
const { chromium } = require('playwright');

const SEL = [
  '.dv-caption', '.sim-hud .hud-top', '.sim-hud .hud-stop', '.sim-hud .hud-ev',
  '.dv-tell', '.dv-cluster', '.pleos-status', '.gnb',
];

const check = async (page, label) => {
  const boxes = await page.evaluate((sels) => {
    const out = [];
    for (const s of sels) {
      for (const el of document.querySelectorAll(s)) {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || +cs.opacity === 0) continue;
        const r = el.getBoundingClientRect();
        if (r.width < 2 || r.height < 2) continue;
        out.push({ sel: s, x: r.x, y: r.y, w: r.width, h: r.height, text: (el.innerText || '').replace(/\s+/g, ' ').slice(0, 46) });
      }
    }
    return out;
  }, SEL);

  const hits = [];
  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      const a = boxes[i], b = boxes[j];
      const ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
      const oy = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
      if (ox > 2 && oy > 2) hits.push({ a: a.sel, b: b.sel, ox: Math.round(ox), oy: Math.round(oy), at: a.text, bt: b.text });
    }
  }
  if (hits.length) {
    console.log(`\n✗ ${label} — 겹침 ${hits.length}건`);
    hits.forEach((h) => console.log(`   ${h.a}  ×  ${h.b}   (${h.ox}×${h.oy}px)\n      "${h.at}"\n      "${h.bt}"`));
  } else {
    console.log(`✓ ${label}`);
  }
  return hits.length;
};

(async () => {
  const b = await chromium.launch();
  let total = 0;
  for (const [W, MODE] of [[1920,'coach'],[1440,'coach'],[1280,'coach'],[1024,'coach'],[768,'coach'],[390,'coach'],
                           [1920,'eval'],[1440,'eval'],[1024,'eval'],
                           [1920,'practice'],[1440,'practice'],[1280,'practice'],[1024,'practice'],[768,'practice'],[390,'practice']]) {
    const p = await b.newPage({ viewport: { width: W, height: 1000 } });
    await p.goto('http://localhost:8765/');
    await p.evaluate((m) => localStorage.setItem('chiilkkabwa.web.v1', JSON.stringify({ introSeen: true, mode: m })), MODE);
    await p.reload(); await p.waitForTimeout(900);

    total += await check(p, `${W}px ${MODE} · 정차`);

    // 주행 상태로 들어가서 HUD가 다 켜진 상태를 본다
    await p.locator('#startBtn').click({ timeout: 8000 }).catch(() => {});
    await p.waitForTimeout(400);
    await p.locator('#simGoBtn, #setupBtn, .btn.primary').first().click({ timeout: 6000 }).catch(() => {});
    await p.waitForTimeout(300);
    await p.locator('#c-pb').click({ timeout: 4000 }).catch(() => {});
    await p.locator('#c-gear button[data-v="D"]').click({ timeout: 4000 }).catch(() => {});
    await p.waitForTimeout(1200);
    // 강습자 정지 요청까지 켜서 HUD를 최대로
    await p.evaluate(() => window.__sim?.requestStop?.(true));
    await p.keyboard.press('Space').catch(() => {});
    await p.waitForTimeout(900);
    const scr = await p.evaluate(() => document.querySelector('#appBody').dataset.screen);
    total += await check(p, `${W}px ${MODE} · 주행(${scr})`);
    await p.close();
  }
  console.log(`\n합계 겹침: ${total}건`);
  await b.close();
  if (total) process.exitCode = 1;
})();
