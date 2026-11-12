// 치일까봐 — 주차 후 리플레이: 속도 시간표 그래프(C01) + 장면 재현 작은 창(C03, 11/12 회의)
//
// 지키는 것(docs/SAFETY_RULES.md):
//   - S04: 이 화면은 **주차 후(복기 단계)에만** 열린다. 주행에 들어가면 작은 창을 바로 닫는다(app.js onPhaseChange).
//   - S07·S10: 실제 녹화 영상이 아니다. 주행 중 기록한 **숫자(속도·페달·핸들·가상 차 위치)로 다시 그린 그림**이고,
//     창 제목과 캡션에 '시뮬레이션 재현'을 늘 붙인다.
//   - S09: 기록은 이번 주행 동안 메모리에만 둔다. 브라우저 저장소에 남기지 않는다(목록 요약만 저장된다).
//   - S11: 그림은 캔버스 기본 도형만 쓴다(외부 영상·이미지·라이브러리 없음).

import * as THEME from './theme.js';

const T = THEME.c;
const TA = THEME.alpha;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const fmtT = (sec) => `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`;

export const TRACK_HZ = 10;      // 초당 10장 — 그래프·재현에 충분하고 메모리는 작다
export const TRACK_MAX = 6000;   // 10분. 넘으면 한 장 건너 하나씩 줄인다(오래 달려도 메모리 일정)
export const PIP_BEFORE = 3;     // 사건 3초 전부터
export const PIP_AFTER = 2;      // 2초 뒤까지
export const PIP_RATE = 0.5;     // 슬로모션 0.5배

/** 주행 1회의 시간표. 화면은 건드리지 않고 숫자만 모은다. */
export class Track {
  constructor() { this.frames = []; this.lastT = -Infinity; }

  /** @param {{t:number,kmh:number,brake?:number,accel?:number,steer?:number,sig?:string,x?:number,s?:number,npcs?:Array}} f */
  push(f) {
    if (f.t - this.lastT < 1 / TRACK_HZ - 1e-6) return false;
    this.lastT = f.t;
    this.frames.push(f);
    if (this.frames.length > TRACK_MAX) this.frames = this.frames.filter((_, i) => i % 2 === 0);
    return true;
  }

  get duration() { return this.frames.length ? this.frames[this.frames.length - 1].t - this.frames[0].t : 0; }

  /** 사건 앞뒤 구간 */
  around(t, before = PIP_BEFORE, after = PIP_AFTER) {
    return this.frames.filter((f) => f.t >= t - before && f.t <= t + after);
  }

  /** 이 시각 장면을 다시 그릴 만큼 기록이 있는가 */
  hasScene(t) { return this.around(t).length >= 5; }
}

/** 두 장면 사이를 이어 붙인다(10장/초를 슬로모션으로 보여 줘도 끊겨 보이지 않게) */
export function frameAt(frames, t) {
  if (!frames.length) return null;
  if (t <= frames[0].t) return frames[0];
  const last = frames[frames.length - 1];
  if (t >= last.t) return last;
  let i = 1; while (frames[i].t < t) i++;
  const a = frames[i - 1], b = frames[i]; const k = (t - a.t) / (b.t - a.t || 1);
  const L = (p, q) => (p == null || q == null ? p ?? q : p + (q - p) * k);
  const npcs = a.npcs ? a.npcs.map((n) => {
    const m = b.npcs?.find((x) => x[0] === n[0]);
    return m ? [n[0], L(n[1], m[1]), L(n[2], m[2]), n[3]] : n;
  }) : null;
  return { t, kmh: L(a.kmh, b.kmh), brake: L(a.brake, b.brake), accel: L(a.accel, b.accel), steer: L(a.steer, b.steer), sig: k < 0.5 ? a.sig : b.sig, x: L(a.x, b.x), s: L(a.s, b.s), npcs };
}

/**
 * 속도 시간표 그래프(SVG 문자열). 사건은 점으로 찍고, 누르면 그 장면을 다시 본다.
 * 점수·등급 없음 — 언제 속도가 어떻게 변했고 무슨 일이 있었는지만.
 * @param {Array} frames Track.frames
 * @param {Array} rows 리플레이 항목 {atSec,title,kind,spoken}
 */
export function timelineSvg(frames, rows, { w = 640, h = 168 } = {}) {
  if (frames.length < 2) return '';
  const pad = { l: 34, r: 12, t: 14, b: 26 };
  const t0 = frames[0].t, t1 = frames[frames.length - 1].t; const span = Math.max(1, t1 - t0);
  const vmax = Math.max(30, Math.ceil(Math.max(...frames.map((f) => f.kmh || 0)) / 10) * 10);
  const X = (t) => pad.l + ((t - t0) / span) * (w - pad.l - pad.r);
  const Y = (v) => h - pad.b - (v / vmax) * (h - pad.t - pad.b);
  const step = Math.max(1, Math.floor(frames.length / 400)); // 점 400개면 충분
  const pts = frames.filter((_, i) => i % step === 0).map((f) => `${X(f.t).toFixed(1)},${Y(f.kmh || 0).toFixed(1)}`).join(' ');
  // 브레이크를 실제로 밟은 구간(30% 넘게)은 아래에 띠로
  const bands = []; let open = null;
  for (const f of frames) {
    const on = (f.brake || 0) >= 30;
    if (on && open == null) open = f.t;
    if (!on && open != null) { bands.push([open, f.t]); open = null; }
  }
  if (open != null) bands.push([open, t1]);
  const grid = [0, vmax / 2, vmax].map((v) => `<line x1="${pad.l}" x2="${w - pad.r}" y1="${Y(v)}" y2="${Y(v)}" class="tl-grid"/><text x="${pad.l - 6}" y="${Y(v) + 4}" class="tl-ax" text-anchor="end">${v}</text>`).join('');
  const marks = rows.map((r, i) => ({ r, i })).filter(({ r }) => r.atSec >= t0 - 0.5 && r.atSec <= t1 + 0.5).map(({ r, i }) => {
    const f = frameAt(frames, r.atSec);
    const cx = X(clamp(r.atSec, t0, t1)), cy = Y(f?.kmh || 0);
    return `<g class="tl-mk ${r.kind}" data-i="${i}" role="button" tabindex="0" aria-label="${esc(`${fmtT(r.atSec)} ${r.title} 장면 다시 보기`)}">
      <rect x="${cx - 12}" y="${pad.t}" width="24" height="${h - pad.t - pad.b}" class="tl-hit"/>
      <line x1="${cx}" x2="${cx}" y1="${pad.t}" y2="${h - pad.b}" class="tl-tick"/>
      <circle cx="${cx}" cy="${cy}" r="7"/>${r.spoken ? `<circle cx="${cx}" cy="${cy}" r="11" class="tl-spoke"/>` : ''}
      <title>${esc(`${fmtT(r.atSec)} · ${r.title}${r.spoken ? ' · 말했어요' : ''}`)}</title></g>`;
  }).join('');
  return `<svg class="tl-svg" viewBox="0 0 ${w} ${h}" role="group" aria-label="속도 시간표: 가로는 시간, 세로는 속도(km/h). 점을 누르면 그 장면을 다시 봐요.">
    ${grid}
    ${bands.map(([a, b]) => `<rect x="${X(a)}" y="${h - pad.b - 6}" width="${Math.max(2, X(b) - X(a))}" height="6" class="tl-brake"/>`).join('')}
    <polyline points="${pts}" class="tl-line"/>
    <text x="${pad.l}" y="${h - 6}" class="tl-ax">${fmtT(t0)}</text><text x="${w - pad.r}" y="${h - 6}" class="tl-ax" text-anchor="end">${fmtT(t1)}</text>
    <text x="${pad.l + 4}" y="${pad.t - 2}" class="tl-ax">km/h</text>
    ${marks}
  </svg>`;
}

export function timelineLegend(hasSim) {
  return `<p class="tl-legend"><span><i class="lg-line"></i>속도</span><span><i class="lg-brake"></i>브레이크 30% 넘게</span><span><i class="lg-dot note"></i>기록</span><span><i class="lg-dot good"></i>잘함</span><span><i class="lg-ring"></i>음성으로 말함</span>${hasSim ? '' : '<span>점을 누르면 계기 장면 재현</span>'}</p>`;
}

// ───────────────────────── 장면 재현 작은 창(PIP) ─────────────────────────

let current = null;

/** 열려 있는 재현 창을 닫는다(주행에 들어갈 때 app.js가 부른다) */
export function closePip() {
  if (!current) return;
  cancelAnimationFrame(current.raf);
  current.el.remove();
  current.opener?.focus?.();
  current = null;
}

/**
 * 사건 하나를 슬로모션으로 다시 그린다.
 * @param {HTMLElement} host 붙일 곳(복기 화면)
 * @param {{frames:Array,row:object,kind:'sim'|'signals',reduced:boolean,opener?:HTMLElement,sourceLabel:string}} o
 */
export function openPip(host, o) {
  closePip();
  const seg = o.frames;
  if (seg.length < 2) return null;
  const el = document.createElement('div');
  el.className = 'pip'; el.setAttribute('role', 'dialog'); el.setAttribute('aria-label', `${o.row.title} 장면 재현`);
  el.innerHTML = `<div class="pip-head"><b>${esc(o.row.title)}</b><span class="pip-tag">시뮬레이션 재현 · ${esc(o.sourceLabel)}</span>
      <button type="button" class="pip-x" aria-label="닫기">✕</button></div>
    <canvas class="pip-cv" width="640" height="380" aria-hidden="true"></canvas>
    <div class="pip-foot"><span class="pip-time"></span>
      <button type="button" class="mini pip-again">다시 보기</button></div>
    <p class="pip-cap">${o.kind === 'sim' ? '주행 중 기록한 가상 차 위치·속도로 다시 그린 그림이에요. 실제 촬영 영상이 아니에요.' : '주행 중 읽은 속도·페달·핸들 값으로 다시 그린 계기예요. 실제 촬영 영상이 아니에요.'}</p>`;
  host.appendChild(el);
  // 앱 화면(오른쪽 아래 모서리)에 띄운다 — 목록을 내려도 따라 보이게 fixed
  const box = (document.getElementById('appBody') || host).getBoundingClientRect();
  const w = el.offsetWidth, h = el.offsetHeight;
  el.style.left = `${Math.max(16, Math.min(innerWidth - w - 16, box.right - w - 16))}px`;
  el.style.top = `${Math.max(16, Math.min(innerHeight - h - 16, box.bottom - h - 16))}px`;
  const cv = el.querySelector('canvas'); const ctx = cv.getContext('2d');
  const t0 = seg[0].t, t1 = seg[seg.length - 1].t, tEv = o.row.atSec;
  const state = { el, raf: 0, opener: o.opener, play: 0, start: performance.now() };
  current = state;
  const timeEl = el.querySelector('.pip-time');

  const paint = (t) => {
    const f = frameAt(seg, t);
    if (o.kind === 'sim') drawRoadScene(ctx, cv, f, t, tEv);
    else drawGauges(ctx, cv, f, t, tEv);
    timeEl.textContent = `${fmtT(t)} ${t < tEv - 0.05 ? '· 사건 전' : t > tEv + 0.6 ? '· 사건 뒤' : '· 이 순간'}${o.reduced ? '' : ` · ${PIP_RATE}배 느리게`}`;
  };
  // 움직임 줄이기 설정이면 사건 순간 한 장만 보여 준다
  if (o.reduced) { paint(tEv); el.querySelector('.pip-again').hidden = true; }
  else {
    const loop = (now) => {
      if (!el.isConnected) { if (current === state) current = null; return; }
      const t = t0 + ((now - state.start) / 1000) * PIP_RATE;
      if (t >= t1) { state.play += 1; if (state.play >= 2) { paint(tEv); return; } state.start = now; }
      paint(Math.min(t, t1));
      state.raf = requestAnimationFrame(loop);
    };
    state.raf = requestAnimationFrame(loop);
    el.querySelector('.pip-again').onclick = () => { cancelAnimationFrame(state.raf); state.play = 0; state.start = performance.now(); state.raf = requestAnimationFrame(loop); };
  }
  el.querySelector('.pip-x').onclick = closePip;
  el.addEventListener('keydown', (e) => { if (e.key === 'Escape') { e.stopPropagation(); closePip(); } });
  el.querySelector('.pip-x').focus();
  return el;
}

function roundRect(ctx, x, y, w, h, r) { ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r); ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath(); }

/** 위에서 본 가상 도로(평가·강습 모드) */
function drawRoadScene(ctx, cv, f, t, tEv) {
  const W = cv.width, H = cv.height; const ppm = H / 19; const egoY = H * 0.62;
  const X = (x) => W / 2 + x * ppm; const Y = (s) => egoY - (s - f.s) * ppm;
  ctx.fillStyle = T('--road-bg'); ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = T('--road-asphalt'); ctx.fillRect(X(-5.25), 0, 10.5 * ppm, H);
  ctx.strokeStyle = T('--road-line'); ctx.lineWidth = 3;
  for (const x of [-5.25, 5.25]) { ctx.beginPath(); ctx.moveTo(X(x), 0); ctx.lineTo(X(x), H); ctx.stroke(); }
  ctx.setLineDash([3 * ppm, 6 * ppm]); ctx.lineDashOffset = -((f.s * ppm) % (9 * ppm));
  for (const x of [-1.75, 1.75]) { ctx.beginPath(); ctx.moveTo(X(x), 0); ctx.lineTo(X(x), H); ctx.stroke(); }
  ctx.setLineDash([]);
  const car = (x, s, color, ego) => {
    const w = 1.83 * ppm, l = 4.7 * ppm; ctx.fillStyle = color; roundRect(ctx, X(x) - w / 2, Y(s) - l / 2, w, l, 0.35 * ppm); ctx.fill();
    ctx.fillStyle = ego ? T('--car-glass') : T('--car-glass-npc'); roundRect(ctx, X(x) - w / 2 + 0.2 * ppm, Y(s) - l / 2 + l * 0.22, w - 0.4 * ppm, l * 0.17, 0.12 * ppm); ctx.fill();
  };
  for (const n of f.npcs || []) car(n[1], n[2], n[3] ? T('--warn-orange') : T('--car-npc'), false);
  car(f.x, f.s, T('--primary'), true);
  if (f.sig && f.sig !== 'NONE' && Math.floor(t * 2.5) % 2 === 0) {
    ctx.fillStyle = T('--car-signal'); const sx = f.sig === 'LEFT' ? X(f.x) - 1.05 * ppm : X(f.x) + 0.75 * ppm; ctx.fillRect(sx, Y(f.s) - 2.2 * ppm, 0.3 * ppm, 0.6 * ppm);
  }
  // 사건 순간: 내 차 둘레에 고리
  const near = Math.abs(t - tEv);
  if (near < 0.7) { ctx.strokeStyle = TA('--danger-2', 0.85 * (1 - near / 0.7)); ctx.lineWidth = 4; ctx.beginPath(); ctx.arc(X(f.x), Y(f.s), 3.6 * ppm, 0, Math.PI * 2); ctx.stroke(); }
  speedBadge(ctx, f, W);
}

/** 계기 재현(연습 모드 — 차량 신호만 있고 주변 차 위치는 없다) */
function drawGauges(ctx, cv, f, t, tEv) {
  const W = cv.width, H = cv.height;
  ctx.fillStyle = T('--surface'); ctx.fillRect(0, 0, W, H);
  // 핸들
  const cx = W * 0.26, cy = H * 0.5, R = H * 0.3;
  ctx.save(); ctx.translate(cx, cy); ctx.rotate(((f.steer || 0) * Math.PI) / 180);
  ctx.strokeStyle = T('--text'); ctx.lineWidth = 14; ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.stroke();
  ctx.lineWidth = 10; ctx.beginPath(); ctx.moveTo(-R, 0); ctx.lineTo(R, 0); ctx.moveTo(0, 0); ctx.lineTo(0, R); ctx.stroke();
  ctx.fillStyle = T('--primary'); ctx.fillRect(-5, -R - 8, 10, 16);
  ctx.restore();
  label(ctx, `핸들 ${Math.round(f.steer || 0)}°`, cx, cy + R + 34);
  // 페달 두 개(가속·브레이크): 막대 높이 = 밟은 정도
  const bar = (x, v, name, color) => {
    const bh = H * 0.56, bw = 52, y0 = H * 0.2;
    ctx.fillStyle = T('--surface3'); roundRect(ctx, x, y0, bw, bh, 10); ctx.fill();
    const fill = bh * clamp((v || 0) / 100, 0, 1); ctx.fillStyle = color; roundRect(ctx, x, y0 + bh - fill, bw, fill, 10); ctx.fill();
    label(ctx, `${name} ${Math.round(v || 0)}%`, x + bw / 2, y0 + bh + 34);
  };
  bar(W * 0.56, f.accel, '가속', T('--primary'));
  bar(W * 0.72, f.brake, '브레이크', T('--danger-2'));
  // 깜빡이
  if (f.sig && f.sig !== 'NONE' && Math.floor(t * 2.5) % 2 === 0) { ctx.fillStyle = T('--car-signal'); ctx.font = '700 44px sans-serif'; ctx.textAlign = 'center'; ctx.fillText(f.sig === 'LEFT' ? '◀' : '▶', W * 0.26 + (f.sig === 'LEFT' ? -R - 30 : R + 30), cy + 14); }
  const near = Math.abs(t - tEv);
  if (near < 0.7) { ctx.strokeStyle = TA('--danger-2', 0.85 * (1 - near / 0.7)); ctx.lineWidth = 8; ctx.strokeRect(6, 6, W - 12, H - 12); }
  speedBadge(ctx, f, W);
}

function label(ctx, text, x, y) { ctx.fillStyle = T('--sub'); ctx.font = '600 22px Pretendard Variable, sans-serif'; ctx.textAlign = 'center'; ctx.fillText(text, x, y); }
function speedBadge(ctx, f, W) {
  ctx.fillStyle = TA('--bg', 0.88); roundRect(ctx, W - 170, 14, 156, 64, 14); ctx.fill();
  ctx.fillStyle = T('--text'); ctx.font = '700 40px Pretendard Variable, sans-serif'; ctx.textAlign = 'right'; ctx.fillText(String(Math.round(f.kmh || 0)), W - 70, 62);
  ctx.font = '600 18px Pretendard Variable, sans-serif'; ctx.textAlign = 'left'; ctx.fillStyle = T('--sub'); ctx.fillText('km/h', W - 64, 62);
}
