// 치일까봐 웹 프로토타입 — 화면·모의 차량·흐름
import * as C from './core.js';
import { mascot, icon, badgeArt, drivingViewArt } from './art.js';

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

// 단조 증가 시계(신호 수신 시각·미션 시작 시각 공통)
let clockMs = 0;
const now = () => (clockMs = Math.max(clockMs + 1, Date.now()));

// ── 저장(기기 내, localStorage) ──────────────────────────────
const STORE_KEY = 'chiilkkabwa.web.v1';
const blankHistory = () => ({
  alias: '나', sound: true, largeText: false, introSeen: false,
  missionResults: [], parkingSimCompleted: 0, pactsMade: 0, reviewsSaved: 0, graduated: false, mentorSessions: 0,
  badges: [], reviews: [], lastNextGoal: null, pedalStds: [], firstAt: null,
});
function loadHistory() {
  try { const raw = localStorage.getItem(STORE_KEY); if (raw) return { ...blankHistory(), ...JSON.parse(raw) }; } catch (e) { /* 저장소 없음 */ }
  return blankHistory();
}
function saveHistory() { try { localStorage.setItem(STORE_KEY, JSON.stringify(H)); } catch (e) { /* 무시 */ } }
let H = loadHistory();

// ── 모의 차량 ────────────────────────────────────────────────
const SIGNALS = {
  gear: { label: '기어', init: 'P' },
  parkingBrakeOn: { label: '주차 브레이크', init: true },
  uxRestricted: { label: '주행 제한(UX)', init: false },
  driverBeltBuckled: { label: '안전벨트', init: true },
  brakePedalPct: { label: '브레이크 페달 압력', init: 0 },
  brakePressed: { label: '브레이크 밟음', init: false },
  turnSignal: { label: '방향지시등', init: 'NONE' },
  wiperOn: { label: '와이퍼', init: false },
  lowBeamOn: { label: '전조등', init: false },
  steeringDeg: { label: '핸들 각도', init: -90 },
  passengerOccupied: { label: '조수석 착석', init: false },
  tripDistanceKm: { label: '트립 거리', init: 1204.3 },
  tripTimeSec: { label: '트립 시간', init: 0 },
  speedKmh: { label: '속도', init: 0 },
};
const STATUS_KEYS = ['gear', 'parkingBrakeOn', 'uxRestricted', 'driverBeltBuckled', 'brakePressed', 'turnSignal', 'wiperOn', 'lowBeamOn', 'steeringDeg', 'passengerOccupied', 'tripDistanceKm'];
const STATUS_LABEL = { OK: '정상', UNSUPPORTED: '미지원', PERMISSION_DENIED: '권한 거부', DISCONNECTED: '끊김' };

const V = {};
const statusOverride = {};
for (const [k, d] of Object.entries(SIGNALS)) { V[k] = { value: d.init, receivedAt: now() }; statusOverride[k] = 'OK'; }
let booting = true;
setTimeout(() => { booting = false; for (const k in V) V[k].receivedAt = now(); }, 1400);

function setV(k, value, { onlyIfChanged = false } = {}) {
  if (onlyIfChanged && V[k].value === value) return;
  V[k] = { value, receivedAt: now() };
}
function snapshot() {
  const s = {};
  for (const k in V) {
    let st = booting ? 'UNKNOWN' : statusOverride[k];
    if (k === 'brakePedalPct') st = booting ? 'UNKNOWN' : statusOverride.brakePressed; // 같은 페달 신호 묶음
    if (k === 'tripTimeSec') st = booting ? 'UNKNOWN' : statusOverride.tripDistanceKm;
    s[k] = { value: st === 'OK' ? V[k].value : null, status: st, source: 'MOCK', receivedAt: V[k].receivedAt };
  }
  return s;
}
const policy = { ...C.DEFAULT_POLICY };

// 주행 시뮬레이션
let timeScale = 1;
let simMs = 0;
let tripSecF = 0;

// ── 앱 상태 ──────────────────────────────────────────────────
let session = C.initialSession();
let prep = null; // 준비 단계 상태
let review = null; // 복기 초안
let overlay = null; // 'history' | 'profile' | 'cert' | 'intro'
let judgeState = null; // { judge, progress, doneAt }
let lastGate = null;

function newPrep() {
  return {
    step: 'goal', goal: null, mode: null, missions: [], missionIdx: 0,
    pacts: [], doneIds: [], parkingDone: false, pedalMetric: null,
  };
}

// ── 소리(정차 중만) ──────────────────────────────────────────
let audioCtx = null;
function chime(kind = 'done') {
  if (!H.sound || session.phase === C.Phase.DRIVING_LOCK) return;
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    const notes = kind === 'badge' ? [659, 880, 1175] : kind === 'click' ? [1400] : [784, 1047];
    notes.forEach((f, i) => {
      const o = audioCtx.createOscillator(), g = audioCtx.createGain();
      o.type = kind === 'click' ? 'square' : 'sine'; o.frequency.value = f;
      const t = audioCtx.currentTime + i * 0.09;
      g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(kind === 'click' ? 0.05 : 0.12, t + 0.01);
      g.gain.exponentialRampToValueAtTime(0.0001, t + (kind === 'click' ? 0.05 : 0.35));
      o.connect(g).connect(audioCtx.destination); o.start(t); o.stop(t + 0.4);
    });
  } catch (e) { /* 소리 없음 */ }
}

// ── 배지 ─────────────────────────────────────────────────────
const toastQueue = [];
function awardBadges() {
  const got = C.newlyEarned(H, Date.now());
  if (!got.length) return;
  H.badges.push(...got); saveHistory();
  got.forEach((b) => toastQueue.push(b));
}
let toastBusy = false;
function flushToasts() {
  if (toastBusy || !toastQueue.length || session.phase === C.Phase.DRIVING_LOCK) return;
  const b = toastQueue.shift(); const meta = C.Badges[b.badge];
  const el = document.createElement('div');
  el.className = 'toast';
  el.innerHTML = `${badgeArt(meta, true, 48)}<div><small>배지</small><b>${meta.title}${b.mockOnly ? ' <em>(모의)</em>' : ''}</b></div>`;
  $('#toasts').appendChild(el); toastBusy = true; chime('badge');
  setTimeout(() => { el.classList.add('out'); setTimeout(() => { el.remove(); toastBusy = false; }, 250); }, 2000);
}

// ── 화면 공통 조각 ───────────────────────────────────────────
const srcChip = (src = 'MOCK') => `<span class="src-chip src-${src}">${C.SourceLabel[src]}</span>`;
const btn = (id, label, cls = 'primary', extra = '') => `<button type="button" class="btn ${cls}" id="${id}" ${extra}>${label}</button>`;
function chipGroup(name, options, selected, { multi = false } = {}) {
  return `<div class="chips" role="${multi ? 'group' : 'radiogroup'}" data-group="${name}">${options.map((o) => {
    const on = multi ? selected.includes(o) : selected === o;
    return `<button type="button" class="chip${on ? ' on' : ''}" role="${multi ? 'checkbox' : 'radio'}" aria-checked="${on}" data-v="${esc(o)}">${on ? icon('check', 18) : ''}<span>${esc(o)}</span></button>`;
  }).join('')}</div>`;
}

// ── 화면들 ───────────────────────────────────────────────────
const Screens = {};

// A-1 안전 게이트
Screens.gate = () => ({
  mount(el) {
    el.innerHTML = `<div class="screen center">
      <div class="hero"><div class="hero-m" id="gm">${mascot('worried', 104)}</div>
        <div><h1>연습 준비를 확인할게요</h1><p class="sub">차가 직접 확인해요. 셋 다 ‘확인’이면 시작할 수 있어요.</p></div></div>
      <ul class="checks">
        <li data-k="gear">${icon('gear', 30)}<span class="ck-l">기어 P</span><span class="ck-s"></span></li>
        <li data-k="pb">${icon('pbrake', 30)}<span class="ck-l">주차 브레이크</span><span class="ck-s"></span></li>
        <li data-k="ux">${icon('road', 30)}<span class="ck-l">주행 제한 없음</span><span class="ck-s"></span></li>
      </ul>
      <p class="reason" id="gateReason" aria-live="polite"></p>
      ${btn('startBtn', '연습 시작', 'primary big')}
      <p class="caption">속도가 0이어도 기어가 P가 아니면 주차로 보지 않아요.</p>
    </div>`;
    $('#startBtn').onclick = () => {
      session = C.onStartPressed(session);
      if (session.phase === C.Phase.PREPARING) { prep = newPrep(); if (!H.firstAt) { H.firstAt = Date.now(); saveHistory(); } }
      render();
    };
    this.lastMood = 'worried';
  },
  update() {
    const s = snapshot();
    const st = (sig, okFn) => (sig.status !== 'OK' ? (sig.status === 'UNKNOWN' ? 'checking' : 'blocked') : okFn(sig.value) ? 'ok' : 'wait');
    const rows = { gear: st(s.gear, (v) => v === 'P'), pb: st(s.parkingBrakeOn, (v) => v === true), ux: st(s.uxRestricted, (v) => v === false) };
    const text = { ok: '확인', wait: '대기', checking: '확인 중', blocked: '읽을 수 없음' };
    for (const [k, v] of Object.entries(rows)) {
      const li = $(`li[data-k="${k}"]`); if (!li) continue;
      li.dataset.state = v;
      const s2 = $('.ck-s', li);
      const html = `${icon(v === 'ok' ? 'check' : 'wait', 20)}${text[v]}`;
      if (s2.dataset.v !== v) { s2.innerHTML = html; s2.dataset.v = v; }
    }
    const ready = session.phase === C.Phase.READY;
    $('#startBtn').disabled = !ready;
    $('#gateReason').textContent = ready ? '준비됐어요. 시작해 볼까요?' : C.LockReason[session.lockReason] || '';
    const mood = ready ? 'happy' : 'worried';
    if (mood !== this.lastMood) { $('#gm').innerHTML = mascot(mood, 104); this.lastMood = mood; }
  },
});

// A-2 목표 고르기
Screens.goal = () => ({
  mount(el) {
    const last = H.lastNextGoal;
    const options = last && !C.CONTENT.goals.includes(last) ? [last, ...C.CONTENT.goals] : C.CONTENT.goals;
    el.innerHTML = `<div class="screen">
      <h1>오늘 뭐가 제일 걱정돼요?</h1>
      ${last ? `<div class="banner info">${icon('note', 22)}<span>지난번 목표: <b>${esc(last)}</b> — 이어서 해 볼까요?</span></div>` : ''}
      <div id="goalChips">${chipGroup('goal', options, prep.goal)}</div>
      <div class="banner mentor" id="passBanner" hidden>${icon('mentor', 22)}<span>조수석에 누가 앉아 있네요. 함께 연습해요</span></div>
      <div class="actions two">
        ${btn('soloBtn', '혼자 연습', 'secondary big', 'disabled')}
        ${btn('mentorBtn', `${icon('mentor', 22)} 멘토와 함께`, 'primary big', 'disabled')}
      </div>
      <p class="caption" id="soloNote">필수 워밍업 3개 + 원하면 선택 미션 · 약 5분. 실제 도로 연습은 동승자와 함께 하세요.</p>
    </div>`;
    rebindChipsSimple(el, 'goal', options, () => prep.goal, (v) => { prep.goal = v; }, { after: () => this.update() });
    const pick = (mode) => { prep.mode = mode; startWarmup(); render(); };
    $('#soloBtn').onclick = () => pick('solo');
    $('#mentorBtn').onclick = () => pick('mentor');
  },
  update() {
    const s = snapshot();
    const passenger = C.usable(s.passengerOccupied) && s.passengerOccupied.value === true;
    $('#passBanner').hidden = !passenger;
    $('#soloBtn').disabled = !prep.goal; $('#mentorBtn').disabled = !prep.goal;
    $('#mentorBtn').classList.toggle('glow', passenger && !!prep.goal);
  },
});

function bindChips(root, group, onPick, { multi = false, max = Infinity, selected = null } = {}) {
  $$(`.chips[data-group="${group}"] .chip`, root).forEach((b) => {
    b.onclick = () => {
      chime('click');
      if (!multi) return onPick(b.dataset.v);
      const cur = selected();
      const v = b.dataset.v;
      if (cur.includes(v)) onPick(cur.filter((x) => x !== v));
      else if (cur.length < max) onPick([...cur, v]);
      else flashNote(`약속은 ${max}개까지 고를 수 있어요`);
    };
  });
}
function rebindChipsSimple(root, group, options, getSel, setSel, opts = {}) {
  const box = $(`.chips[data-group="${group}"]`, root).parentElement;
  const redraw = () => {
    box.innerHTML = chipGroup(group, options, getSel(), opts);
    bindChips(root, group, (v) => { setSel(v); redraw(); opts.after?.(); }, { ...opts, selected: getSel });
  };
  redraw();
}

function startWarmup() {
  prep.missions = [...C.MissionCatalog.required(), ...C.MissionCatalog.optional()];
  prep.missionIdx = 0; prep.step = 'mission';
  beginJudge(prep.missions[0]);
}
function beginJudge(judge) {
  judge.start(snapshot(), now());
  judgeState = { judge, progress: null, doneAt: null, recorded: false };
}
/** 목표에 따라 단계 순서를 바꾼다: '주차'가 걱정이면 가상 주차를 페달보다 먼저(연습 전 5분 안에 핵심부터) */
function stepOrder(p) {
  const base = p?.goal === '주차' ? ['goal', 'mission', 'parking', 'pedal', 'pact', 'go'] : ['goal', 'mission', 'pedal', 'parking', 'pact', 'go'];
  return p?.mode === 'solo' ? base.filter((x) => x !== 'pact') : base;
}
function nextPrepStep() {
  judgeState = null;
  const order = stepOrder(prep);
  prep.step = order[order.indexOf(prep.step) + 1];
  if (prep.step === 'pedal') beginJudge(C.pedalHoldJudge('brakePedalPct'));
  render();
}
function recordMission(id, prog) {
  H.missionResults.push({ id, source: prog.source, metric: prog.metric, at: Date.now() });
  if (id === 'PEDAL_FEEL' && prog.metric != null) H.pedalStds.push({ at: Date.now(), std: +prog.metric.toFixed(2) });
  prep.doneIds.push(id);
  saveHistory(); awardBadges(); chime('done');
}

// A-3 워밍업 미션
const STATUS_HELP = { UNSUPPORTED: '이 차에서는 이 신호를 읽을 수 없어요.', PERMISSION_DENIED: '이 신호를 읽을 권한이 없어요.', DISCONNECTED: '차량 신호가 잠시 끊겼어요.' };
Screens.mission = () => ({
  mount(el) {
    const j = judgeState.judge; const meta = C.MISSION_META[j.id];
    const total = prep.missions.length, n = prep.missionIdx + 1;
    el.innerHTML = `<div class="screen mission">
      <div class="m-head"><span class="m-count">운전석 워밍업 <b>${n}</b>/${total}</span>
        <div class="dots">${prep.missions.map((m, i) => `<i class="${i < prep.missionIdx ? 'd' : i === prep.missionIdx ? 'c' : ''}"></i>`).join('')}</div>
        ${meta.optional ? '<span class="tag soft">선택</span>' : ''}${srcChip('MOCK')}</div>
      <div class="m-card">
        <div class="m-ill" id="mIll">${icon(meta.icon, 84)}<div class="m-live" id="mLive"></div></div>
        <div class="m-text">
          <h1>${j.title}</h1>
          <p class="know"><span>필기</span>${meta.know}</p>
          <p class="know car"><span>내 차에서는</span>${meta.car}</p>
          <div class="m-state" id="mState" aria-live="polite"></div>
        </div>
      </div>
      <div class="actions" id="mActions"></div>
    </div>`;
    this.key = null;
  },
  update() {
    const p = judgeState.progress; if (!p) return;
    const j = judgeState.judge; const meta = C.MISSION_META[j.id];
    // 차가 읽은 값
    const s = snapshot(); const sig = s[meta.signal];
    $('#mLive').innerHTML = liveReadout(j.id, sig, s);
    const key = p.kind + (p.kind === 'progress' ? p.step + p.hint + (p.retry || '') + Math.round((p.holdRatio || 0) * 10) : '');
    if (key === this.key) return; this.key = key;
    const st = $('#mState'), act = $('#mActions');
    if (p.kind === 'progress') {
      st.innerHTML = `${p.total > 1 ? `<div class="m-steps">${Array.from({ length: p.total }, (_, i) => `<i class="${i < p.step - 1 ? 'd' : i === p.step - 1 ? 'c' : ''}"></i>`).join('')}<span>${p.step}/${p.total} 단계</span></div>` : ''}
        <p class="hint">${p.hint}</p>${p.retry ? `<p class="retry">${p.retry}</p>` : ''}
        ${p.holdRatio != null ? `<div class="holdbar"><i style="width:${Math.min(100, p.holdRatio * 100)}%"></i></div>` : ''}`;
      act.innerHTML = meta.optional ? btn('skipBtn', '이건 다음에 할게요', 'ghost') + btn('skipAllBtn', '선택 미션은 모두 다음에', 'ghost') : '';
      $('#mIll').classList.remove('ok');
    } else if (p.kind === 'done') {
      st.innerHTML = `<div class="praise">${mascot('happy', 72)}<div><p class="praise-t">${p.praise}</p><p class="caption">${srcChip(p.source)} 으로 확인했어요</p></div></div>`;
      act.innerHTML = btn('nextBtn', prep.missionIdx + 1 < prep.missions.length ? '다음 미션' : '워밍업 마치기', 'primary big');
      $('#mIll').classList.add('ok');
      setTimeout(() => $('#nextBtn')?.focus(), 50);
    } else {
      st.innerHTML = `<div class="banner warn">${icon('info', 22)}<span>${STATUS_HELP[p.status] || '신호를 읽을 수 없어요.'} 이 미션은 건너뛸게요.</span></div>`;
      act.innerHTML = btn('skipBtn', '건너뛰기', 'secondary big');
    }
    const go = () => {
      prep.missionIdx++;
      if (prep.missionIdx < prep.missions.length) { beginJudge(prep.missions[prep.missionIdx]); render(); }
      else nextPrepStep();
    };
    $('#nextBtn') && ($('#nextBtn').onclick = go);
    $('#skipBtn') && ($('#skipBtn').onclick = go);
    $('#skipAllBtn') && ($('#skipAllBtn').onclick = () => { prep.missionIdx = prep.missions.length; nextPrepStep(); });
  },
});

function liveReadout(id, sig, s) {
  if (!sig || sig.status !== 'OK') return `<span class="live off">${sig ? (STATUS_LABEL[sig.status] || '확인 중') : ''}</span>`;
  const v = sig.value;
  switch (id) {
    case 'SEAT_BELT': return `<span class="live ${v ? 'on' : ''}">${v ? '체결' : '미체결'}</span>`;
    case 'BRAKE_PRESS_RELEASE': return `<span class="live ${v > 5 ? 'on' : ''}">${v > 5 ? `밟음 ${Math.round(v)}%` : '뗌'}</span>`;
    case 'TURN_SIGNAL': return `<span class="live turn">${v === 'LEFT' ? '<b class="blink">◀</b> 왼쪽' : v === 'RIGHT' ? '오른쪽 <b class="blink">▶</b>' : '꺼짐'}</span>`;
    case 'WIPER': case 'LOW_BEAM': return `<span class="live ${v ? 'on' : ''}">${v ? '켜짐' : '꺼짐'}</span>`;
    case 'STEERING_CENTER': return `<span class="live ${Math.abs(v) <= 5 ? 'on' : ''}">${Math.round(v)}°</span><span class="wheel-mini" style="transform:rotate(${v}deg)">${icon('wheel', 40)}</span>`;
    default: return '';
  }
}

// A-4 페달 감각
Screens.pedal = () => ({
  mount(el) {
    const j = judgeState.judge;
    el.innerHTML = `<div class="screen pedal">
      <div class="m-head"><span class="m-count">페달 감각 · 살살 밟기</span><span class="tag soft">선택</span>${srcChip('MOCK')}</div>
      <div class="pedal-wrap">
        <div class="gauge" aria-label="브레이크 페달 압력">
          <div class="g-band" style="bottom:${j.low}%;height:${j.high - j.low}%"><span>초록 칸</span></div>
          <div class="g-fill" id="gFill"></div>
          <div class="g-mark" id="gMark"><b id="gVal">0%</b></div>
          ${[0, 25, 50, 75, 100].map((t) => `<i class="g-tick" style="bottom:${t}%"><em>${t}</em></i>`).join('')}
        </div>
        <div class="pedal-text">
          <h1>초록 칸 안에서 2초 버티기</h1>
          <p class="sub">브레이크 페달을 살살 밟아 지금 <b id="pNow">0</b>%를 ${j.low}~${j.high}% 사이에 머물게 해 보세요.</p>
          <div class="ring" id="pRing" style="--p:0"><span id="pRingT">2.0초</span></div>
          <div id="pState" aria-live="polite"></div>
          <p class="caption">이 값은 연습용이며 실제 운전 기준이 아니에요. 가속 페달 대신 브레이크 페달로 해요.</p>
        </div>
      </div>
      <div class="actions" id="pActions">${btn('skipBtn', '이건 다음에 할게요', 'ghost')}</div>
    </div>`;
    $('#skipBtn').onclick = () => nextPrepStep();
    this.key = null;
  },
  update() {
    const p = judgeState.progress; if (!p) return;
    const s = snapshot(); const v = C.usable(s.brakePedalPct) ? s.brakePedalPct.value : 0;
    $('#gFill').style.height = `${v}%`; $('#gMark').style.bottom = `${v}%`; $('#gVal').textContent = `${Math.round(v)}%`; $('#pNow').textContent = Math.round(v);
    const inBand = v >= 20 && v <= 35; $('.gauge').classList.toggle('in', inBand);
    const ratio = p.kind === 'done' ? 1 : p.holdRatio || 0;
    $('#pRing').style.setProperty('--p', ratio); $('#pRingT').textContent = p.kind === 'done' ? '완료' : `${(2 - ratio * 2).toFixed(1)}초`;
    const key = p.kind; if (key === this.key) return; this.key = key;
    if (p.kind === 'done') {
      const prev = H.pedalStds.length >= 2 ? H.pedalStds[H.pedalStds.length - 2].std : null;
      const cmp = prev != null ? (p.metric <= prev ? `지난번(${prev.toFixed(1)}%p)보다 부드러웠어요` : `지난번은 ${prev.toFixed(1)}%p였어요. 다음에 또 비교해 봐요`) : '다음 연습 때 오늘 기록과 비교해 드릴게요';
      $('#pState').innerHTML = `<div class="praise">${mascot('happy', 64)}<div><p class="praise-t">${p.praise}</p><p class="caption">흔들림 ${p.metric.toFixed(1)}%p · ${cmp}</p></div></div>`;
      const nx = stepOrder(prep)[stepOrder(prep).indexOf('pedal') + 1];
      $('#pActions').innerHTML = btn('nextBtn', `다음: ${{ parking: '가상 주차', pact: '멘토 약속', go: '출발 준비' }[nx]}`, 'primary big');
      $('#nextBtn').onclick = () => nextPrepStep();
    } else if (p.kind === 'unavailable') {
      $('#pState').innerHTML = `<div class="banner warn">${icon('info', 22)}<span>${STATUS_HELP[p.status]} 이 단계는 건너뛸게요.</span></div>`;
      $('#pActions').innerHTML = btn('skipBtn', '건너뛰기', 'secondary big'); $('#skipBtn').onclick = () => nextPrepStep();
    } else {
      $('#pState').innerHTML = '';
    }
  },
});

// A-5 가상 주차
const WORLD_TOP = -9.2, WORLD_H = 12.2; // 화면 축척: 가로 18m × 세로 12.2m
const DIMS = { lengthMm: 4700, widthMm: 1830, wheelbaseMm: 2720, isDefault: true };
Screens.parking = () => ({
  mount(el) {
    const sim = new C.ParkingSim(DIMS);
    this.sim = sim; this.slot = { cx: 0, cy: 0, w: 2.5, l: 5 };
    this.start = { x: 3.2, y: -5.5, heading: Math.PI / 2 };
    this.pose = { ...this.start }; this.dir = -1; this.move = 0; this.local = 0; this.useVehicle = true; this.bump = 0; this.done = false;
    this.neighbors = [-5, -2.5, 2.5, 5].map((cx) => ({ cx, cy: 0.1, poly: rectPoly(cx, 0.1, 1.83, 4.6) }));
    this.refX = this.slot.cx + sim.minRadius;
    el.innerHTML = `<div class="screen parking">
      <div class="m-head"><span class="m-count">내 차로 하는 가상 주차 · 후진 주차</span>${srcChip('MOCK')}
        <span class="pk-top">${btn('pkReset', `${icon('reset', 18)} 처음부터`, 'ghost')}${btn('pkSkip', '다음에 할게요', 'ghost')}</span></div>
      <div class="pk-canvas"><canvas id="pk" aria-label="가상 주차 탑뷰"></canvas>
        <span class="pk-dims">내 차 ${(DIMS.lengthMm / 1000).toFixed(1)}m × ${(DIMS.widthMm / 1000).toFixed(2)}m${DIMS.isDefault ? ' · 기본값' : ''}</span>
        <div class="pk-msg" id="pkMsg" aria-live="polite"></div>
        <p class="pk-tip">${icon('info', 18)} 뒷바퀴(노란 점)가 옆 차 범퍼를 막 지났을 때(노란 기준선) 핸들을 끝까지</p></div>
      <div class="pk-controls">
        <div class="pk-steer"><div class="pk-wheel" id="pkWheel">${icon('wheel', 56)}</div>
          <div><b id="pkDeg">0°</b><small id="pkWheelDeg">바퀴 0°</small><small id="pkSrc"></small></div></div>
        <div class="pk-slide"><input type="range" id="pkSlider" min="-540" max="540" step="10" value="0" aria-label="화면 핸들">
          <label class="c-check"><input type="checkbox" id="pkFollow" checked> 차 핸들 따라가기</label></div>
        <div class="pk-move">
          <button type="button" class="btn secondary big hold" id="pkFwd">${icon('arrowUp', 24)} 전진</button>
          <button type="button" class="btn primary big hold" id="pkRev">${icon('arrowDown', 24)} 후진</button>
        </div>
      </div>
      <div class="actions" id="pkActions"></div>
      <p class="caption">버튼을 누르고 있는 동안 움직여요 · 키보드 ↑↓ 이동, ←→ 핸들 · 화면 속 연습이라 실제 차는 움직이지 않아요</p>
    </div>`;
    const cv = $('#pk'); this.cv = cv; this.ctx = cv.getContext('2d');
    this.resize = () => { const r = cv.parentElement.getBoundingClientRect(); const dpr = devicePixelRatio || 1; cv.width = r.width * dpr; cv.height = r.width * (WORLD_H / 18) * dpr; cv.style.height = `${r.width * (WORLD_H / 18)}px`; this.draw(); };
    this.ro = new ResizeObserver(this.resize); this.ro.observe(cv.parentElement);
    const hold = (b, d) => {
      const startM = (e) => { e.preventDefault(); if (this.done) return; this.move = d; this.dir = d; b.classList.add('down'); };
      const stopM = () => { this.move = 0; b.classList.remove('down'); };
      b.addEventListener('pointerdown', startM); ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => b.addEventListener(ev, stopM));
    };
    hold($('#pkFwd'), 1); hold($('#pkRev'), -1);
    $('#pkSlider').oninput = (e) => { this.local = +e.target.value; };
    $('#pkFollow').onchange = (e) => { this.useVehicle = e.target.checked; };
    $('#pkReset').onclick = () => { this.pose = { ...this.start }; this.done = false; this.dir = -1; this.updateActions(); };
    $('#pkSkip').onclick = () => nextPrepStep();
    this.keys = (e) => {
      if (overlay) return;
      const down = e.type === 'keydown';
      if (e.key === 'ArrowUp' || e.key === 'ArrowDown') { e.preventDefault(); if (this.done) return; this.move = down ? (e.key === 'ArrowUp' ? 1 : -1) : 0; if (down) this.dir = this.move; }
      if (down && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
        e.preventDefault();
        const d = e.key === 'ArrowLeft' ? -30 : 30;
        if (this.followsVehicle()) setSteer(V.steeringDeg.value + d); else { this.local = clamp(this.local + d, -540, 540); $('#pkSlider').value = this.local; }
      }
    };
    addEventListener('keydown', this.keys); addEventListener('keyup', this.keys);
    let last = performance.now();
    const loop = (t) => {
      if (!this.alive) return;
      const dt = Math.min(0.05, (t - last) / 1000); last = t;
      this.stepSim(dt); this.draw();
      this.raf = requestAnimationFrame(loop);
    };
    this.alive = true; this.raf = requestAnimationFrame(loop);
    this.updateActions();
  },
  unmount() { this.alive = false; cancelAnimationFrame(this.raf); this.ro?.disconnect(); removeEventListener('keydown', this.keys); removeEventListener('keyup', this.keys); },
  followsVehicle() { const s = snapshot(); return this.useVehicle && C.usable(s.steeringDeg); },
  steering() { if (this.followsVehicle()) return snapshot().steeringDeg.value; return this.local; },
  stepSim(dt) {
    if (!this.move || this.done) { this.bump = Math.max(0, this.bump - dt); return; }
    const speed = this.move > 0 ? 1.0 : -0.85;
    const next = this.sim.step(this.pose, this.steering(), speed, dt);
    const poly = this.sim.corners(next);
    const hit = this.neighbors.some((n) => C.polysOverlap(poly, n.poly));
    const out = poly.some((p) => p.x < -8.9 || p.x > 8.9 || p.y < WORLD_TOP + 0.1 || p.y > WORLD_TOP + WORLD_H - 0.1);
    if (hit || out) { this.bump = 1.2; this.move = 0; $$('.hold.down').forEach((b) => b.classList.remove('down')); return; }
    this.pose = next;
    const st = this.sim.parkState(this.pose, this.slot);
    if (st.ok && !this.done) {
      this.done = true; this.move = 0;
      H.parkingSimCompleted++; prep.parkingDone = true; saveHistory(); awardBadges(); chime('done');
      this.updateActions();
    }
  },
  updateActions() {
    const a = $('#pkActions'); if (!a) return;
    const nx = stepOrder(prep)[stepOrder(prep).indexOf('parking') + 1];
    a.innerHTML = this.done ? btn('pkNext', `다음: ${{ pedal: '페달 감각', pact: '멘토 약속', go: '출발 준비' }[nx]}`, 'primary big') : '';
    if (this.done) { $('#pkNext').onclick = () => nextPrepStep(); setTimeout(() => $('#pkNext')?.focus(), 50); }
    $('#pkReset').innerHTML = `${icon('reset', 18)} ${this.done ? '한 번 더' : '처음부터'}`;
  },
  update() {
    const deg = this.steering(); const wheel = (this.sim.wheelAngleRad(deg) * 180) / Math.PI;
    $('#pkDeg').textContent = `${Math.abs(Math.round(deg))}° ${deg > 2 ? '오른쪽' : deg < -2 ? '왼쪽' : '가운데'}`;
    $('#pkWheelDeg').textContent = `바퀴 ${Math.abs(wheel).toFixed(0)}°${Math.abs(wheel) >= 34.9 ? ' · 끝까지' : ''}`;
    $('#pkWheel').style.transform = `rotate(${deg}deg)`;
    const fv = this.followsVehicle();
    $('#pkSlider').disabled = fv; if (fv) $('#pkSlider').value = deg;
    $('#pkSrc').textContent = fv ? '차 핸들 신호(모의)' : '화면 핸들';
    const msg = $('#pkMsg');
    let m = '', cls = '';
    if (this.done) { m = '칸 안에 들어왔어요!'; cls = 'ok'; }
    else if (this.bump > 0) { m = '옆 차에 닿을 것 같아요. 반대로 조금 움직여 볼까요?'; cls = 'warn'; }
    else {
      const ra = this.pose; const st = this.sim.parkState(this.pose, this.slot);
      const nearRef = Math.abs(ra.x - this.refX) < 0.35 && Math.abs(Math.sin(ra.heading)) > 0.9;
      if (nearRef && Math.abs(wheel) < 30) { m = '지금! 핸들을 오른쪽 끝까지 돌리고 후진해요'; cls = 'key'; }
      else if (Math.abs(Math.sin(ra.heading)) < 0.12 && Math.abs(wheel) > 8 && st.dx < 1.2 && ra.y < 1.4) { m = '차가 똑바로 섰어요. 핸들을 가운데로 풀고 쭉 후진!'; cls = 'key'; }
      else if (st.dx < 1.2 && st.dy < 2.5) m = st.headingErr > 6 ? `거의 다 왔어요. 차 방향을 ${st.headingErr.toFixed(0)}°만 더 세워 볼까요?` : st.dy > 0.35 ? '방향 좋아요. 조금 더 천천히 후진!' : '좌우를 조금만 맞춰 볼까요?';
    }
    if (msg.dataset.m !== m) { msg.textContent = m; msg.className = `pk-msg ${cls}`; msg.dataset.m = m; }
  },
  draw() {
    const { ctx, cv, sim, slot } = this; if (!ctx || !cv.width) return;
    const W = cv.width, S = W / 18; const X = (x) => (x + 9) * S, Y = (y) => (y - WORLD_TOP) * S;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.fillStyle = '#18202A'; ctx.fillRect(0, 0, W, cv.height);
    // 통로
    ctx.fillStyle = '#1C2531'; ctx.fillRect(0, 0, W, Y(-2.5));
    ctx.strokeStyle = '#2E3A4A'; ctx.setLineDash([S * 0.8, S * 0.6]); ctx.lineWidth = S * 0.08;
    ctx.beginPath(); ctx.moveTo(0, Y(-7.2)); ctx.lineTo(W, Y(-7.2)); ctx.stroke(); ctx.setLineDash([]);
    // 주차선
    ctx.strokeStyle = '#C9D3DF'; ctx.lineWidth = S * 0.1;
    for (const x of [-6.25, -3.75, -1.25, 1.25, 3.75, 6.25]) { ctx.beginPath(); ctx.moveTo(X(x), Y(-2.5)); ctx.lineTo(X(x), Y(2.5)); ctx.stroke(); }
    ctx.beginPath(); ctx.moveTo(X(-6.25), Y(2.5)); ctx.lineTo(X(6.25), Y(2.5)); ctx.stroke();
    // 목표 칸
    const parked = this.done;
    ctx.fillStyle = parked ? 'rgba(39,174,96,.28)' : 'rgba(47,128,237,.16)';
    ctx.fillRect(X(slot.cx - 1.25) + 2, Y(-2.5), (2.5 * S) - 4, 5 * S);
    ctx.strokeStyle = parked ? '#27AE60' : '#2F80ED'; ctx.setLineDash([S * 0.25, S * 0.2]); ctx.lineWidth = S * 0.06;
    ctx.strokeRect(X(slot.cx - 1.1), Y(-2.35), 2.2 * S, 4.7 * S); ctx.setLineDash([]);
    ctx.fillStyle = parked ? '#7EE2A8' : '#8DB8F5'; ctx.font = `700 ${S * 0.55}px Pretendard Variable, sans-serif`; ctx.textAlign = 'center';
    ctx.fillText(parked ? '도착!' : '여기', X(slot.cx), Y(0.2));
    // 옆 차
    for (const n of this.neighbors) drawCar(ctx, S, X, Y, n.poly, { body: this.bump > 0 && Math.abs(n.cx) === 2.5 ? '#6B4F12' : '#3A4757', glass: '#25303D' });
    // 기준선(뒷바퀴가 여기 오면 핸들 끝까지)
    ctx.strokeStyle = '#FFD66B'; ctx.lineWidth = S * 0.07; ctx.setLineDash([S * 0.3, S * 0.2]);
    ctx.beginPath(); ctx.moveTo(X(this.refX), Y(-8.6)); ctx.lineTo(X(this.refX), Y(-2.6)); ctx.stroke(); ctx.setLineDash([]);
    ctx.fillStyle = '#FFD66B'; ctx.font = `700 ${S * 0.42}px Pretendard Variable, sans-serif`; ctx.fillText('기준선', X(this.refX), Y(-8.8));
    // 궤적 미리보기(뒤축 중심)
    if (!parked) {
      let p = { ...this.pose }; const dir = this.dir || -1; const deg = this.steering();
      ctx.strokeStyle = dir < 0 ? 'rgba(255,214,107,.75)' : 'rgba(141,184,245,.75)'; ctx.lineWidth = S * 0.06; ctx.setLineDash([S * 0.12, S * 0.18]);
      ctx.beginPath(); ctx.moveTo(X(p.x), Y(p.y));
      for (let i = 0; i < 40; i++) { p = sim.step(p, deg, dir, 0.1); ctx.lineTo(X(p.x), Y(p.y)); }
      ctx.stroke(); ctx.setLineDash([]);
      ctx.globalAlpha = 0.18; drawCar(ctx, S, X, Y, sim.corners(p), { body: '#8DB8F5', glass: '#8DB8F5', outlineOnly: true }); ctx.globalAlpha = 1;
    }
    // 내 차
    drawMyCar(ctx, S, X, Y, sim, this.pose, this.steering(), parked);
    // 축척
    ctx.fillStyle = '#7F8C9D'; ctx.font = `500 ${S * 0.36}px Pretendard Variable, sans-serif`; ctx.textAlign = 'left';
    ctx.fillRect(X(-8.6), Y(2.75), 2 * S, S * 0.06); ctx.fillText('2m', X(-8.6), Y(2.6));
  },
});
function rectPoly(cx, cy, w, l) { return [{ x: cx - w / 2, y: cy - l / 2 }, { x: cx + w / 2, y: cy - l / 2 }, { x: cx + w / 2, y: cy + l / 2 }, { x: cx - w / 2, y: cy + l / 2 }]; }
function drawCar(ctx, S, X, Y, poly, { body, glass, outlineOnly }) {
  ctx.beginPath(); poly.forEach((p, i) => (i ? ctx.lineTo(X(p.x), Y(p.y)) : ctx.moveTo(X(p.x), Y(p.y)))); ctx.closePath();
  if (outlineOnly) { ctx.strokeStyle = body; ctx.lineWidth = S * 0.06; ctx.stroke(); return; }
  ctx.fillStyle = body; ctx.fill();
  // 앞유리(위쪽 1/4)
  const [a, b, c, d] = poly; const lerp = (p, q, t) => ({ x: p.x + (q.x - p.x) * t, y: p.y + (q.y - p.y) * t });
  const g1 = lerp(a, d, 0.2), g2 = lerp(b, c, 0.2), g3 = lerp(b, c, 0.36), g4 = lerp(a, d, 0.36);
  const inset = (p, q, t) => lerp(p, q, t);
  const q = [inset(g1, g2, 0.1), inset(g2, g1, 0.1), inset(g3, g4, 0.1), inset(g4, g3, 0.1)];
  ctx.beginPath(); q.forEach((p, i) => (i ? ctx.lineTo(X(p.x), Y(p.y)) : ctx.moveTo(X(p.x), Y(p.y)))); ctx.closePath(); ctx.fillStyle = glass; ctx.fill();
}
function drawMyCar(ctx, S, X, Y, sim, pose, steeringDeg, parked) {
  ctx.save();
  const c = sim.bodyCenter(pose);
  ctx.translate(X(c.x), Y(c.y)); ctx.rotate(pose.heading);
  const L = sim.length * S, Wd = sim.width * S, wb = sim.wheelbase * S;
  const wheel = sim.wheelAngleRad(steeringDeg);
  // 바퀴
  const tw = 0.24 * S, tl = 0.68 * S; ctx.fillStyle = '#0B0F14';
  const wheelAt = (x, y, ang) => { ctx.save(); ctx.translate(x, y); ctx.rotate(ang); ctx.fillRect(-tw / 2, -tl / 2, tw, tl); ctx.restore(); };
  const fy = -wb / 2, ry = wb / 2, wx = Wd / 2 - tw * 0.2;
  wheelAt(-wx, fy, wheel); wheelAt(wx, fy, wheel); wheelAt(-wx, ry, 0); wheelAt(wx, ry, 0);
  // 차체
  const r = 0.35 * S;
  ctx.fillStyle = parked ? '#27AE60' : '#2F80ED';
  roundRect(ctx, -Wd / 2, -L / 2, Wd, L, r); ctx.fill();
  ctx.fillStyle = 'rgba(255,255,255,.12)'; roundRect(ctx, -Wd / 2 + 0.12 * S, -L / 2 + 0.12 * S, Wd - 0.24 * S, L * 0.18, r * 0.7); ctx.fill();
  // 유리
  ctx.fillStyle = '#123A6B'; roundRect(ctx, -Wd / 2 + 0.2 * S, -L / 2 + L * 0.24, Wd - 0.4 * S, L * 0.16, 0.15 * S); ctx.fill();
  roundRect(ctx, -Wd / 2 + 0.25 * S, L / 2 - L * 0.22, Wd - 0.5 * S, L * 0.1, 0.12 * S); ctx.fill();
  // 지붕
  ctx.fillStyle = parked ? '#2FC46E' : '#4A93F0'; roundRect(ctx, -Wd / 2 + 0.22 * S, -L / 2 + L * 0.41, Wd - 0.44 * S, L * 0.34, 0.2 * S); ctx.fill();
  // 사이드미러
  ctx.fillStyle = parked ? '#27AE60' : '#2F80ED'; ctx.fillRect(-Wd / 2 - 0.16 * S, -L / 2 + L * 0.3, 0.18 * S, 0.12 * S); ctx.fillRect(Wd / 2 - 0.02 * S, -L / 2 + L * 0.3, 0.18 * S, 0.12 * S);
  // 전조등·후미등
  ctx.fillStyle = '#FFF4C2'; ctx.fillRect(-Wd / 2 + 0.15 * S, -L / 2 + 0.02 * S, 0.4 * S, 0.1 * S); ctx.fillRect(Wd / 2 - 0.55 * S, -L / 2 + 0.02 * S, 0.4 * S, 0.1 * S);
  ctx.fillStyle = '#FF6B6B'; ctx.fillRect(-Wd / 2 + 0.15 * S, L / 2 - 0.12 * S, 0.4 * S, 0.1 * S); ctx.fillRect(Wd / 2 - 0.55 * S, L / 2 - 0.12 * S, 0.4 * S, 0.1 * S);
  // 뒷바퀴 표시
  ctx.fillStyle = '#FFD66B'; [-1, 1].forEach((s) => { ctx.beginPath(); ctx.arc(s * (Wd / 2 + 0.28 * S), ry, 0.12 * S, 0, Math.PI * 2); ctx.fill(); });
  ctx.restore();
}
function roundRect(ctx, x, y, w, h, r) { ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r); ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath(); }
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

// A-6 멘토 약속
Screens.pact = () => ({
  mount(el) {
    el.innerHTML = `<div class="screen">
      <h1>오늘의 약속 <small>(최대 ${C.CONTENT.PACT_MAX}개)</small></h1>
      <p class="sub">옆자리에서 어떻게 말해 줄지 먼저 맞춰 두면, 차 안에서 덜 다퉈요.</p>
      <div>${chipGroup('pact', C.CONTENT.pacts, prep.pacts, { multi: true })}</div>
      <p class="caption" id="pactCount"></p>
      <div class="actions">${btn('pactBtn', `${icon('pact', 22)} 둘 다 약속했어요`, 'primary big', 'disabled')}</div>
    </div>`;
    rebindChipsSimple(el, 'pact', C.CONTENT.pacts, () => prep.pacts, (v) => { prep.pacts = v; }, { multi: true, max: C.CONTENT.PACT_MAX, after: () => this.update() });
    $('#pactBtn').onclick = () => { H.pactsMade++; saveHistory(); awardBadges(); chime('done'); nextPrepStep(); };
  },
  update() { $('#pactBtn').disabled = !prep.pacts.length; $('#pactCount').textContent = `${prep.pacts.length}/${C.CONTENT.PACT_MAX} 골랐어요`; },
});

// A-6b 출발 준비 완료
Screens.go = () => ({
  mount(el) {
    const n = prep.doneIds.filter((id) => id !== 'PEDAL_FEEL').length;
    el.innerHTML = `<div class="screen center">
      <div class="hero"><div class="hero-m">${mascot('happy', 104)}</div><div><h1>출발 준비 완료</h1>
      <p class="sub">기어를 D로 옮기면 화면은 쉬어요. 연습은 함께 탄 사람과 하세요.</p></div></div>
      <ul class="summary-list">
        <li>${icon('seat', 24)}<span>워밍업 <b>${n}</b>개 완료</span></li>
        <li>${icon('pedal', 24)}<span>살살 밟기 ${prep.doneIds.includes('PEDAL_FEEL') ? '<b>완료</b>' : '건너뜀'}</span></li>
        <li>${icon('parking', 24)}<span>가상 주차 ${prep.parkingDone ? '<b>완료</b>' : '건너뜀'}</span></li>
        ${prep.mode === 'mentor' ? `<li>${icon('pact', 24)}<span>오늘의 약속 <b>${prep.pacts.length}</b>개</span></li>` : ''}
        <li>${icon('note', 24)}<span>오늘 목표: <b>${esc(prep.goal)}</b></span></li>
      </ul>
      ${prep.mode === 'solo' ? `<div class="banner warn">${icon('mentor', 22)}<span>실제 도로 연습은 동승자와 함께 하세요</span></div>` : ''}
      ${prep.mode === 'mentor' && prep.pacts.length ? `<div class="pact-card"><small>오늘의 약속</small>${prep.pacts.map((p) => `<p>· ${esc(p)}</p>`).join('')}</div>` : ''}
    </div>`;
  },
});

// 준비 중 일시 정지(주차 브레이크 풀림·신호 불명)
Screens.paused = () => ({
  mount(el) {
    el.innerHTML = `<div class="screen center"><div class="hero"><div class="hero-m">${mascot('worried', 96)}</div>
      <div><h1>연습을 잠시 멈췄어요</h1><p class="sub" id="pauseReason"></p><p class="caption">다시 준비되면 하던 곳에서 이어서 해요.</p></div></div></div>`;
  },
  update() { $('#pauseReason').textContent = C.LockReason[session.lockReason] || ''; },
});

// A-7 주행 중 잠금 — 정적: 애니메이션·소리·버튼 없음
Screens.lock = () => ({
  mount(el) {
    el.innerHTML = `<div class="screen lock">
      <div class="lock-ico">${mascot('rest', 120)}</div>
      <p class="lock-tag">연습 중</p>
      <h1>화면은 잠시 쉴게요.<br>도로만 보세요.</h1>
      <p class="sub">주차하면 오늘 연습을 함께 돌아봐요.</p>
    </div>`;
  },
});

// A-8 주차 후 복기
Screens.review = () => ({
  mount(el) {
    const sum = C.summarize(session.tripStart, session.tripEnd);
    const mode = prep?.mode || 'solo';
    review = review || { learner: null, mentor: null, next: null, summary: sum, mode };
    const dist = sum?.distanceKm != null ? ` · ${sum.distanceKm.toFixed(1)}km` : '';
    el.innerHTML = `<div class="screen review">
      <div class="hero"><div class="hero-m">${mascot('happy', 88)}</div><div>
        <h1>수고했어요!</h1>
        <p class="big-stat">오늘 연습 <b>${sum ? sum.durationMin : 0}분</b>${dist}</p>
        <p class="caption">${sum?.durationFromVehicle ? `${srcChip('MOCK')} 트립 기준` : '(앱 기준 시간)'}</p>
      </div></div>
      <section><h2>오늘 어땠어요?</h2><div>${chipGroup('learner', C.CONTENT.learner, review.learner)}</div></section>
      ${mode === 'mentor' ? `<section><h2>멘토 한마디</h2><div>${chipGroup('mentor', C.CONTENT.mentor, review.mentor)}</div></section>` : ''}
      <section><h2>다음 목표 하나</h2><div>${chipGroup('next', C.CONTENT.nextGoals, review.next)}</div></section>
      <div class="actions two">
        ${btn('gradBtn', `${icon('flag', 22)} 혼자 운전 시작할래요`, 'secondary big')}
        ${btn('saveBtn', '저장하고 다음 연습', 'primary big', 'disabled')}
      </div>
    </div>`;
    rebindChipsSimple(el, 'learner', C.CONTENT.learner, () => review.learner, (v) => { review.learner = v; }, { after: () => this.update() });
    if (mode === 'mentor') rebindChipsSimple(el, 'mentor', C.CONTENT.mentor, () => review.mentor, (v) => { review.mentor = v; }, { after: () => this.update() });
    rebindChipsSimple(el, 'next', C.CONTENT.nextGoals, () => review.next, (v) => { review.next = v; }, { after: () => this.update() });
    $('#saveBtn').onclick = () => finishReview(false);
    $('#gradBtn').onclick = () => finishReview(true);
  },
  update() { $('#saveBtn').disabled = !review.next; },
});

function finishReview(graduate) {
  H.reviews.unshift({ at: Date.now(), goal: prep?.goal || null, mode: review.mode, learner: review.learner, mentor: review.mentor, next: review.next, summary: review.summary });
  H.reviews = H.reviews.slice(0, 30);
  H.reviewsSaved++; if (review.next) H.lastNextGoal = review.next;
  if (graduate) H.graduated = true;
  saveHistory(); awardBadges();
  review = null;
  session = C.onReviewDone(session);
  prep = newPrep();
  if (graduate) overlay = 'cert';
  render();
}

// ── 오버레이(내 기록·설정·수료 카드·사용법) ──────────────────
function openOverlay(name) { overlay = name; renderOverlay(); }
function closeOverlay() { overlay = null; renderOverlay(); }
function renderOverlay() {
  const root = $('#modalRoot');
  if (!overlay || session.phase === C.Phase.DRIVING_LOCK) { root.innerHTML = ''; root.classList.remove('open'); return; }
  root.classList.add('open');
  const body = { history: historyHtml, profile: profileHtml, cert: certHtml, intro: introHtml }[overlay]();
  root.innerHTML = `<div class="modal-scrim" data-close></div><div class="modal" role="dialog" aria-modal="true">
    <button type="button" class="icon-btn modal-x" data-close aria-label="닫기">${icon('close', 22)}</button>${body}</div>`;
  $$('[data-close]', root).forEach((b) => (b.onclick = () => { if (overlay === 'intro') { H.introSeen = true; saveHistory(); } closeOverlay(); }));
  bindOverlay(root);
  setTimeout(() => $('.modal', root)?.focus?.(), 0);
}
function historyHtml() {
  const lv = C.level(H); const steps = C.LEVEL_STEPS;
  const nextAt = steps[lv] ?? null; const prevAt = steps[lv - 1];
  const pct = nextAt == null ? 100 : ((H.reviewsSaved - prevAt) / (nextAt - prevAt)) * 100;
  const have = new Map(H.badges.map((b) => [b.badge, b]));
  const stds = H.pedalStds.slice(-6);
  const maxStd = Math.max(4, ...stds.map((s) => s.std));
  return `<h2 class="m-title">${icon('star', 24)} 내 기록</h2>
    <div class="level-card">
      <div class="lv-num">Lv.${lv}</div>
      <div class="lv-body"><b>${C.LEVEL_NAMES[lv - 1]}</b><small>복기 ${H.reviewsSaved}회${nextAt != null ? ` · 다음 레벨까지 ${nextAt - H.reviewsSaved}회` : ''}</small>
        <div class="holdbar"><i style="width:${pct}%"></i></div>
        <small class="muted">레벨은 이어 간 연습 횟수예요. 실력 점수가 아니에요.</small></div>
    </div>
    <h3>배지 <small>${H.badges.length}/9</small></h3>
    <div class="badge-grid">${Object.entries(C.Badges).map(([k, b]) => {
      const e = have.get(k);
      return `<div class="badge ${e ? 'on' : ''}">${badgeArt(b, !!e, 64)}<b>${b.title}${e?.mockOnly ? ' <em>(모의)</em>' : ''}</b><small>${e ? new Date(e.at).toLocaleDateString('ko-KR') : k === 'MENTOR_RETURN' ? '후속 기능' : b.desc}</small></div>`;
    }).join('')}</div>
    <h3>살살 밟기 — 내 기록 비교 <small>흔들림(작을수록 부드러움)</small></h3>
    ${stds.length ? `<div class="bars">${stds.map((s, i) => `<div class="bar"><i style="height:${(s.std / maxStd) * 100}%"></i><b>${s.std.toFixed(1)}</b><small>${i === stds.length - 1 ? '최근' : `${stds.length - 1 - i}회 전`}</small></div>`).join('')}</div>` : '<p class="muted">아직 기록이 없어요. 페달 감각 미션을 해 보면 여기에 쌓여요.</p>'}
    <h3>최근 복기</h3>
    ${H.reviews.length ? `<ul class="rev-list">${H.reviews.slice(0, 5).map((r) => `<li><span>${new Date(r.at).toLocaleDateString('ko-KR')}</span><b>${esc(r.goal || '연습')}</b><span>${r.summary ? `${r.summary.durationMin}분` : ''}</span><span class="muted">${esc(r.learner || '')}</span><span>다음: ${esc(r.next || '-')}</span></li>`).join('')}</ul>` : '<p class="muted">주차 후 복기를 저장하면 여기에 쌓여요.</p>'}`;
}
function profileHtml() {
  const aliases = ['나', '연습생', '새내기 운전자', '주말 드라이버'];
  return `<h2 class="m-title">${icon('user', 24)} 설정</h2>
    <h3>별칭 <small>이 기기에만 저장돼요</small></h3>
    <div>${chipGroup('alias', aliases, H.alias)}</div>
    <h3>화면</h3>
    <div class="set-row">${icon('sound', 22)}<span>정차 중 효과음</span><button type="button" class="switch" id="setSound" role="switch" aria-checked="${H.sound}"><span></span></button></div>
    <div class="set-row">${icon('text', 22)}<span>큰 글씨</span><button type="button" class="switch" id="setLarge" role="switch" aria-checked="${H.largeText}"><span></span></button></div>
    <h3>기록</h3>
    <button type="button" class="btn danger" id="delBtn">${icon('trash', 20)} 이 기기의 기록 모두 지우기</button>
    <p class="caption">서버·계정·위치·차대번호를 쓰지 않아요. 기록은 이 브라우저에만 있어요.</p>`;
}
function certHtml() {
  const first = H.firstAt ? new Date(H.firstAt).toLocaleDateString('ko-KR') : new Date().toLocaleDateString('ko-KR');
  return `<div class="cert">
    <div class="cert-top">${mascot('happy', 84)}<div><small>치일까봐 연습 수료 카드</small><h2>${esc(H.alias)} 님, 혼자 출발!</h2></div></div>
    <div class="cert-stats"><div><b>${H.reviewsSaved}</b><small>이어 간 연습</small></div><div><b>${H.badges.length}</b><small>모은 배지</small></div><div><b>${first}</b><small>첫 연습</small></div></div>
    <div class="cert-badges">${H.badges.map((b) => badgeArt(C.Badges[b.badge], true, 40)).join('')}</div>
    <p class="cert-note">운전 실력 인증이 아니라, 이어 온 연습의 기록이에요.<br>도로에서는 언제나 천천히, 안전하게.</p>
  </div>`;
}
function introHtml() {
  return `<h2 class="m-title">${icon('info', 24)} 이렇게 써 보세요</h2>
    <ol class="intro">
      <li><b>왼쪽은 차량 화면</b>, 오른쪽(휴대폰은 아래 버튼)은 <b>가상 차량 콘솔</b>이에요. 콘솔에서 조작하면 앱이 신호를 ‘읽어서’ 확인해요.</li>
      <li>콘솔 맨 위 <b>‘다음 조작’</b>을 따라가면 전체 흐름을 볼 수 있어요: 안전 확인 → 목표 → 워밍업 → 페달 → 가상 주차 → 약속 → 주행 중 잠금 → 주차 후 복기.</li>
      <li>워밍업은 <b>시작한 뒤 새로 한 조작</b>만 인정해요. 이미 매고 있던 벨트는 풀었다 다시 매야 해요.</li>
      <li>기어를 D로 옮기면 화면이 조용히 잠겨요. 속도 0 → 기어 P → 주차 브레이크를 걸면 복기가 열려요.</li>
    </ol>
    <p class="caption">모든 신호는 <span class="src-chip src-MOCK">모의</span>예요. 차량 에뮬레이터·실차 신호 연결은 Android 앱에서 해요.</p>
    <div class="actions two">${btn('introDemo', `${icon('console', 22)} 자동 시연 보기(약 1분)`, 'secondary big')}${btn('introOk', '직접 해 보기', 'primary big')}</div>`;
}
function bindOverlay(root) {
  if (overlay === 'profile') {
    const aliases = ['나', '연습생', '새내기 운전자', '주말 드라이버'];
    rebindChipsSimple(root, 'alias', aliases, () => H.alias, (v) => { H.alias = v; saveHistory(); renderChrome(); });
    $('#setSound').onclick = (e) => { H.sound = !H.sound; e.currentTarget.setAttribute('aria-checked', H.sound); saveHistory(); };
    $('#setLarge').onclick = (e) => { H.largeText = !H.largeText; e.currentTarget.setAttribute('aria-checked', H.largeText); saveHistory(); applyPrefs(); };
    let armed = false;
    $('#delBtn').onclick = (e) => {
      if (!armed) { armed = true; e.currentTarget.innerHTML = `${icon('trash', 20)} 한 번 더 누르면 지워져요`; return; }
      const keep = { introSeen: true }; H = { ...blankHistory(), ...keep }; saveHistory(); applyPrefs(); closeOverlay(); render();
    };
  }
  if (overlay === 'intro') {
    $('#introOk').onclick = () => { H.introSeen = true; saveHistory(); closeOverlay(); };
    $('#introDemo').onclick = () => { H.introSeen = true; saveHistory(); closeOverlay(); startDemo(); };
  }
}
function applyPrefs() { document.documentElement.classList.toggle('large', !!H.largeText); }

// ── 공통 렌더 ────────────────────────────────────────────────
let mounted = { key: null, screen: null };
function screenKey() {
  switch (session.phase) {
    case C.Phase.CHECKING: case C.Phase.READY: return 'gate';
    case C.Phase.DRIVING_LOCK: return 'lock';
    case C.Phase.REVIEW: return 'review';
    case C.Phase.PREPARING:
      if (session.lockReason) return 'paused';
      if (prep.step === 'mission') return `mission:${prep.missionIdx}`;
      return prep.step;
    default: return 'gate';
  }
}
function render() {
  const key = screenKey();
  if (key !== mounted.key) {
    mounted.screen?.unmount?.();
    const scr = Screens[key.split(':')[0]]();
    const body = $('#appBody');
    body.innerHTML = '';
    body.dataset.screen = key.split(':')[0];
    scr.mount(body);
    body.scrollTop = 0;
    mounted = { key, screen: scr };
    if (!reduceMotion && session.phase !== C.Phase.DRIVING_LOCK) { body.classList.remove('enter'); void body.offsetWidth; body.classList.add('enter'); }
    else body.classList.remove('enter');
  }
  mounted.screen.update?.();
  renderChrome();
}

const STEPS = [['goal', '목표'], ['mission', '워밍업'], ['pedal', '페달'], ['parking', '가상 주차'], ['pact', '약속'], ['go', '출발']];
let chromeKey = '';
function renderChrome() {
  const s = snapshot();
  const ph = session.phase;
  const pill = { CHECKING: '확인 중', READY: '정차 중', PREPARING: '정차 중 · 연습 준비', DRIVING_LOCK: '주행 중', REVIEW: '주차 후 복기' }[ph];
  const k = [ph, prep?.step, prep?.mode, H.alias, overlay].join('|');
  document.body.dataset.phase = ph;
  if (k !== chromeKey) {
    chromeKey = k;
    $('#phasePill').textContent = pill;
    $('#phasePill').dataset.phase = ph;
    $('#aliasLabel').textContent = H.alias;
    const st = $('#stepper');
    if (ph === C.Phase.PREPARING && prep) {
      const names = Object.fromEntries(STEPS);
      const list = stepOrder(prep).map((id) => [id, names[id]]);
      const cur = list.findIndex(([id]) => id === prep.step);
      st.innerHTML = list.map(([id, l], i) => `<span class="${i < cur ? 'd' : i === cur ? 'c' : ''}">${i < cur ? icon('check', 14) : `<i>${i + 1}</i>`}${l}</span>`).join('');
      st.hidden = false;
    } else st.hidden = true;
    renderOverlay();
  }
  // Driving view
  $('#dvSpeed').textContent = Math.round(V.speedKmh.value);
  $('#dvGear').textContent = s.gear.status === 'OK' ? s.gear.value : '–';
  $('#dvPb').classList.toggle('on', s.parkingBrakeOn.value === true);
  renderGuide(s);
}

// ── 콘솔 ─────────────────────────────────────────────────────
function setSteer(v) { v = clamp(Math.round(v / 5) * 5, -540, 540); setV('steeringDeg', v); syncConsole(); }
function setBrake(v) {
  v = clamp(v, 0, 100); setV('brakePedalPct', v); setV('brakePressed', v > 5, { onlyIfChanged: true }); syncConsole();
}
function setSwitch(id, on) { const b = $(id); b.setAttribute('aria-checked', String(on)); }
function syncConsole() {
  $$('#c-gear button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.v === V.gear.value)));
  $$('#c-turn button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.v === V.turnSignal.value)));
  setSwitch('#c-pb', V.parkingBrakeOn.value); setSwitch('#c-belt', V.driverBeltBuckled.value); setSwitch('#c-wiper', V.wiperOn.value);
  setSwitch('#c-beam', V.lowBeamOn.value); setSwitch('#c-pass', V.passengerOccupied.value); setSwitch('#c-fast', timeScale > 1);
  $('#v-belt').textContent = V.driverBeltBuckled.value ? '체결' : '미체결';
  const sp = $('#c-speed'); sp.value = V.speedKmh.value; sp.disabled = V.gear.value === 'P' || V.gear.value === 'N';
  $('#v-speed').textContent = Math.round(V.speedKmh.value);
  $('#speedNote').hidden = !sp.disabled;
  $('#c-brake').value = V.brakePedalPct.value; $('#v-brake').textContent = Math.round(V.brakePedalPct.value); $('#v-brakeP').textContent = V.brakePressed.value ? '밟음' : '뗌';
  $('#c-steer').value = V.steeringDeg.value; $('#v-steer').textContent = Math.abs(V.steeringDeg.value);
  $('#v-steerDir').textContent = V.steeringDeg.value > 0 ? '오른쪽' : V.steeringDeg.value < 0 ? '왼쪽' : '가운데';
}
function bindConsole() {
  $$('#c-gear button').forEach((b) => (b.onclick = () => {
    setV('gear', b.dataset.v, { onlyIfChanged: true });
    if (b.dataset.v === 'P' || b.dataset.v === 'N') { setV('speedKmh', 0); setV('uxRestricted', false, { onlyIfChanged: true }); }
    syncConsole(); tick();
  }));
  $$('#c-turn button').forEach((b) => (b.onclick = () => { setV('turnSignal', b.dataset.v, { onlyIfChanged: true }); syncConsole(); tick(); }));
  const sw = (id, key) => ($(id).onclick = () => { setV(key, !V[key].value); syncConsole(); tick(); });
  sw('#c-pb', 'parkingBrakeOn'); sw('#c-belt', 'driverBeltBuckled'); sw('#c-wiper', 'wiperOn'); sw('#c-beam', 'lowBeamOn'); sw('#c-pass', 'passengerOccupied');
  $('#c-belt').addEventListener('click', () => { if (V.driverBeltBuckled.value) chime('click'); });
  $('#c-fast').onclick = () => { timeScale = timeScale > 1 ? 1 : 30; syncConsole(); };
  $('#c-speed').oninput = (e) => { const v = +e.target.value; setV('speedKmh', v); setV('uxRestricted', v > 0, { onlyIfChanged: true }); syncConsole(); tick(); };
  $('#c-brake').oninput = (e) => { stopRamp(); setBrake(+e.target.value); tick(); };
  $('#c-steer').oninput = (e) => { setSteer(+e.target.value); tick(); };
  $('#c-steerCenter').onclick = () => { setSteer(0); tick(); };
  // 꾹 밟기: 누르는 동안 끝까지, 떼면 0.9초에 걸쳐 천천히
  let ramp = null; const stopRamp = () => { if (ramp) { cancelAnimationFrame(ramp); ramp = null; } };
  const hb = $('#c-brakeHold');
  hb.addEventListener('pointerdown', (e) => { e.preventDefault(); stopRamp(); const t0 = performance.now(), v0 = V.brakePedalPct.value;
    const up = (t) => { const k = Math.min(1, (t - t0) / 250); setBrake(v0 + (100 - v0) * k); if (k < 1) ramp = requestAnimationFrame(up); }; ramp = requestAnimationFrame(up); hb.classList.add('down'); });
  const release = () => { if (!hb.classList.contains('down')) return; hb.classList.remove('down'); stopRamp(); const t0 = performance.now(), v0 = V.brakePedalPct.value;
    const down = (t) => { const k = Math.min(1, (t - t0) / 900); setBrake(v0 * (1 - k)); if (k < 1) ramp = requestAnimationFrame(down); else ramp = null; }; ramp = requestAnimationFrame(down); };
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => hb.addEventListener(ev, release));
  // 예외 재현
  $('#statusRows').innerHTML = STATUS_KEYS.map((k) => `<label class="st-row"><span>${SIGNALS[k].label}</span>
    <select data-k="${k}">${Object.entries(STATUS_LABEL).map(([v, l]) => `<option value="${v}">${l}</option>`).join('')}</select></label>`).join('');
  $$('#statusRows select').forEach((sel) => (sel.onchange = () => { statusOverride[sel.dataset.k] = sel.value; tick(); }));
  $('#c-policy').onchange = (e) => { policy.allowWhenParkingBrakeUnsupported = e.target.checked; tick(); };
  let armed = false;
  $('#c-reset').onclick = (e) => {
    if (!armed) { armed = true; e.currentTarget.textContent = '한 번 더 누르면 지워져요'; return; }
    try { localStorage.removeItem(STORE_KEY); } catch (err) { /* 무시 */ }
    location.reload();
  };
  // 모바일 시트
  const fab = $('#consoleFab'), con = $('#console');
  const toggleSheet = (open) => { con.classList.toggle('open', open); fab.setAttribute('aria-expanded', String(open)); fab.classList.toggle('active', open); document.body.classList.toggle('sheet-open', open); };
  fab.onclick = () => toggleSheet(!con.classList.contains('open'));
  $('#sheetHandle').onclick = () => toggleSheet(false);
}

const GUIDE_TARGETS = ['r-gear', 'r-pb', 'r-speed', 'r-fast', 'r-belt', 'r-brake', 'r-turn', 'r-wiper', 'r-beam', 'r-steer', 'r-pass'];
let guideKey = '';
function guide(s) {
  if (booting) return ['차량 신호를 받는 중이에요…', null];
  const ph = session.phase;
  const blocked = Object.entries(statusOverride).find(([, v]) => v !== 'OK');
  if (ph === C.Phase.CHECKING) {
    const r = session.lockReason;
    if (r === 'DRIVING_RESTRICTED') return ['속도를 0으로 내려 주세요.', 'r-speed'];
    if (r === 'NOT_IN_PARK') return ['기어를 <b>P</b>로 옮겨 주세요.', 'r-gear'];
    if (r === 'PARKING_BRAKE_OFF') return ['<b>주차 브레이크</b>를 걸어 주세요.', 'r-pb'];
    return [blocked ? '‘예외 재현’에서 신호 상태를 <b>정상</b>으로 되돌리면 잠금이 풀려요.' : '기어 P와 주차 브레이크를 확인해 주세요.', blocked ? null : 'r-gear'];
  }
  if (ph === C.Phase.READY) return ['앱 화면에서 <b>[연습 시작]</b>을 눌러요.', null];
  if (ph === C.Phase.DRIVING_LOCK) {
    if (V.speedKmh.value === 0 && V.gear.value !== 'P') {
      return session.tripStart && (V.tripTimeSec.value - (session.tripStart.tripTimeSec ?? 0)) > 60
        ? ['다 달렸으면 기어를 <b>P</b>로, 주차 브레이크를 걸어요.', 'r-gear']
        : ['속도를 올려 달려 보세요. <b>×30 빨리 감기</b>를 켜면 트립이 빨리 쌓여요.', 'r-speed'];
    }
    if (V.speedKmh.value > 0) return ['달리는 중이에요. 앱 화면은 조용히 쉬고 있어요. 멈추려면 속도를 0으로.', timeScale > 1 ? 'r-speed' : 'r-fast'];
    return [V.gear.value === 'P' ? '<b>주차 브레이크</b>를 걸면 복기가 열려요.' : '기어를 <b>P</b>로 옮겨요.', V.gear.value === 'P' ? 'r-pb' : 'r-gear'];
  }
  if (ph === C.Phase.REVIEW) return ['앱에서 오늘을 돌아보고 <b>다음 목표</b>를 골라요.', null];
  if (ph === C.Phase.PREPARING) {
    if (session.lockReason === 'PARKING_BRAKE_OFF') return ['<b>주차 브레이크</b>를 다시 걸면 이어서 해요.', 'r-pb'];
    if (session.lockReason) return ['신호 상태를 정상으로 되돌려 주세요.', null];
    switch (prep.step) {
      case 'goal': return ['앱에서 목표를 골라요. <b>조수석에 사람 있음</b>을 켜면 멘토 모드가 강조돼요.', 'r-pass'];
      case 'mission': {
        const id = judgeState?.judge.id; const p = judgeState?.progress;
        if (p?.kind === 'done') return ['잘했어요! 앱에서 <b>[다음]</b>을 눌러요.', null];
        if (p?.kind === 'unavailable') return ['신호를 읽을 수 없어 앱이 건너뛰기를 안내해요.', null];
        if (id === 'SEAT_BELT') return [V.driverBeltBuckled.value ? '<b>안전벨트</b>를 한 번 풀었다가 다시 매요.' : '이제 <b>안전벨트</b>를 매요.', 'r-belt'];
        if (id === 'BRAKE_PRESS_RELEASE') return [p?.step === 2 ? '이제 <b>꾹 밟기</b>에서 손을 떼요(천천히 떼짐).' : '<b>꾹 밟기</b>를 누르고 있거나 페달을 끝까지 밀어요.', 'r-brake'];
        if (id === 'TURN_SIGNAL') return [['방향지시등 <b>◀ 왼쪽</b>', '방향지시등 <b>오른쪽 ▶</b>', '방향지시등 <b>끔</b>'][(p?.step || 1) - 1], 'r-turn'];
        if (id === 'WIPER') return ['<b>와이퍼</b>를 켜 보세요.', 'r-wiper'];
        if (id === 'LOW_BEAM') return ['<b>전조등</b>을 켜 보세요.', 'r-beam'];
        if (id === 'STEERING_CENTER') return ['<b>핸들</b>을 가운데(±5°)로 맞추고 2초.', 'r-steer'];
        return ['', null];
      }
      case 'pedal': return judgeState?.progress?.kind === 'done' ? ['부드러웠어요! 앱에서 다음으로.', null] : ['<b>브레이크 페달</b> 슬라이더를 20~35%에 두고 2초.', 'r-brake'];
      case 'parking': return ['<b>핸들</b>을 돌리면 화면 속 바퀴가 따라 꺾여요. 앱의 [후진]을 누르고 있어요.', 'r-steer'];
      case 'pact': return ['앱에서 약속을 1~3개 골라요.', null];
      case 'go': return V.parkingBrakeOn.value ? ['<b>주차 브레이크</b>를 풀고 기어를 D로 옮겨 봐요.', 'r-pb'] : ['기어를 <b>D</b>로 옮겨 보세요. 화면이 쉬어요.', 'r-gear'];
      default: return ['', null];
    }
  }
  return ['', null];
}
function renderGuide(s) {
  const [text, target] = guide(s);
  const k = text + target;
  if (k === guideKey) return; guideKey = k;
  $('#guideText').innerHTML = text;
  GUIDE_TARGETS.forEach((id) => $('#' + id)?.classList.toggle('guide-target', id === target));
  $('#consoleFab').classList.toggle('ping', !!target);
}

// ── 메인 루프 ────────────────────────────────────────────────
let lastT = performance.now();
let lastSec = -1;
function tick() {
  const t = performance.now(); const dt = Math.min(0.5, (t - lastT) / 1000); lastT = t;
  simMs += dt * 1000 * timeScale;
  // 트립(모의): 기어가 P가 아니면 시간, 속도가 있으면 거리
  if (V.gear.value !== 'P') {
    tripSecF += dt * timeScale;
    const sec = Math.floor(tripSecF);
    if (sec !== lastSec) { setV('tripTimeSec', sec); lastSec = sec; }
  }
  if (V.speedKmh.value > 0) setV('tripDistanceKm', +(V.tripDistanceKm.value + (V.speedKmh.value * dt * timeScale) / 3600).toFixed(4));
  const snap = snapshot();
  const gate = C.evaluateGate(snap, policy); lastGate = gate;
  const before = session;
  session = C.onGate(session, gate, snap, simMs);
  if (before.phase !== session.phase) onPhaseChange(before.phase, session.phase);
  else if (before.phase === C.Phase.PREPARING && before.lockReason && !session.lockReason && judgeState) judgeState.judge.start(snapshot(), now()); // 재개: 새 관측부터
  if (session.phase === C.Phase.PREPARING && !session.lockReason && judgeState && (prep.step === 'mission' || prep.step === 'pedal')) {
    if (judgeState.progress?.kind !== 'done') {
      const p = judgeState.judge.update(snap, now());
      judgeState.progress = p;
      if (p.kind === 'done' && !judgeState.recorded) { judgeState.recorded = true; recordMission(judgeState.judge.id, p); }
    }
  }
  render();
  flushToasts();
}
function onPhaseChange(from, to) {
  if (to === C.Phase.DRIVING_LOCK) { overlay = null; $('#toasts').innerHTML = ''; toastBusy = false; review = null; }
  if (to === C.Phase.REVIEW) review = null;
  if (from === C.Phase.PREPARING && to === C.Phase.DRIVING_LOCK) judgeState = null;
}
function flashNote(t) {
  const el = document.createElement('div'); el.className = 'toast note'; el.textContent = t; $('#toasts').appendChild(el);
  setTimeout(() => el.remove(), 1600);
}


// ── 자동 시연(심사·영상용) ───────────────────────────────────
// 콘솔·앱 버튼을 실제로 눌러 가며 전체 흐름을 약 1분에 보여 준다. 사람이 콘솔을 만지면 바로 멈춘다.
const demo = { running: false, token: 0 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function demoBanner(text) {
  let el = $('#demoBanner');
  if (!el) { el = document.createElement('div'); el.id = 'demoBanner'; el.className = 'demo-banner'; document.body.appendChild(el); }
  el.innerHTML = text ? `<b>자동 시연</b><span>${text}</span><button type="button" class="mini" id="demoStop">멈추기</button>` : '';
  el.hidden = !text;
  $('#demoStop') && ($('#demoStop').onclick = () => stopDemo('멈췄어요'));
}
function stopDemo(msg) {
  if (!demo.running) return;
  demo.running = false; demo.token++;
  const sc = mounted.screen; if (sc && 'move' in sc) sc.move = 0;
  $('#demoBtn').textContent = '자동 시연';
  demoBanner('');
  if (msg) flashNote(`자동 시연 — ${msg}`);
}
async function startDemo() {
  if (demo.running) return;
  if (session.phase !== C.Phase.CHECKING && session.phase !== C.Phase.READY) { location.href = `${location.pathname}?demo=1`; return; }
  demo.running = true; const tk = ++demo.token;
  $('#demoBtn').textContent = '시연 멈추기';
  const alive = () => demo.running && demo.token === tk;
  const step = async (label, ms = 700) => { if (!alive()) throw new Error('stop'); demoBanner(label); await sleep(ms); if (!alive()) throw new Error('stop'); };
  const click = (sel) => { const el = $(sel); if (el && !el.disabled) el.click(); };
  const ramp = async (fn, from, to, ms) => { const t0 = performance.now(); for (;;) { const k = Math.min(1, (performance.now() - t0) / ms); fn(from + (to - from) * k); tick(); if (k >= 1) break; await sleep(40); if (!alive()) throw new Error('stop'); } };
  const waitFor = async (cond, max = 15000) => { const t0 = performance.now(); while (!cond()) { if (performance.now() - t0 > max || !alive()) throw new Error('stop'); await sleep(80); } };
  const setGear = (g) => click(`#c-gear button[data-v="${g}"]`);
  try {
    syncConsole();
    await waitFor(() => !booting);
    await step('차가 기어 P·주차 브레이크·주행 제한을 직접 확인해요', 1600);
    click('#startBtn');
    await step('조수석에 멘토가 앉으면 차가 알아봐요', 900); click('#c-pass');
    await step('오늘 걱정되는 것: 주차', 900); click('.chips[data-group="goal"] .chip[data-v="주차"]');
    await step('멘토와 함께 시작', 800); click('#mentorBtn');
    await step('이미 매고 있던 벨트는 인정하지 않아요 → 풀었다가', 1300); click('#c-belt');
    await step('다시 ‘딸깍’', 800); click('#c-belt');
    await step('차가 확인했어요', 1500); click('#nextBtn');
    await step('브레이크를 끝까지 꾹', 600); await ramp(setBrake, 0, 100, 500);
    await step('천천히 떼기', 400); await ramp(setBrake, 100, 0, 900);
    await step('끝까지 꾹, 그리고 천천히 — 확인!', 1400); click('#nextBtn');
    await step('방향지시등 왼쪽', 700); click('#c-turn button[data-v="LEFT"]');
    await step('오른쪽', 700); click('#c-turn button[data-v="RIGHT"]');
    await step('끄기', 700); click('#c-turn button[data-v="NONE"]');
    await step('깜빡이 완료', 1300); click('#nextBtn');
    await step('선택 미션은 오늘은 넘어가요(5분 안에 핵심만)', 1300); click('#skipAllBtn');
    await waitFor(() => prep?.step === 'parking');
    const sc = () => mounted.screen;
    await step('내 차 크기로 하는 가상 주차 — 기준선까지 살짝 앞으로', 900);
    setSteer(0); sc().move = 1; sc().dir = 1; await sleep(700); sc().move = 0;
    await step('뒷바퀴가 옆 차 범퍼를 지났어요 → 핸들을 끝까지', 900); await ramp(setSteer, 0, 540, 700);
    await step('후진하면 화면 속 바퀴가 핸들을 따라 꺾여요', 200);
    sc().dir = -1; sc().move = -1;
    await waitFor(() => Math.abs(Math.sin(sc().pose.heading)) < 0.02 || sc().done || sc().bump > 0);
    sc().move = 0;
    await step('차가 섰어요 → 핸들을 풀고 쭉 후진', 700); await ramp(setSteer, 540, 0, 600);
    sc().move = -1; await waitFor(() => sc().done || sc().bump > 0, 9000); sc().move = 0;
    await step(sc().done ? '칸 안에 들어왔어요!' : '거의 다 왔어요', 1600); click('#pkNext');
    await step('페달 감각: 초록 칸 안에서 2초', 500); await ramp(setBrake, 0, 27, 700);
    await waitFor(() => judgeState?.progress?.kind === 'done', 5000); await step('부드러웠어요', 1300); await ramp(setBrake, 27, 0, 300); click('#nextBtn');
    await step('멘토와 오늘의 약속', 700);
    $$('.chips[data-group="pact"] .chip')[0]?.click(); await sleep(400); $$('.chips[data-group="pact"] .chip')[3]?.click();
    await step('둘 다 약속했어요', 900); click('#pactBtn');
    await step('출발 준비 완료 — 주차 브레이크를 풀고 D', 1600); click('#c-pb'); await sleep(400); setGear('D');
    await step('화면은 조용히 잠겨요. 소리·애니메이션·버튼 없음', 900);
    if (timeScale === 1) click('#c-fast');
    await ramp((v) => { setV('speedKmh', Math.round(v)); setV('uxRestricted', v > 0, { onlyIfChanged: true }); syncConsole(); }, 0, 38, 900);
    await step('(시간 빨리 감기 ×30으로 14분 주행을 몇 초에)', 4200);
    await ramp((v) => { setV('speedKmh', Math.round(v)); setV('uxRestricted', v > 0, { onlyIfChanged: true }); syncConsole(); }, 38, 0, 700);
    await step('주차: 기어 P + 주차 브레이크', 600); setGear('P'); await sleep(300); click('#c-pb');
    if (timeScale > 1) click('#c-fast');
    await waitFor(() => session.phase === C.Phase.REVIEW);
    await step('트립으로 오늘 연습을 돌아봐요', 1500);
    $$('.chips[data-group="learner"] .chip')[0]?.click(); await step('오늘 어땠어요?', 700);
    $$('.chips[data-group="mentor"] .chip')[1]?.click(); await step('멘토 한마디', 700);
    $$('.chips[data-group="next"] .chip')[0]?.click(); await step('다음 목표 하나', 900);
    click('#saveBtn');
    await step('저장 — 다음 연습은 ‘지난번 목표’에서 이어져요', 2200);
    stopDemo('끝! 이제 직접 해 보세요');
  } catch (e) { if (demo.token === tk) stopDemo('멈췄어요'); }
}
// 사람이 콘솔을 직접 만지면 시연을 멈춘다
$('#console').addEventListener('pointerdown', (e) => { if (demo.running && e.isTrusted) stopDemo('콘솔을 만져서 멈췄어요'); }, true);

// ── 시작 ─────────────────────────────────────────────────────
$('#brandMascot').innerHTML = mascot('calm', 40);
$('#topMascot').innerHTML = mascot('calm', 30);
$('#dvArt').innerHTML = drivingViewArt();
$('#historyBtn .ib-ico').innerHTML = icon('star', 22);
$('#profileBtn .ib-ico').innerHTML = icon('user', 22);
$('#consoleFab .fab-ico').innerHTML = icon('console', 22);
$('#historyBtn').onclick = () => openOverlay('history');
$('#profileBtn').onclick = () => openOverlay('profile');
$('#helpBtn').onclick = () => openOverlay('intro');
$('#demoBtn').onclick = () => (demo.running ? stopDemo('멈췄어요') : startDemo());
addEventListener('keydown', (e) => { if (e.key === 'Escape' && overlay) closeOverlay(); });
applyPrefs();
bindConsole(); syncConsole();
render();
setInterval(tick, 100);
if (new URLSearchParams(location.search).has('demo')) setTimeout(startDemo, 200);
else if (!H.introSeen) setTimeout(() => openOverlay('intro'), 300);
