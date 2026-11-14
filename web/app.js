// 치일까봐 웹 프로토타입 — 화면·모의 차량·흐름
import * as C from './core.js';
import { mascot, icon, badgeArt, drivingViewArt } from './art.js';
import * as SIM from './sim.js';
import * as VOICE from './voice.js';
import * as THEME from './theme.js';
import * as OB from './onboarding.js';
import * as RP from './replay.js';
import * as LEARN from './learn.js';
import { aboutHtml } from './about.js';
import { holdPedal, bindBrakeSlider } from './pedal-control.js';
import { KeyboardDrive, gearPresentation, stopRequestState } from './drive-controls.js';

// 캔버스에서 쓰는 색은 리터럴을 적지 않고 CSS 토큰에서 읽는다(테마를 따라가도록).
// styles.css 의 :root / :root[data-theme="light"] 가 유일한 색 정의 자리다.
const T = THEME.c;
const TA = THEME.alpha;

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
  alias: '나', mode: 'practice', simReports: [], sound: true, largeText: false, introSeen: false,
  missionResults: [], parkingSimCompleted: 0, pactsMade: 0, reviewsSaved: 0, graduated: false, mentorSessions: 0,
  badges: [], reviews: [], lastNextGoal: null, pedalStds: [], firstAt: null,
  // F12 음성 길잡이 — null이면 모드에 따름(혼자=켬 / 함께=끔). 끈 상태가 기본값으로 저장된다.
  voiceOn: null, speedLimitVoice: false, replays: [],
});
function loadHistory() {
  try { const raw = localStorage.getItem(STORE_KEY); if (raw) return { ...blankHistory(), ...JSON.parse(raw) }; } catch (e) { /* 저장소 없음 */ }
  return blankHistory();
}
function saveHistory() { try { localStorage.setItem(STORE_KEY, JSON.stringify(H)); } catch (e) { /* 무시 */ } }
let H = loadHistory();
// 모드: practice(연습 — 공개 SDK 범위) / eval(평가 — 가상) / coach(강습 — 제조사 협업 가정 · 가상)
let appMode = ['practice', 'eval', 'coach'].includes(H.mode) ? H.mode : 'practice';
let sim = null; let simReport = null;
let simManeuver = 'STRAIGHT';
function setSimManeuver(dir) {
  simManeuver = ['LEFT', 'RIGHT', 'STRAIGHT'].includes(dir) ? dir : 'STRAIGHT';
  if (sim) sim.input.maneuver = simManeuver;
  $$('#c-maneuver button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.v === simManeuver)));
}

// ── F12 주행 중 음성 길잡이 · F13 리플레이 ─────────────────────
// 화면은 건드리지 않는다. 음성이 나와도 잠금 화면은 그대로다(CLAUDE.md §5-4).
const speaker = new VOICE.Speaker();
let voice = null;          // 주행 1회당 하나
let watcher = null;        // 차량 신호에서 상황을 알아내는 감시기(연습 모드 = 실제 앱이 하는 일)
let replayRows = [];       // 이번 주행의 리플레이 항목
let replayShown = false;   // 복기 화면 앞에 리플레이를 한 번 보여 줬는지
let nextVoiceEventId = 0;
let mockNavUntil = 0;
let seenSimEvents = 0;
let track = new RP.Track(); // 이번 주행의 속도·페달·가상 위치 시간표(메모리에만 — 저장하지 않는다)     // 평가·강습 모드에서 이미 처리한 sim 기록 수
// 시계가 둘이다. 섞으면 급출발이 '완만한 가속'으로 보인다.
//  voiceSec     = 빨리 감기(×30)를 반영한 '주행 경과 시간'. 음성 빈도 규칙·리플레이 시각에 쓴다.
//                 (프로토타입에서 ×30을 켜면 45초 간격도 같이 압축돼 시연 안에서 규칙이 보인다)
//  voiceRealSec = 실제 흐른 시간. **속도 변화율(급출발·급제동) 계산에만** 쓴다.
//                 차량 신호는 실시간으로 들어오므로 0.7초에 0→38km/h는 그냥 급출발이다.
let voiceSec = 0;
let voiceRealSec = 0;

function voicePrefOn(mode = prep?.mode || 'solo') { return H.voiceOn == null ? mode !== 'mentor' : !!H.voiceOn; }
const voiceCap = () => H.voiceCap === 3 ? 3 : VOICE.RULES.maxPerDrive;

/** 주행에 들어갈 때 음성·리플레이를 새로 시작한다 */
function startVoiceDrive(mode) {
  voiceSec = 0; voiceRealSec = 0; seenSimEvents = 0; replayRows = []; replayShown = false; mockNavUntil = 0;
  track = new RP.Track(); RP.closePip();
  watcher = new VOICE.SignalWatcher();
  // 연습 모드에서는 정차 중에 프레임 루프를 멈춰 두므로(성능) 주행에 들어갈 때 다시 켠다
  startSimLoop();
  voice = new VOICE.VoiceGuide({
    // 평가·강습은 목표 선택 화면을 건너뛰고 가상 종합 연습을 한다.
    goal: appMode === 'practice' ? prep?.goal || null : '그냥 몸풀기',
    mode,
    enabled: voicePrefOn(mode),
    speedLimitOn: !!H.speedLimitVoice,
    maxPerDrive: voiceCap(),
  });
  renderVoiceMeter();
}

/** 주행이 끝나면 남은 발화를 멈추고 리플레이를 만든다 */
function endVoiceDrive() {
  voice?.dropped('CANCELLED');
  if (voice) voice.window = null;
  speaker.stop();
  mockNavUntil = 0;
  replayRows = VOICE.buildReplay(replayRows, voice, 'MOCK');
  if (replayRows.length) {
    H.replays = [{ at: Date.now(), mode: appMode, goal: prep?.goal || null, rows: replayRows.slice(0, 40) }, ...(H.replays || [])].slice(0, 10);
    saveHistory();
  }
  renderVoiceMeter();
}

/** 발화 문구와 사건 종류가 달라도 같은 ID로 복기에 연결한다. */
function recordVoiceEvent(event) {
  // 시뮬레이터 리포트도 원본 사건을 읽으므로 같은 ID를 사용한다.
  event.eventId = ++nextVoiceEventId;
  replayRows.push(event);
  return event.eventId;
}

async function playCoaching(guide, utterance) {
  if (performance.now() < mockNavUntil) {
    guide.dropped('AUDIO_FOCUS');
  } else {
    const result = await speaker.speak(utterance.line);
    if (voice !== guide || guide.pending !== utterance) return;
    if (result === 'spoken') {
      guide.committed(utterance);
      if (utterance.code === 'STEADY') {
        replayRows.push({ eventId: utterance.eventId, t: utterance.atSec, code: 'STEADY',
          title: VOICE.TITLES.STEADY, kmh: utterance.kmh, sensor: null, advice: '', good: true });
      }
    } else {
      guide.dropped(speaker.lastError || 'ERROR');
    }
  }
  renderVoiceMeter();
}

/** 왼쪽 콘솔의 음성 계기 — 앱 화면이 아니라 프로토타입 계측 영역이다 */
function renderVoiceMeter() {
  const box = $('#voiceMeter'); if (!box) return;
  const on = voice ? voice.enabled : voicePrefOn(prep?.mode || 'solo');
  const st = speaker.state();
  const spoken = voice ? voice.log : [];
  const skipped = voice ? voice.skipped.slice(-4).reverse() : [];
  box.innerHTML = `<div class="vm-head"><b>${on ? '음성 켬' : '음성 끔'}</b>
      <span class="vm-count">${voice ? `${voice.spokenCount}/${voice.cap}회` : `최대 ${voiceCap()}회`}</span></div>
    <p class="vm-state ${st.ok ? 'ok' : 'warn'}">${esc(st.why)}</p>
    ${spoken.length ? `<ul class="vm-log">${spoken.slice().reverse().map((x) => `<li><span>${fmtT(x.atSec)}</span>${esc(x.line)}</li>`).join('')}</ul>` : '<p class="vm-empty">아직 말한 것이 없어요.</p>'}
    ${skipped.length ? `<details class="vm-skip"><summary>말하지 않은 이유 ${voice.skipped.length}건</summary><ul>${skipped.map((x) => `<li><span>${fmtT(x.atSec)}</span>${esc(VOICE.TITLES[x.code] || x.code)} — ${SKIP_WHY[x.why] || x.why}</li>`).join('')}</ul></details>` : ''}`;
}
const SKIP_WHY = {
  OFF: '음성이 꺼져 있어요', NOT_THIS_GOAL: '오늘 목표가 아니에요', DRIVE_CAP: '이번 주행 횟수를 다 썼어요',
  TOO_SOON: '45초가 지나지 않았어요', SAME_CAP: '같은 지적을 이미 2번 했어요', ONCE_ONLY: '주행당 1번만 해요',
  TOO_LONG: '문장이 너무 길어요', NO_LINE: '말할 문장이 없어요', LOWER_PRIORITY: '더 급한 안내를 우선했어요',
  AUDIO_FOCUS: '내비·통화에 양보했어요(발화 버림)', BUSY: '다른 소리가 나는 중이었어요', UNAVAILABLE: '브라우저가 소리를 막았어요',
  CANCELLED: '발화가 취소됐어요', TIMEOUT: '재생 완료를 확인하지 못했어요', ERROR: '음성을 재생하지 못했어요',
};

/** 브라우저 자동 재생 정책: 첫 조작 때 음성을 깨운다 */
addEventListener('pointerdown', () => { speaker.unlock(); renderVoiceMeter(); }, { once: true });
addEventListener('keydown', () => { speaker.unlock(); renderVoiceMeter(); }, { once: true });
speaker.onState = () => renderVoiceMeter();
let simSettings = { ...SIM.PRESETS.growing, preset: 'growing' };

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
  accelPedalPct: { label: '가속 페달', init: 0 },
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
let obStep = 0;     // 온보딩 단계
let wikiPage = 0;   // 운전 노트 챕터
// 온보딩 내용이 바뀌면 숫자를 올린다 → 예전에 닫은 사람에게도 한 번 더 뜬다(11/12 회의: '들어가자마자 안 뜨더라')
const INTRO_V = 3; // 3: C04 온보딩 문구 개편(2026-11-13) — 이전에 본 사람에게도 한 번 더 보여 준다
let judgeState = null; // { judge, progress, doneAt }
let lastGate = null;

function newPrep() {
  return {
    step: appMode === 'practice' ? 'goal' : 'simSetup', goal: null, mode: null, missions: [], missionIdx: 0,
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
  el.className = 'toast timed'; el.style.setProperty('--life', '2s');
  el.innerHTML = `${badgeArt(meta, true, 48)}<div><small>배지</small><b>${meta.title}${b.mockOnly ? ' <em>(모의)</em>' : ''}</b></div>`;
  $('#toasts').appendChild(el); toastBusy = true; chime('badge');
  setTimeout(() => { el.classList.add('out'); setTimeout(() => { el.remove(); toastBusy = false; }, 250); }, 2000);
}

// ── 화면 공통 조각 ───────────────────────────────────────────
const srcChip = (src = 'MOCK') => `<span class="src-chip src-${src}">${C.SourceLabel[src]}</span>`;
const fmtT = (sec) => `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`;
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
      <h2 class="how-h">어떻게 연습하세요?</h2>
      <div class="how-two">
        <button type="button" class="how" id="soloBtn" disabled>
          ${icon('sound', 26)}<b>혼자 연습</b>
          <small>주행 중에는 <b>음성 길잡이</b>가 옆자리 역할을 해요. 필요할 때만 한마디, 최대 ${voiceCap()}회.</small>
        </button>
        <button type="button" class="how" id="mentorBtn" disabled>
          ${icon('mentor', 26)}<b>함께 연습</b>
          <small>출발 전에 <b>약속 카드</b>를 맞춰요. 음성 길잡이는 기본으로 꺼져 있고, 설정에서 켤 수 있어요.</small>
        </button>
      </div>
      <p class="caption" id="soloNote">어느 쪽이든 워밍업 3개 + 원하면 선택 미션 · 약 5분. 실제 도로 연습은 처음엔 동승자와 함께 하시면 더 안전해요.</p>
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
  /** keepFocus: 다시 그린 뒤 방금 고른 칩으로 포커스를 되돌린다(키보드·조그 다이얼 조작에서 포커스가 날아가지 않게) */
  const redraw = (keepFocus = null) => {
    box.innerHTML = chipGroup(group, options, getSel(), opts);
    bindChips(root, group, (v) => { setSel(v); redraw(v); opts.after?.(); }, { ...opts, selected: getSel });
    if (keepFocus != null) $(`.chips[data-group="${group}"] .chip[data-v="${cssEsc(keepFocus)}"]`, root)?.focus();
  };
  redraw();
}
/** 선택자에 쓸 값 이스케이프(칩 문구에 따옴표·공백이 들어간다) */
const cssEsc = (v) => (window.CSS?.escape ? CSS.escape(v) : String(v).replace(/["\\]/g, '\\$&'));

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
  if (appMode !== 'practice') return ['simSetup'];
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
        ${meta.optional ? '<span class="tag soft">선택</span>' : ''}${srcChip('MOCK')}
        ${LEARN.HELP[j.id] ? `<button type="button" class="icon-btn m-info" id="mInfo" aria-label="${j.title} 도움말 — 단계와 O/X 예시">${infoIco}도움말</button>` : ''}</div>
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
    $('#mInfo')?.addEventListener('click', () => openOverlay('help'));
  },
  update() {
    const p = judgeState.progress; if (!p) return;
    const j = judgeState.judge; const meta = C.MISSION_META[j.id];
    // 차가 읽은 값
    const s = snapshot(); const sig = s[meta.signal];
    const live = liveReadout(j.id, sig, s); const ml = $('#mLive');
    if (ml.dataset.h !== live) { ml.innerHTML = live; ml.dataset.h = live; }
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
      const k0 = ({ KeyW: 'ArrowUp', KeyS: 'ArrowDown', KeyA: 'ArrowLeft', KeyD: 'ArrowRight' })[e.code];
      if (k0) { const ev = e; e = { key: k0, type: ev.type, preventDefault: () => ev.preventDefault() }; }
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
    if (this.done) { m = '칸 안에 들어왔어요'; cls = 'ok'; }
    else if (this.bump > 0) { m = '옆 차에 닿을 것 같아요. 반대로 조금 움직여 볼까요?'; cls = 'warn'; }
    else {
      const ra = this.pose; const st = this.sim.parkState(this.pose, this.slot);
      const nearRef = Math.abs(ra.x - this.refX) < 0.35 && Math.abs(Math.sin(ra.heading)) > 0.9;
      if (nearRef && Math.abs(wheel) < 30) { m = '지금 핸들을 오른쪽 끝까지 돌리고 후진해요'; cls = 'key'; }
      else if (Math.abs(Math.sin(ra.heading)) < 0.12 && Math.abs(wheel) > 8 && st.dx < 1.2 && ra.y < 1.4) { m = '차가 똑바로 섰어요. 핸들을 가운데로 풀고 쭉 후진'; cls = 'key'; }
      else if (st.dx < 1.2 && st.dy < 2.5) m = st.headingErr > 6 ? `거의 다 왔어요. 차 방향을 ${st.headingErr.toFixed(0)}°만 더 세워 볼까요?` : st.dy > 0.35 ? '방향 좋아요. 조금 더 천천히 후진' : '좌우를 조금만 맞춰 볼까요?';
    }
    if (msg.dataset.m !== m) { msg.textContent = m; msg.className = `pk-msg ${cls}`; msg.dataset.m = m; }
  },
  draw() {
    const { ctx, cv, sim, slot } = this; if (!ctx || !cv.width) return;
    const W = cv.width, S = W / 18; const X = (x) => (x + 9) * S, Y = (y) => (y - WORLD_TOP) * S;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.fillStyle = T('--pk-bg'); ctx.fillRect(0, 0, W, cv.height);
    // 통로
    ctx.fillStyle = T('--pk-curb'); ctx.fillRect(0, 0, W, Y(-2.5));
    ctx.strokeStyle = T('--pk-dash'); ctx.setLineDash([S * 0.8, S * 0.6]); ctx.lineWidth = S * 0.08;
    ctx.beginPath(); ctx.moveTo(0, Y(-7.2)); ctx.lineTo(W, Y(-7.2)); ctx.stroke(); ctx.setLineDash([]);
    // 주차선
    ctx.strokeStyle = T('--pk-line'); ctx.lineWidth = S * 0.1;
    for (const x of [-6.25, -3.75, -1.25, 1.25, 3.75, 6.25]) { ctx.beginPath(); ctx.moveTo(X(x), Y(-2.5)); ctx.lineTo(X(x), Y(2.5)); ctx.stroke(); }
    ctx.beginPath(); ctx.moveTo(X(-6.25), Y(2.5)); ctx.lineTo(X(6.25), Y(2.5)); ctx.stroke();
    // 목표 칸
    const parked = this.done;
    ctx.fillStyle = parked ? TA('--success', 0.28) : TA('--primary', 0.16);
    ctx.fillRect(X(slot.cx - 1.25) + 2, Y(-2.5), (2.5 * S) - 4, 5 * S);
    ctx.strokeStyle = parked ? T('--success') : T('--primary'); ctx.setLineDash([S * 0.25, S * 0.2]); ctx.lineWidth = S * 0.06;
    ctx.strokeRect(X(slot.cx - 1.1), Y(-2.35), 2.2 * S, 4.7 * S); ctx.setLineDash([]);
    ctx.fillStyle = parked ? T('--success-text') : T('--primary-text'); ctx.font = `700 ${S * 0.55}px Pretendard Variable, sans-serif`; ctx.textAlign = 'center';
    ctx.fillText(parked ? '도착' : '여기', X(slot.cx), Y(0.2));
    // 옆 차
    for (const n of this.neighbors) drawCar(ctx, S, X, Y, n.poly, { body: this.bump > 0 && Math.abs(n.cx) === 2.5 ? T('--warn') : T('--car-npc'), glass: T('--car-glass-npc') });
    // 기준선(뒷바퀴가 여기 오면 핸들 끝까지)
    ctx.strokeStyle = T('--yellow'); ctx.lineWidth = S * 0.07; ctx.setLineDash([S * 0.3, S * 0.2]);
    ctx.beginPath(); ctx.moveTo(X(this.refX), Y(-8.6)); ctx.lineTo(X(this.refX), Y(-2.6)); ctx.stroke(); ctx.setLineDash([]);
    ctx.fillStyle = T('--yellow'); ctx.font = `700 ${S * 0.42}px Pretendard Variable, sans-serif`; ctx.fillText('기준선', X(this.refX), Y(-8.8));
    // 궤적 미리보기(뒤축 중심)
    if (!parked) {
      let p = { ...this.pose }; const dir = this.dir || -1; const deg = this.steering();
      ctx.strokeStyle = dir < 0 ? TA('--yellow', 0.75) : TA('--primary-text', 0.75); ctx.lineWidth = S * 0.06; ctx.setLineDash([S * 0.12, S * 0.18]);
      ctx.beginPath(); ctx.moveTo(X(p.x), Y(p.y));
      for (let i = 0; i < 40; i++) { p = sim.step(p, deg, dir, 0.1); ctx.lineTo(X(p.x), Y(p.y)); }
      ctx.stroke(); ctx.setLineDash([]);
      ctx.globalAlpha = 0.18; drawCar(ctx, S, X, Y, sim.corners(p), { body: T('--primary-text'), glass: T('--primary-text'), outlineOnly: true }); ctx.globalAlpha = 1;
    }
    // 내 차
    drawMyCar(ctx, S, X, Y, sim, this.pose, this.steering(), parked);
    // 축척
    ctx.fillStyle = T('--pk-note'); ctx.font = `500 ${S * 0.36}px Pretendard Variable, sans-serif`; ctx.textAlign = 'left';
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
  const tw = 0.24 * S, tl = 0.68 * S; ctx.fillStyle = T('--car-tyre');
  const wheelAt = (x, y, ang) => { ctx.save(); ctx.translate(x, y); ctx.rotate(ang); ctx.fillRect(-tw / 2, -tl / 2, tw, tl); ctx.restore(); };
  const fy = -wb / 2, ry = wb / 2, wx = Wd / 2 - tw * 0.2;
  wheelAt(-wx, fy, wheel); wheelAt(wx, fy, wheel); wheelAt(-wx, ry, 0); wheelAt(wx, ry, 0);
  // 차체
  const r = 0.35 * S;
  ctx.fillStyle = parked ? T('--success') : T('--primary');
  roundRect(ctx, -Wd / 2, -L / 2, Wd, L, r); ctx.fill();
  ctx.fillStyle = 'rgba(255,255,255,.12)'; // 하이라이트: 두 테마 모두 흰 반투명 roundRect(ctx, -Wd / 2 + 0.12 * S, -L / 2 + 0.12 * S, Wd - 0.24 * S, L * 0.18, r * 0.7); ctx.fill();
  // 유리
  ctx.fillStyle = T('--car-glass'); roundRect(ctx, -Wd / 2 + 0.2 * S, -L / 2 + L * 0.24, Wd - 0.4 * S, L * 0.16, 0.15 * S); ctx.fill();
  roundRect(ctx, -Wd / 2 + 0.25 * S, L / 2 - L * 0.22, Wd - 0.5 * S, L * 0.1, 0.12 * S); ctx.fill();
  // 지붕
  ctx.fillStyle = parked ? T('--success-2') : T('--car-glass-2'); roundRect(ctx, -Wd / 2 + 0.22 * S, -L / 2 + L * 0.41, Wd - 0.44 * S, L * 0.34, 0.2 * S); ctx.fill();
  // 사이드미러
  ctx.fillStyle = parked ? T('--success') : T('--primary'); ctx.fillRect(-Wd / 2 - 0.16 * S, -L / 2 + L * 0.3, 0.18 * S, 0.12 * S); ctx.fillRect(Wd / 2 - 0.02 * S, -L / 2 + L * 0.3, 0.18 * S, 0.12 * S);
  // 전조등·후미등
  ctx.fillStyle = T('--car-head'); ctx.fillRect(-Wd / 2 + 0.15 * S, -L / 2 + 0.02 * S, 0.4 * S, 0.1 * S); ctx.fillRect(Wd / 2 - 0.55 * S, -L / 2 + 0.02 * S, 0.4 * S, 0.1 * S);
  ctx.fillStyle = T('--car-tail'); ctx.fillRect(-Wd / 2 + 0.15 * S, L / 2 - 0.12 * S, 0.4 * S, 0.1 * S); ctx.fillRect(Wd / 2 - 0.55 * S, L / 2 - 0.12 * S, 0.4 * S, 0.1 * S);
  // 뒷바퀴 표시
  ctx.fillStyle = T('--yellow'); [-1, 1].forEach((s) => { ctx.beginPath(); ctx.arc(s * (Wd / 2 + 0.28 * S), ry, 0.12 * S, 0, Math.PI * 2); ctx.fill(); });
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
      <div><h1>${session.phase === C.Phase.REVIEW ? '복기를 잠시 닫았어요' : '연습을 잠시 멈췄어요'}</h1><p class="sub" id="pauseReason"></p><p class="caption">${session.phase === C.Phase.REVIEW ? '복기는 주차가 확인될 때만 열려요. 다시 주차하면 보던 곳에서 이어져요' : '다시 준비되면 하던 곳에서 이어서 해요'}</p></div></div></div>`;
  },
  update() { $('#pauseReason').textContent = C.LockReason[session.lockReason] || ''; },
});

// A-7 주행 중 잠금 — 정적: 애니메이션·소리·버튼 없음
Screens.lock = () => ({
  mount(el) {
    el.innerHTML = `<div class="screen lock">
      <div class="lock-ico">${mascot('rest', 120)}</div>
      <p class="lock-tag">${appMode === 'eval' ? '평가 중' : '연습 중'}</p>
      <h1>화면은 잠시 쉴게요.<br>도로만 보세요.</h1>
      <p class="sub">${appMode === 'eval' ? '센서(가상)가 상황을 기록하고 있어요. 주차하면 상황별 조언을 보여 줘요.' : '주차하면 오늘 연습을 함께 돌아봐요.'}</p>
      <p class="lock-why">${icon('pbrake', 20)}<span>멈췄는데도 안 열리나요? 신호 대기처럼 잠깐 멈춘 건 주차가 아니에요. <b>기어 P</b>에 두고 <b>주차 브레이크</b>를 걸면 열려요. 내 기록·설정도 그때 열려요</span></p>
      ${voice?.enabled
        ? `<p class="lock-voice">${icon('sound', 22)} 필요할 때만 한마디 할게요 · 최대 ${voice.cap}회<br><small>내비·통화·경고음이 나오면 바로 양보해요</small></p>`
        : `<p class="lock-voice off">${icon('mentor', 22)} ${(prep?.mode || 'solo') === 'mentor' ? '함께 타신 분이 있어 음성은 꺼 뒀어요' : '음성 길잡이는 꺼져 있어요'}</p>`}
    </div>`;
    // 주행 중 화면은 정적이다 — 이 글은 주행에 들어갈 때 한 번 정해지고 바뀌지 않는다.
    // 실시간 계기는 왼쪽 콘솔(프로토타입 계측 영역)에만 둔다.
    renderVoiceMeter();
  },
});

const infoIco = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><circle cx="12" cy="12" r="9.5" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 10.5v6" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/><circle cx="12" cy="7.4" r="1.3" fill="currentColor"/></svg>';

// A-9 주행 리플레이(F13) — 점수·등급 없음. 사실과 조언만.
const SENSOR_LABEL = { front: '전방', rear: '후방', side: '측방' };
Screens.replay = () => ({
  mount(el) {
    const rows = replayRows;
    const spokenN = rows.filter((r) => r.spoken).length;
    el.innerHTML = `<div class="screen replay">
      <div class="hero"><div class="hero-m">${mascot('calm', 80)}</div><div>
        <h1>오늘 주행 돌아보기</h1>
        <p class="sub">${rows.length ? `기록된 순간 ${rows.length}개${spokenN ? ` · 그중 ${spokenN}개는 주행 중에 말해 드렸어요` : ''}` : '같이 보면서 이야기해 봐요'}</p>
      </div></div>
      ${timelineSection(rows, false)}
      ${rows.length ? `<ol class="rp-list">${rows.map((r, i) => `<li class="rp ${r.kind}">
        <span class="rp-t">${fmtT(r.atSec)}</span>
        <div class="rp-body">
          <b>${esc(r.title)}</b>
          ${r.spoken ? `<span class="rp-spoke" title="주행 중에 음성으로 말한 항목">${icon('sound', 16)} 말했어요</span>` : ''}
          <small class="rp-meta">${r.kmh}km/h${r.sensor ? ` · ${SENSOR_LABEL[r.sensor]} 센서(가상)` : ''} · ${srcChip(r.source)}</small>
          ${r.advice ? `<p class="rp-adv">${esc(r.advice)}</p>` : ''}
          ${sceneBtn(i, r.atSec)}
        </div></li>`).join('')}</ol>`
        : `<p class="rp-empty">${mascot('happy', 64)}<span>${VOICE.REPLAY_EMPTY}</span></p>`}
      <p class="caption">점수가 아니라 그 순간의 사실이에요. 기기 안에만 저장되고, 내보내기는 직접 하실 때만 해요.</p>
      <div class="actions"><div></div>${btn('rpNext', '복기 계속', 'primary big')}</div>
    </div>`;
    $('#rpNext').onclick = () => { RP.closePip(); replayShown = true; render(); };
    bindTimeline(el, rows, false);
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
        <h1>수고했어요</h1>
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
// C04: 머리말의 '소개'·'사용법'은 차량 앱이 아니라 웹 체험판을 설명하는 창이다(차 화면 밖).
// 그래서 가상 주행 중에도 연다. 대신 열려 있는 동안 가상 주행 시간을 멈춘다.
// '내 기록'·'나'·도움말 같은 앱 안의 창은 주행 중에는 열지 않는다(S04).
const SITE_OVERLAYS = ['intro', 'about'];
const siteOverlayOpen = () => !!overlay && SITE_OVERLAYS.includes(overlay);
const learningOverlayLocked = () => (overlay === 'help' || overlay === 'wiki') && !LEARN.learningAvailable(snapshot());
function renderOverlay() {
  const root = $('#modalRoot');
  if (learningOverlayLocked()) overlay = null;
  const blocked = session.phase === C.Phase.DRIVING_LOCK && !siteOverlayOpen();
  if (!overlay || blocked) { root.innerHTML = ''; root.classList.remove('open'); return; }
  root.classList.add('open');
  const body = { history: historyHtml, profile: profileHtml, cert: certHtml, intro: introHtml,
    help: () => LEARN.helpHtml(judgeState?.judge?.id, judgeState?.judge?.title || '도움말'), wiki: () => LEARN.wikiHtml(wikiPage), about: aboutHtml }[overlay]();
  root.innerHTML = `<div class="modal-scrim" data-close></div><div class="modal${overlay === 'intro' ? ' modal-ob' : ''}" role="dialog" aria-modal="true" aria-label="${{ history: '내 기록', profile: '설정', cert: '수료 카드', intro: '치일까봐 둘러보기', help: '미션 도움말', wiki: '운전 노트', about: '서비스 소개' }[overlay]}" tabindex="-1">
    <button type="button" class="icon-btn modal-x" data-close aria-label="닫기">${icon('close', 22)}</button>${session.phase === C.Phase.DRIVING_LOCK ? '<p class="site-note">웹 체험판 안내예요. 이 창이 열려 있는 동안 가상 주행은 멈춰 있어요. 실제 차에서는 주행 중에 이런 창이 뜨지 않아요</p>' : ''}${body}</div>`;
  $$('[data-close]', root).forEach((b) => (b.onclick = () => { if (overlay === 'intro') { H.introSeen = true; H.introV = INTRO_V; saveHistory(); } closeOverlay(); }));
  bindOverlay(root);
  $$('[data-wiki]', root).forEach((b) => (b.onclick = () => { wikiPage = LEARN.wikiIndex(b.dataset.wiki); openOverlay('wiki'); }));
  $$('[data-page]', root).forEach((b) => (b.onclick = () => { wikiPage = +b.dataset.page; renderOverlay(); }));
  setTimeout(() => $('.modal', root)?.focus?.(), 0);
}
// ── C05 (2026-11-14): 내 기록 — '이어 간다'는 느낌을 주는 두 조각 ──
// 점수·연속 기록(스트릭)은 쓰지 않는다. 끊기면 압박이 되고, 실력 평가처럼 읽히기 때문이다.
/** 다음 목표: 지난 복기에서 고른 목표를 맨 위에 다시 보여 준다 */
function historyNextHtml() {
  if (!H.lastNextGoal) return '';
  return `<div class="hist-next">${icon('flag', 22)}<div><small>다음 연습에서 이어서 해 볼 것</small><b>${esc(H.lastNextGoal)}</b></div></div>`;
}
/** 최근 7일 중 연습한 날. 오늘이 맨 오른쪽 */
function weekStripHtml() {
  const day = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
  const done = new Set((H.reviews || []).map((r) => day(r.at)));
  const today = day(Date.now());
  const names = ['일', '월', '화', '수', '목', '금', '토'];
  const days = Array.from({ length: 7 }, (_, k) => today - (6 - k) * 86400000);
  const n = days.filter((d) => done.has(d)).length;
  return `<div class="week" role="group" aria-label="최근 7일 중 ${n}일 연습">
    <div class="week-head"><b>최근 7일</b><small>${n ? `${n}일 연습했어요` : '아직 연습한 날이 없어요. 주차된 차에서 5분이면 돼요'}</small></div>
    <ol class="week-days">${days.map((d) => `<li class="${done.has(d) ? 'on' : ''}${d === today ? ' today' : ''}"><i aria-hidden="true"></i><span>${d === today ? '오늘' : names[new Date(d).getDay()]}</span><span class="sr-only">${done.has(d) ? '연습함' : '연습 안 함'}</span></li>`).join('')}</ol>
  </div>`;
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
    ${historyNextHtml()}
    ${weekStripHtml()}
    <h3>배지 <small>${H.badges.length}/9</small></h3>
    <div class="badge-grid">${Object.entries(C.Badges).map(([k, b]) => {
      const e = have.get(k);
      return `<div class="badge ${e ? 'on' : ''}">${badgeArt(b, !!e, 64)}<b>${b.title}${e?.mockOnly ? ' <em>(모의)</em>' : ''}</b><small>${e ? new Date(e.at).toLocaleDateString('ko-KR') : k === 'MENTOR_RETURN' ? '후속 기능' : b.desc}</small></div>`;
    }).join('')}</div>
    <h3>살살 밟기 — 내 기록 비교 <small>흔들림(작을수록 부드러움)</small></h3>
    ${stds.length ? `<div class="bars">${stds.map((s, i) => `<div class="bar"><i style="height:${(s.std / maxStd) * 100}%"></i><b>${s.std.toFixed(1)}</b><small>${i === stds.length - 1 ? '최근' : `${stds.length - 1 - i}회 전`}</small></div>`).join('')}</div>` : '<p class="muted">아직 기록이 없어요. 페달 감각 미션을 해 보면 여기에 쌓여요.</p>'}
    ${(H.simReports || []).length ? `<h3>가상 주행 점수 기록 <small>평가·강습 모드 · 가상 시뮬레이션</small></h3>
      <div class="bars">${H.simReports.slice(0, 8).reverse().map((r) => `<div class="bar"><i style="height:${Math.max(4, r.score)}%"></i><b>${r.score}</b><small>${r.mode === 'eval' ? '평가' : '강습'}</small></div>`).join('')}</div>` : ''}
    <h3>최근 복기</h3>
    ${H.reviews.length ? `<ul class="rev-list">${H.reviews.slice(0, 5).map((r) => `<li><span>${new Date(r.at).toLocaleDateString('ko-KR')}</span><b>${esc(r.goal || '연습')}</b><span>${r.summary ? `${r.summary.durationMin}분` : ''}</span><span class="muted">${esc(r.learner || '')}</span><span>다음: ${esc(r.next || '-')}</span></li>`).join('')}</ul>` : '<p class="muted">주차 후 복기를 저장하면 여기에 쌓여요.</p>'}`;
}
function profileHtml() {
  const aliases = ['나', '연습생', '새내기 운전자', '주말 드라이버'];
  return `<h2 class="m-title">${icon('user', 24)} 설정</h2>
    <h3>별칭 <small>이 기기에만 저장돼요</small></h3>
    <div>${chipGroup('alias', aliases, H.alias)}</div>
    <h3>주행 중 음성 길잡이 <small>F12</small></h3>
    <div class="set-row">${icon('sound', 22)}<span>주행 중 음성 길잡이<small>${voicePrefOn() ? '켜짐' : '꺼짐'}</small></span><button type="button" class="switch" id="setVoice" role="switch" aria-label="주행 중 음성 길잡이" aria-checked="${voicePrefOn(prep?.mode || 'solo')}"><span></span></button></div>
    <div class="set-row">${icon('note', 22)}<span>주행당 최대 횟수<small>많을수록 잔소리가 될 수 있어요. 간격 45초는 그대로</small></span>
      <div class="seg" role="radiogroup" aria-label="주행당 최대 횟수">${[3, 5].map((k) => `<button type="button" role="radio" data-cap="${k}" aria-checked="${voiceCap() === k}">${k}</button>`).join('')}</div></div>
    <div class="set-row">${icon('gear', 22)}<span>제한 속도 음성<small>내비가 이미 안내하니 기본은 꺼짐</small></span><button type="button" class="switch" id="setSpeedVoice" role="switch" aria-label="제한 속도 음성" aria-checked="${!!H.speedLimitVoice}"><span></span></button></div>
    <div class="set-row vm-test"><span>소리가 나오는지 확인<small>${esc(speaker.state().why)}</small></span><button type="button" class="mini" id="setVoiceTest">들어 보기</button></div>
    <p class="caption">웹 모의 시연이에요. 차량에서의 주행 중 음성 재생은 Pleos 주행 제한과 오디오 정책을 별도로 확인해야 해요.</p>
    <h3>화면</h3>
    <div class="set-row">${icon('sound', 22)}<span>정차 중 효과음</span><button type="button" class="switch" id="setSound" role="switch" aria-label="정차 중 효과음" aria-checked="${H.sound}"><span></span></button></div>
    <div class="set-row">${icon('text', 22)}<span>큰 글씨</span><button type="button" class="switch" id="setLarge" role="switch" aria-label="큰 글씨" aria-checked="${H.largeText}"><span></span></button></div>
    <h3>배우기</h3>
    <div class="set-row">${icon('note', 22)}<span>운전 노트<small>출발 전·언덕·차로 바꾸기·교차로·주차 — 정차 중에만 열려요</small></span><button type="button" class="mini" data-wiki="before">열기</button></div>
    <h3>기록</h3>
    <button type="button" class="btn danger" id="delBtn">${icon('trash', 20)} 이 기기의 기록 모두 지우기</button>
    <p class="caption">서버·계정·위치·차대번호를 쓰지 않아요. 기록은 이 브라우저에만 있어요.</p>`;
}
function certHtml() {
  const first = H.firstAt ? new Date(H.firstAt).toLocaleDateString('ko-KR') : new Date().toLocaleDateString('ko-KR');
  return `<div class="cert">
    <div class="cert-top">${mascot('happy', 84)}<div><small>치일까봐 연습 수료 카드</small><h2>${esc(H.alias)} 님, 혼자 출발</h2></div></div>
    <div class="cert-stats"><div><b>${H.reviewsSaved}</b><small>이어 간 연습</small></div><div><b>${H.badges.length}</b><small>모은 배지</small></div><div><b>${first}</b><small>첫 연습</small></div></div>
    <div class="cert-badges">${H.badges.map((b) => badgeArt(C.Badges[b.badge], true, 40)).join('')}</div>
    <p class="cert-note">운전 실력 인증이 아니라, 이어 온 연습의 기록이에요.<br>도로에서는 언제나 천천히, 안전하게.</p>
  </div>`;
}
let obDir = '';
function introHtml() { return OB.html(obStep, obDir); }
function bindOverlay(root) {
  if (overlay === 'profile') {
    const aliases = ['나', '연습생', '새내기 운전자', '주말 드라이버'];
    rebindChipsSimple(root, 'alias', aliases, () => H.alias, (v) => { H.alias = v; saveHistory(); renderChrome(); });
    $('#setVoice').onclick = (e) => {
      H.voiceOn = !voicePrefOn();
      saveHistory();
      if (voice) voice.enabled = voicePrefOn(prep?.mode || 'solo');
      if (!voicePrefOn(prep?.mode || 'solo')) {
        voice?.dropped('CANCELLED');
        if (voice) voice.window = null;
        speaker.stop();
      }
      renderVoiceMeter(); renderOverlay();
    };
    $$('[data-cap]', root).forEach((b) => (b.onclick = () => { H.voiceCap = +b.dataset.cap; saveHistory(); if (voice) voice.cap = H.voiceCap; renderOverlay(); }));
    $('#setSpeedVoice').onclick = (e) => { H.speedLimitVoice = !H.speedLimitVoice; e.currentTarget.setAttribute('aria-checked', String(H.speedLimitVoice)); saveHistory(); if (voice) voice.speedLimitOn = H.speedLimitVoice; };
    $('#setVoiceTest').onclick = async (e) => {
      speaker.unlock();
      const b = e.currentTarget; b.disabled = true; b.textContent = '말하는 중…';
      const r = await speaker.speak(VOICE.LINES.STEADY.line);
      b.textContent = r === 'spoken' ? '들렸어요' : '소리가 막혔어요';
      setTimeout(() => { b.disabled = false; b.textContent = '들어 보기'; renderOverlay(); }, 1200);
    };
    $('#setSound').onclick = (e) => { H.sound = !H.sound; e.currentTarget.setAttribute('aria-checked', H.sound); saveHistory(); };
    $('#setLarge').onclick = (e) => { H.largeText = !H.largeText; e.currentTarget.setAttribute('aria-checked', H.largeText); saveHistory(); applyPrefs(); };
    let armed = false;
    $('#delBtn').onclick = (e) => {
      if (!armed) { armed = true; e.currentTarget.innerHTML = `${icon('trash', 20)} 한 번 더 누르면 지워져요`; return; }
      const keep = { introSeen: true }; H = { ...blankHistory(), ...keep }; saveHistory(); applyPrefs(); closeOverlay(); render();
    };
  }
  if (overlay === 'intro') {
    const finish = (then) => { H.introSeen = true; H.introV = INTRO_V; saveHistory(); closeOverlay(); then?.(); };
    const go = (k) => {
      const to = Math.max(0, Math.min(OB.STEPS.length - 1, k));
      if (to === obStep) return;
      obDir = to > obStep ? 'next' : 'prev';
      obStep = to;
      renderOverlay();
      // 단계를 바꿔도 읽는 자리를 잃지 않게 제목으로 포커스를 옮긴다
      setTimeout(() => $('.ob-title', $('#modalRoot'))?.focus?.(), 0);
    };
    $$('[data-ob]', root).forEach((b) => (b.onclick = () => ({
      prev: () => go(obStep - 1),
      next: () => go(obStep + 1),
      skip: () => finish(),
      // 끝내면 '어디부터 하면 되는지'를 화면에서 짚어 준다(설명만 읽고 닫으면 다시 막막하다)
      done: () => finish(coachMark),
      demo: () => finish(startDemo),
    }[b.dataset.ob]())));
    $$('[data-go]', root).forEach((b) => (b.onclick = () => go(+b.dataset.go)));
    // ← → 로도 넘긴다(차량 조그 다이얼처럼 좌우 이동이 기본 동작이다)
    $('.ob', root)?.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight') { e.preventDefault(); go(obStep + 1); }
      if (e.key === 'ArrowLeft') { e.preventDefault(); go(obStep - 1); }
    });
    // 터치: 좌우로 밀어서 넘긴다(휴대폰·차량 터치 화면). 세로 스크롤과 헷갈리지 않게 가로 이동이 더 클 때만
    const ob = $('.ob', root); let sx = null, sy = 0;
    ob?.addEventListener('pointerdown', (e) => { if (e.pointerType !== 'mouse') { sx = e.clientX; sy = e.clientY; } });
    ob?.addEventListener('pointerup', (e) => {
      if (sx == null) return; const dx = e.clientX - sx, dy = e.clientY - sy; sx = null;
      if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.5) go(obStep + (dx < 0 ? 1 : -1));
    });
  }
}

/**
 * 온보딩을 끝낸 직후 '첫 행동'을 짚어 주는 안내(코치 마크).
 * 지금 화면에서 가장 먼저 눌러야 할 곳 하나에 테두리를 두르고 한 줄로 말해 준다.
 * 아무 곳이나 누르거나 7초가 지나면 사라진다. 화면을 막지 않는다(뒤를 바로 누를 수 있다).
 */
function coachMark() {
  $('#coachMark')?.remove();
  const visible = (el) => el && el.offsetParent !== null && el.getBoundingClientRect().width > 0;
  const guideCard = $('#guide'), fab = $('#consoleFab'), start = $('#startBtn');
  // 우선순위: 차 화면의 '연습 시작'(이미 열려 있으면) → 조작판의 '다음 조작' → (휴대폰) 조작판 버튼
  const pick = visible(start) && !start.disabled ? [start, '여기를 누르면 연습이 시작돼요']
    : visible(guideCard) ? [guideCard, '여기 적힌 대로 하나씩 해 보세요. 할 일이 바뀌면 이 글도 바뀌어요']
    : visible(fab) ? [fab, '여기를 누르면 가상 조작판이 열려요'] : null;
  if (!pick) return;
  const [el, text] = pick;
  el.scrollIntoView({ block: 'nearest', behavior: reduceMotion ? 'auto' : 'smooth' });
  const r = el.getBoundingClientRect();
  const m = document.createElement('div');
  m.id = 'coachMark'; m.className = 'coach-mark'; m.setAttribute('role', 'status');
  const below = r.bottom + 96 < innerHeight;
  m.innerHTML = `<span class="cm-ring" style="left:${r.left - 6}px;top:${r.top - 6}px;width:${r.width + 12}px;height:${r.height + 12}px"></span>
    <p class="cm-tip ${below ? 'below' : 'above'}" style="left:${Math.min(Math.max(16, r.left), innerWidth - 336)}px;${below ? `top:${r.bottom + 14}px` : `top:${r.top - 14}px`}">${text}</p>`;
  document.body.appendChild(m);
  const off = () => { m.remove(); removeEventListener('pointerdown', off, true); removeEventListener('keydown', off, true); };
  setTimeout(() => { addEventListener('pointerdown', off, true); addEventListener('keydown', off, true); }, 50);
  setTimeout(off, 7000);
}
function applyPrefs() { document.documentElement.classList.toggle('large', !!H.largeText); }

// ── 테마(다크 ↔ 라이트) ─────────────────────────────────────
// Pleos Connect는 다크를 기본으로 라이트 변경을 지원한다(현대차그룹 공식 소개) → 그대로 따른다.
function syncThemeBtn() {
  const light = THEME.current() === 'light';
  const b = $('#themeBtn'); if (!b) return;
  $('#themeIco').innerHTML = icon(light ? 'moon' : 'sun', 20);
  $('#themeLabel').textContent = light ? '어둡게' : '밝게';
  b.setAttribute('aria-label', light ? '어두운 화면으로 바꾸기' : '밝은 화면으로 바꾸기');
}
/** 테마가 바뀌면 캔버스는 스스로 다시 그리지 않으므로 강제로 한 번 그린다 */
function repaintForTheme() {
  syncThemeBtn();
  chromeKey = ''; guideKey = '';          // 캐시된 HTML을 다시 만들게
  const hud = $('#simHud'); if (hud) hud.dataset.k = '';
  renderChrome();
  mounted.screen?.draw?.();               // 가상 주차 화면
  lastIdleDraw = 0; drawSimView();        // 가상 도로
  renderVoiceMeter();
}

// ── 공통 렌더 ────────────────────────────────────────────────
let mounted = { key: null, screen: null };
function screenKey() {
  switch (session.phase) {
    case C.Phase.CHECKING: case C.Phase.READY: return 'gate';
    case C.Phase.DRIVING_LOCK: return appMode === 'coach' && sim ? 'coach' : 'lock';
    case C.Phase.REVIEW:
      // E3(C01): 주차 확인이 깨지면 복기·리포트·장면 재현을 모두 닫고 멈춤 화면으로. 다시 주차하면 이어서
      if (session.lockReason) return 'paused';
      // 연습 모드: 리플레이를 한 번 보여 준 뒤 복기 폼으로. 평가·강습 모드는 리포트 안에 들어 있다.
      if (appMode === 'practice' && !replayShown) return 'replay';
      return appMode !== 'practice' && simReport ? 'report' : 'review';
    case C.Phase.PREPARING:
      // 출발 직전(주차 브레이크를 푸는 게 다음 행동)에는 멈춤 화면으로 바꾸지 않는다
      if (session.lockReason && !(session.lockReason === 'PARKING_BRAKE_OFF' && ['go', 'simSetup'].includes(prep.step))) return 'paused';
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
  if (learningOverlayLocked()) closeOverlay();
  renderChrome();
}

const STEPS = [['goal', '목표'], ['mission', '워밍업'], ['pedal', '페달'], ['parking', '가상 주차'], ['pact', '약속'], ['go', '출발']];
let chromeKey = '';
function renderChrome() {
  syncAppButtons();
  const s = snapshot();
  const ph = session.phase;
  const modeName = { practice: '연습', eval: '평가', coach: '강습' }[appMode];
  const pill = { CHECKING: '확인 중', READY: '정차 중', PREPARING: `정차 중 · ${modeName} 준비`, DRIVING_LOCK: `주행 중${appMode === 'practice' ? '' : ` · ${modeName}`}`, REVIEW: appMode === 'practice' ? '주차 후 복기' : '주행 리포트' }[ph];
  const k = [ph, prep?.step, prep?.mode, H.alias, overlay, appMode].join('|');
  document.body.dataset.phase = ph;
  if (k !== chromeKey) {
    chromeKey = k;
    $('#phasePill').textContent = pill;
    $('#phasePill').dataset.phase = ph;
    $('#aliasLabel').textContent = H.alias;
    const st = $('#stepper');
    if (ph === C.Phase.PREPARING && prep && appMode === 'practice') {
      const names = Object.fromEntries(STEPS);
      const list = stepOrder(prep).map((id) => [id, names[id]]);
      const cur = list.findIndex(([id]) => id === prep.step);
      st.innerHTML = list.map(([id, l], i) => `<span class="${i < cur ? 'd' : i === cur ? 'c' : ''}">${i < cur ? icon('check', 14) : `<i>${i + 1}</i>`}${l}</span>`).join('');
      st.hidden = false;
    } else st.hidden = true;
    renderOverlay();
  }
  // Driving view — Telltale(계기 표시등): 차가 이미 켜는 표시등을 그대로 보여 준다
  const tells = [
    ['belt', '벨트', s.driverBeltBuckled.status === 'OK' ? (s.driverBeltBuckled.value ? 'ok' : 'on') : ''],
    ['pbrake', 'P', s.parkingBrakeOn.status === 'OK' && s.parkingBrakeOn.value ? 'on' : ''],
    ['beam', '전조등', s.lowBeamOn.value ? 'ok' : ''],
    ['wiper', '와이퍼', s.wiperOn.value ? 'ok' : ''],
    ['turn', s.turnSignal.value === 'LEFT' ? '◀' : s.turnSignal.value === 'RIGHT' ? '▶' : '깜빡이', s.turnSignal.value && s.turnSignal.value !== 'NONE' ? 'ok' : ''],
  ];
  const tellKey = tells.map((t) => t[1] + t[2]).join('|');
  const tellEl = $('#dvTell');
  if (tellEl.dataset.k !== tellKey) {
    tellEl.dataset.k = tellKey;
    tellEl.innerHTML = tells.map(([ico, label, cls]) => `<span class="tell ${cls}">${icon(ico, 14)}${label}</span>`).join('');
  }
  $('#dvSpeed').textContent = Math.round(V.speedKmh.value);
  $('#dvGear').textContent = s.gear.status === 'OK' ? s.gear.value : '–';
  $('#dvPb').classList.toggle('on', s.parkingBrakeOn.value === true);
  syncConsoleGear(s);
  renderGuide(s);
}

// ── 콘솔 ─────────────────────────────────────────────────────
const keyboardDrive = new KeyboardDrive();
const consoleInput = { brake: () => false, accel: () => false, steer: () => false };
const stopRequestsSeen = new WeakSet();
function stopDisplay(vehicle) {
  if (vehicle.stopReq) stopRequestsSeen.add(vehicle);
  return stopRequestState(vehicle.stopReq, vehicle.ego.v, stopRequestsSeen.has(vehicle));
}
function applyKeyboard(values) {
  if (values.accel != null && values.accel !== V.accelPedalPct.value) setAccel(values.accel);
  if (values.brake != null && values.brake !== V.brakePedalPct.value) setBrake(values.brake);
  if (values.steer != null && Math.round(values.steer / 5) * 5 !== V.steeringDeg.value) setSteer(values.steer);
}
function releaseConsoleInputs() {
  applyKeyboard(keyboardDrive.releaseAll());
  if (consoleInput.accel()) setAccel(0);
  if (consoleInput.brake()) setBrake(0);
  if (consoleInput.steer()) setSteer(0);
}
function manualSteer() { keyboardDrive.takeover('steer'); consoleInput.steer(); }
function setControlHelp(open) {
  $('#wasdHint').hidden = !open;
  $('#controlHelp').setAttribute('aria-expanded', String(open));
}
function setSteer(v) { v = clamp(Math.round(v / 5) * 5, -540, 540); setV('steeringDeg', v); syncConsole(); }
function setBrake(v) {
  v = clamp(v, 0, 100); setV('brakePedalPct', v); setV('brakePressed', v > 5, { onlyIfChanged: true }); syncConsole();
}
function setSwitch(id, on) { const b = $(id); b.setAttribute('aria-checked', String(on)); }
function syncConsoleGear(s) {
  const label = gearPresentation(s.gear.status === 'OK' ? s.gear.value : null,
    s.parkingBrakeOn.status === 'OK' ? s.parkingBrakeOn.value : null);
  const output = $('#c-gearState');
  if (output.textContent !== label) output.textContent = label;
}
function syncConsole() {
  $$('#c-gear button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.v === V.gear.value)));
  syncConsoleGear(snapshot());
  $$('#c-turn button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.v === V.turnSignal.value)));
  setSwitch('#c-pb', V.parkingBrakeOn.value); setSwitch('#c-belt', V.driverBeltBuckled.value); setSwitch('#c-wiper', V.wiperOn.value);
  setSwitch('#c-beam', V.lowBeamOn.value); setSwitch('#c-pass', V.passengerOccupied.value); setSwitch('#c-fast', timeScale > 1);
  $('#v-belt').textContent = V.driverBeltBuckled.value ? '체결' : '미체결';
  const sp = $('#c-speed'); sp.value = V.speedKmh.value; sp.disabled = appMode !== 'practice' || V.gear.value === 'P' || V.gear.value === 'N';
  $('#v-speed').textContent = Math.round(V.speedKmh.value);
  $('#speedNote').textContent = appMode !== 'practice' ? '(가상 주행: 페달로 조절)' : '(기어 D·R에서만)';
  $('#speedNote').hidden = !sp.disabled;
  $('#c-accel').value = V.accelPedalPct.value; $('#v-accel').textContent = Math.round(V.accelPedalPct.value);
  $('#c-brake').value = V.brakePedalPct.value; $('#v-brake').textContent = Math.round(V.brakePedalPct.value); $('#v-brakeP').textContent = V.brakePressed.value ? '밟음' : '뗌';
  $('#c-steer').value = V.steeringDeg.value; $('#v-steer').textContent = Math.abs(V.steeringDeg.value);
  $('#v-steerDir').textContent = V.steeringDeg.value > 0 ? '오른쪽' : V.steeringDeg.value < 0 ? '왼쪽' : '가운데';
  const wh = $('#c-wheel'); wh.style.setProperty('--deg', `${V.steeringDeg.value}deg`); wh.setAttribute('aria-valuenow', String(V.steeringDeg.value));
  wh.setAttribute('aria-valuetext', `${Math.abs(V.steeringDeg.value)}도 ${$('#v-steerDir').textContent}`);
  $('#c-brakeHold').style.setProperty('--press', String(V.brakePedalPct.value / 100));
  $('#c-accelHold').style.setProperty('--press', String(V.accelPedalPct.value / 100));
}
/** 핸들 그림: 잡고 돌린 각도만큼(1:1) 돌아간다. 한 바퀴 넘게도 돌릴 수 있다(±540°). */
function bindWheel(el) {
  let drag = null;
  const ang = (e) => { const r = el.getBoundingClientRect(); return (Math.atan2(e.clientY - (r.top + r.height / 2), e.clientX - (r.left + r.width / 2)) * 180) / Math.PI; };
  const end = () => { const active = !!drag; drag = null; el.classList.remove('down'); return active; };
  el.addEventListener('pointerdown', (e) => {
    if (drag || (e.button != null && e.button !== 0) || e.isPrimary === false) return;
    e.preventDefault(); keyboardDrive.takeover('steer');
    el.setPointerCapture?.(e.pointerId); drag = { id: e.pointerId, a: ang(e), v: V.steeringDeg.value, acc: 0 }; el.classList.add('down');
  });
  el.addEventListener('pointermove', (e) => {
    if (!drag || drag.id !== e.pointerId) return;
    const a = ang(e); let d = a - drag.a; if (d > 180) d -= 360; if (d < -180) d += 360;
    drag.a = a; drag.acc += d; setSteer(drag.v + drag.acc); tick();
  });
  ['pointerup', 'pointercancel', 'lostpointercapture'].forEach((ev) => el.addEventListener(ev, (e) => { if (drag?.id === e.pointerId) end(); }));
  el.addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    e.preventDefault(); e.stopPropagation(); manualSteer(); setSteer(V.steeringDeg.value + (e.key === 'ArrowLeft' ? -15 : 15)); tick();
  });
  return end;
}

function bindConsole() {
  $$('#c-maneuver button').forEach((b) => (b.onclick = () => setSimManeuver(b.dataset.v)));
  $$('#c-gear button').forEach((b) => (b.onclick = () => {
    if (sim && simRunning() && sim.ego.v > 0.4 && (b.dataset.v === 'P' || b.dataset.v === 'R')) { flashNote('차가 완전히 멈춘 다음 옮길 수 있어요'); return; }
    releaseConsoleInputs();
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
  $('#c-steer').oninput = (e) => { manualSteer(); setSteer(+e.target.value); tick(); };
  $('#c-steerCenter').onclick = () => { manualSteer(); setSteer(0); tick(); };
  // 페달 그림(11/12 회의 A05): 마우스 클릭은 0/1뿐이라 세기를 못 준다 → **누르고 있는 시간만큼 깊게**.
  // 짧게 톡 = 살살, 길게 = 세게. 슬라이더는 그대로 두어 정확한 값도 넣을 수 있다.
  const cancelBrakeHold = holdPedal($('#c-brakeHold'), () => V.brakePedalPct.value, setBrake, 1600, 900, { onTakeover: () => keyboardDrive.takeover('brake') });
  consoleInput.brake = cancelBrakeHold;
  // 수동 슬라이더는 페달 그림의 누름/놓음 애니메이션보다 우선한다.
  bindBrakeSlider($('#c-brake'), cancelBrakeHold, setBrake, tick);
  consoleInput.accel = holdPedal($('#c-accelHold'), () => V.accelPedalPct.value, setAccel, 2500, 250, { onTakeover: () => keyboardDrive.takeover('accel') });
  consoleInput.steer = bindWheel($('#c-wheel'));
  $('#c-accel').oninput = (e) => { consoleInput.accel(); setAccel(+e.target.value); };
  $('#c-belt').addEventListener('click', () => { if (!V.driverBeltBuckled.value) return; const r = $('#r-belt'); r.classList.remove('clack'); void r.offsetWidth; r.classList.add('clack'); });
  $('#controlHelp').onclick = () => setControlHelp($('#wasdHint').hidden);
  $('#wasdHintX').onclick = () => { setControlHelp(false); $('#controlHelp').focus(); };
  $('#wasdHint').addEventListener('keydown', (e) => { if (e.key === 'Escape') { e.stopPropagation(); setControlHelp(false); $('#controlHelp').focus(); } });
  // 예외 재현
  $('#statusRows').innerHTML = STATUS_KEYS.map((k) => `<label class="st-row"><span>${SIGNALS[k].label}</span>
    <select data-k="${k}">${Object.entries(STATUS_LABEL).map(([v, l]) => `<option value="${v}">${l}</option>`).join('')}</select></label>`).join('');
  $$('#statusRows select').forEach((sel) => (sel.onchange = () => { statusOverride[sel.dataset.k] = sel.value; tick(); }));
  $('#c-policy').onchange = (e) => { policy.allowWhenParkingBrakeUnsupported = e.target.checked; tick(); };
  // 음성 계기
  $('#c-voiceTest').onclick = async (e) => {
    speaker.unlock();
    const b = e.currentTarget; b.disabled = true; b.textContent = '말하는 중…';
    const r = await speaker.speak(VOICE.LINES.STEADY.line);
    b.textContent = r === 'spoken' ? '들렸어요' : '막혔어요';
    renderVoiceMeter();
    setTimeout(() => { b.disabled = false; b.textContent = '소리 확인'; }, 1400);
  };
  // 내비 안내가 끼어든 상황 — 진짜 오디오 포커스는 브라우저에 없으니, 다른 소리를 재생해 duck 상황을 만든다.
  // 이때 앱은 **그 발화를 버린다**(나중에 다시 말하지 않는다 — T13 ④).
  $('#c-navDuck').onclick = (e) => {
    speaker.unlock();
    const b = e.currentTarget; b.disabled = true; b.textContent = '내비 안내 중…';
    const dropped = voice?.dropped('AUDIO_FOCUS');
    if (voice) voice.window = null;
    speaker.stop('AUDIO_FOCUS');
    mockNavUntil = performance.now() + 2600;
    void speaker.speak('잠시 후 우회전입니다.');
    flashNote(dropped ? `내비에 양보: “${dropped.line}” 는 버렸어요` : '내비 안내가 끼어들면 그 말은 버려요');
    renderVoiceMeter();
    setTimeout(() => { b.disabled = false; b.textContent = '내비 안내 끼어들기'; renderVoiceMeter(); }, 2600);
  };

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

const GUIDE_TARGETS = ['r-accel', 'r-gear', 'r-pb', 'r-speed', 'r-fast', 'r-belt', 'r-brake', 'r-turn', 'r-wiper', 'r-beam', 'r-steer', 'r-pass'];
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
  if (appMode !== 'practice') { const g = simGuide(); if (g) return g; }
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
        if (p?.kind === 'done') return ['잘했어요 앱에서 <b>[다음]</b>을 눌러요.', null];
        if (p?.kind === 'unavailable') return ['신호를 읽을 수 없어 앱이 건너뛰기를 안내해요.', null];
        if (id === 'SEAT_BELT') return [V.driverBeltBuckled.value ? '이미 <b>안전벨트</b>를 매고 있다면 그대로 두세요.' : '이제 <b>안전벨트</b>를 매요.', 'r-belt'];
        if (id === 'BRAKE_PRESS_RELEASE') return [p?.step === 2 ? '이제 <b>페달 그림</b>에서 손을 떼요(천천히 떼짐).' : '<b>페달 그림</b>을 꾹 누르고 있거나(누른 만큼 깊게) 막대를 끝까지 밀어요.', 'r-brake'];
        if (id === 'TURN_SIGNAL') return [['방향지시등 <b>◀ 왼쪽</b>', '방향지시등 <b>오른쪽 ▶</b>', '방향지시등 <b>끔</b>'][(p?.step || 1) - 1], 'r-turn'];
        if (id === 'WIPER') return ['<b>와이퍼</b>를 켜 보세요.', 'r-wiper'];
        if (id === 'LOW_BEAM') return ['<b>전조등</b>을 켜 보세요.', 'r-beam'];
        if (id === 'STEERING_CENTER') return ['<b>핸들</b>을 가운데(±5°)로 맞추고 2초.', 'r-steer'];
        return ['', null];
      }
      case 'pedal': return judgeState?.progress?.kind === 'done' ? ['부드러웠어요 앱에서 다음으로.', null] : ['<b>브레이크 페달</b> 슬라이더를 20~35%에 두고 2초.', 'r-brake'];
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
  // E3(C01): 복기 중 주차 확인이 깨지면 장면 재현(작은 창)부터 바로 닫는다
  if (session.phase === C.Phase.REVIEW && session.lockReason && !before.lockReason) { RP.closePip(); overlay = null; }
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
  if (to === C.Phase.DRIVING_LOCK) { RP.closePip(); overlay = null; $('#toasts').innerHTML = ''; toastBusy = false; review = null; }
  if (to === C.Phase.REVIEW) review = null;
  if (appMode !== 'practice' && to === C.Phase.DRIVING_LOCK && Math.abs(V.steeringDeg.value) > 5) { setSteer(0); flashNote('핸들을 가운데로 맞추고 출발해요'); }
  if (appMode !== 'practice' && to === C.Phase.DRIVING_LOCK) { sim = new SIM.DriveSim(appMode === 'eval' ? { lka: 'off', acc: false, cap: null } : { ...simSettings }, (Date.now() % 997) + 1); simReport = null; }
  if (appMode !== 'practice' && from === C.Phase.DRIVING_LOCK && to === C.Phase.REVIEW && sim) {
    simReport = sim.report();
    H.simReports = [{ at: Date.now(), mode: appMode, score: simReport.score, laneKeepPct: simReport.laneKeepPct, dur: simReport.durationSec }, ...(H.simReports || [])].slice(0, 20);
    saveHistory();
  }
  if (from === C.Phase.PREPARING && to === C.Phase.DRIVING_LOCK) judgeState = null;
  if (to === C.Phase.DRIVING_LOCK) startVoiceDrive(prep?.mode || 'solo');
  if (from === C.Phase.DRIVING_LOCK) endVoiceDrive();
}
function flashNote(t) {
  // 주행 잠금 중에는 앱 화면에 아무것도 띄우지 않는다(S04: 주행 중 화면은 정적).
  // 이 알림들은 모두 콘솔·시연 조작에 대한 것이라 콘솔 쪽에 보여 준다.
  if (session.phase === C.Phase.DRIVING_LOCK) {
    const c = $('#consoleFlash'); c.textContent = t; c.hidden = false;
    clearTimeout(flashNote.t); flashNote.t = setTimeout(() => { c.hidden = true; }, 2600);
    return;
  }
  const el = document.createElement('div'); el.className = 'toast note timed'; el.style.setProperty('--life', '1.6s'); el.textContent = t; $('#toasts').appendChild(el);
  setTimeout(() => el.remove(), 1600);
}


// ── 자동 시연(심사·영상용) ───────────────────────────────────
// 콘솔·앱 버튼을 실제로 눌러 가며 전체 흐름을 약 1분에 보여 준다. 사람이 콘솔을 만지면 바로 멈춘다.
const demo = { running: false, token: 0 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// 자동 시연 속도(0.5× · 1× · 2× · 3×). 시연 중에도 바꾸면 바로 반영된다.
let demoSpeed = 1;
const dsleep = (ms) => sleep(ms / demoSpeed);
function demoBanner(text) {
  let el = $('#demoBanner');
  if (!el) { el = document.createElement('div'); el.id = 'demoBanner'; el.className = 'demo-banner'; el.setAttribute('role', 'status'); document.body.appendChild(el); }
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
  if (appMode !== 'practice') return startSimDemo();
  // 처음부터 다시 시작하지 않고 **지금 있는 단계에서 이어 간다**.
  // 0 안전 확인 · 1 목표 · 2 워밍업 · 3 가상 주차 · 4 페달 · 5 약속 · 6 출발 · 7 주행 · 8 복기
  const STAGE = { goal: 1, mission: 2, parking: 3, pedal: 4, pact: 5, go: 6 };
  const at = session.phase === C.Phase.DRIVING_LOCK ? 7 : session.phase === C.Phase.REVIEW ? 8
    : session.phase === C.Phase.PREPARING ? (STAGE[prep?.step] ?? 1) : 0;
  const mi = prep?.step === 'mission' ? prep.missionIdx : 0;
  demo.running = true; const tk = ++demo.token;
  $('#demoBtn').textContent = '시연 멈추기';
  const alive = () => demo.running && demo.token === tk;
  const step = async (label, ms = 700) => { if (!alive()) throw new Error('stop'); demoBanner(label); await dsleep(ms); if (!alive()) throw new Error('stop'); };
  const click = (sel) => { const el = $(sel); if (el && !el.disabled) el.click(); };
  const ramp = async (fn, from, to, ms) => { ms /= demoSpeed; const t0 = performance.now(); for (;;) { const k = Math.min(1, (performance.now() - t0) / ms); fn(from + (to - from) * k); tick(); if (k >= 1) break; await sleep(40); if (!alive()) throw new Error('stop'); } };
  const waitFor = async (cond, max = 15000) => { const t0 = performance.now(); while (!cond()) { if (performance.now() - t0 > max || !alive()) throw new Error('stop'); await sleep(80); } };
  const setGear = (g) => click(`#c-gear button[data-v="${g}"]`);
  try {
    syncConsole();
    await waitFor(() => !booting);
    if (at > 0) await step('지금 자리에서 이어서 보여 줄게요', 900);
    if (at === 0) {
      if (V.gear.value !== 'P') setGear('P');
      if (!V.parkingBrakeOn.value) click('#c-pb');
      await waitFor(() => session.phase === C.Phase.READY);
      await step('차가 기어 P·주차 브레이크·주행 제한을 직접 확인해요', 1600);
      click('#startBtn');
    }
    if (at <= 1) {
      await step('조수석에 멘토가 앉으면 차가 알아봐요', 900); if (!V.passengerOccupied.value) click('#c-pass');
      await step('오늘 걱정되는 것: 주차', 900); click('.chips[data-group="goal"] .chip[data-v="주차"]');
      await step('멘토와 함께 시작', 800); click('#mentorBtn');
    }
    if (at <= 2) {
      if (mi <= 0) {
        if (V.driverBeltBuckled.value) {
          await step('안전벨트가 이미 체결되어 있어요. 풀지 말고 그대로 유지해요', 1300);
        } else {
          await step('미체결 상태라면 안전벨트를 매요', 1000); click('#c-belt');
        }
        await waitFor(() => judgeState?.progress?.kind === 'done');
        await step('안전벨트 상태를 확인했어요', 1500); click('#nextBtn');
      }
      if (mi <= 1) {
        await step('브레이크를 끝까지 꾹', 600); await ramp(setBrake, 0, 100, 500);
        await step('천천히 떼기', 400); await ramp(setBrake, 100, 0, 900);
        await step('끝까지 꾹, 그리고 천천히 — 확인', 1400); click('#nextBtn');
      }
      if (mi <= 2) {
        await step('방향지시등 왼쪽', 700); click('#c-turn button[data-v="LEFT"]');
        await step('오른쪽', 700); click('#c-turn button[data-v="RIGHT"]');
        await step('끄기', 700); click('#c-turn button[data-v="NONE"]');
        await step('깜빡이 완료', 1300); click('#nextBtn');
      }
      await step('선택 미션은 오늘은 넘어가요(5분 안에 핵심만)', 1300); click('#skipAllBtn');
    }
    if (at <= 3) {
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
    await step(sc().done ? '칸 안에 들어왔어요' : '거의 다 왔어요', 1600); click('#pkNext');
    }
    if (at <= 4 && prep?.step === 'pedal') {
    await step('페달 감각: 초록 칸 안에서 2초', 500); await ramp(setBrake, 0, 27, 700);
    await waitFor(() => judgeState?.progress?.kind === 'done', 5000); await step('부드러웠어요', 1300); await ramp(setBrake, 27, 0, 300); click('#nextBtn');
    }
    if (at <= 5 && prep?.step === 'pact') {
    await step('멘토와 오늘의 약속', 700);
    $$('.chips[data-group="pact"] .chip')[0]?.click(); await sleep(400); $$('.chips[data-group="pact"] .chip')[3]?.click();
    await step('둘 다 약속했어요', 900); click('#pactBtn');
    }
    if (at <= 6) {
    await step('출발 준비 완료 — 주차 브레이크를 풀고 D', 1600); if (V.parkingBrakeOn.value) click('#c-pb'); await dsleep(400); setGear('D');
    }
    if (at <= 7) {
    await waitFor(() => session.phase === C.Phase.DRIVING_LOCK);
    await step('화면은 조용히 잠겨요 — 영상·애니메이션·화면 전환·터치 없음', 900);
    if (timeScale === 1) click('#c-fast');
    const setSpeed = (v) => { setV('speedKmh', Math.round(v)); setV('uxRestricted', v > 0, { onlyIfChanged: true }); syncConsole(); };
    // 급출발을 일부러 만든다 → 화면은 그대로 두고 음성만 한마디(F12)
    await ramp(setSpeed, 0, 38, 700);
    await step('급하게 출발했죠? 화면은 그대로, 목소리만 한마디 해요', 2400);
    await step('(시간 빨리 감기 ×30으로 14분 주행을 몇 초에)', 3200);
    // 급제동 — 브레이크를 실제로 밟아야 급제동으로 센다(내리막·엔진 브레이크와 구분)
    setBrake(60); await ramp(setSpeed, 38, 8, 500);
    await step('이번엔 급제동 — 45초가 지났으니 한 번 더 말해 줘요', 2400);
    setBrake(0); await ramp(setSpeed, 8, 0, 400);
    await step('주차: 기어 P + 주차 브레이크', 600); setSpeed(0); setGear('P'); await dsleep(300); if (!V.parkingBrakeOn.value) click('#c-pb');
    if (timeScale > 1) click('#c-fast');
    }
    await waitFor(() => session.phase === C.Phase.REVIEW);
    // A-9 리플레이가 복기 앞에 한 번 들어온다(연습 모드)
    if (screenKey() === 'replay') {
      await step(`주행 중 있었던 일을 시간순으로 — 기록 ${replayRows.length}개${voice?.log.length ? `, 그중 ${voice.log.length}개는 말해 줬어요` : ''}`, 2600);
      await step('강사·멘토와 같은 화면을 보며 "3분 12초에 여기서" 할 수 있어요. 점수는 없어요', 2200);
      click('#rpNext');
      await waitFor(() => screenKey() === 'review');
    }
    await step('트립으로 오늘 연습을 돌아봐요', 1500);
    $$('.chips[data-group="learner"] .chip')[0]?.click(); await step('오늘 어땠어요?', 700);
    $$('.chips[data-group="mentor"] .chip')[1]?.click(); await step('멘토 한마디', 700);
    $$('.chips[data-group="next"] .chip')[0]?.click(); await step('다음 목표 하나', 900);
    click('#saveBtn');
    await step('저장 — 다음 연습은 ‘지난번 목표’에서 이어져요', 2200);
    stopDemo('끝 이제 직접 해 보세요');
  } catch (e) { if (demo.token === tk) stopDemo('멈췄어요'); }
}
// 사람이 콘솔을 직접 만지면 시연을 멈춘다
$('#console').addEventListener('pointerdown', (e) => { if (demo.running && e.isTrusted) stopDemo('콘솔을 만져서 멈췄어요'); }, true);


// ══ 평가 모드 · 강습 모드(가상 시뮬레이션) ══════════════════════════════
const simRunning = () => !!sim && session.phase === C.Phase.DRIVING_LOCK;
const VIRTUAL_CHIP = (coach) => `<span class="tag virtual">${coach ? '가상 시뮬레이션 · 제조사 협업 가정' : '가상 시뮬레이션'}</span>`;

function simGuide() {
  const ph = session.phase;
  if (ph === C.Phase.PREPARING && !session.lockReason) {
    if (V.parkingBrakeOn.value) return ['설정을 확인하고 <b>주차 브레이크</b>를 풀어요.', 'r-pb'];
    if (V.gear.value !== 'D') return ['기어를 <b>D</b>로 옮겨 가상 도로로 나가요.', 'r-gear'];
  }
  if (ph === C.Phase.DRIVING_LOCK && sim) {
    const e = sim.ego;
    if (V.gear.value !== 'D' && e.v < 0.3) return V.gear.value === 'P' ? ['<b>주차 브레이크</b>를 걸면 주행 리포트가 열려요.', 'r-pb'] : ['기어를 <b>D</b>로(달리기) 또는 <b>P</b>로(끝내기).', 'r-gear'];
    if (sim.stopReq) return ['강습자가 <b>정지 요청</b>을 했어요. 앱(조수석 패널)에서 해제하면 다시 달려요.', null];
    if (e.v < 0.5 && sim.t < 4) return ['<b>가속 페달</b>을 살살(30~50%) 밟아 출발해요 · 키보드 <b>↑</b>', 'r-accel'];
    if (sim.t < 14) return ['차로 가운데를 지켜요. 핸들은 작게 — 키보드 <b>← →</b>', 'r-steer'];
    if (sim.t < 30) return ['옆 차가 끼어들 수 있어요. 앞차와 <b>2초 거리</b>를 두세요(↓ 브레이크).', 'r-brake'];
    if (sim.t < 50) return ['<b>방향지시등</b>을 켜고 핸들로 옆 차로로 옮겨 보세요. 뒤차 간격은 후방 센서가 봐요.', 'r-turn'];
    return ['끝내려면 <b>브레이크</b>로 멈춘 뒤 기어 P + 주차 브레이크.', e.v > 0.3 ? 'r-brake' : 'r-gear'];
  }
  if (ph === C.Phase.REVIEW && simReport) return ['앱에서 <b>점수와 상황별 조언</b>을 확인해요.', null];
  return null;
}

// ── 준비 화면 ──
Screens.simSetup = () => ({
  mount(el) {
    const coach = appMode === 'coach';
    const rows = Object.values(SIM.EVENTS).filter((x) => x.pts > 0).sort((a, b) => b.pts - a.pts);
    el.innerHTML = `<div class="screen sim-setup">
      <div class="hero"><div class="hero-m">${mascot(coach ? 'calm' : 'happy', 92)}</div><div>
        <div class="tag-row">${VIRTUAL_CHIP(coach)}</div>
        <h1>${coach ? '강습 모드 — 강습자가 함께 타는 차' : '평가 모드 — 가상 도로 주행'}</h1>
        <p class="sub">${coach ? '주행 중 오른쪽 화면은 <b>조수석 강습자 패널</b>이 돼요. 정지 요청, 최고 속도 제한, 주행보조 강도를 강습자가 정해요.' : '전후방·측방 센서(가상)가 급출발·급제동·차선 유지·끼어들기·안전거리·속도를 기록해요. 주차하면 점수와 상황별 조언을 보여 줘요.'}</p>
      </div></div>
      ${coach ? `<section class="panel"><h2>상황별 주행보조 추천</h2><div class="preset-grid" id="presets"></div></section>
      <section class="panel" id="coachDetail"></section>` : `<section class="panel"><h2>이럴 때 점수가 깎여요</h2><div class="rule-grid">${rows.map((r) => `<div class="rule"><b>−${r.pts}</b><span>${r.title}</span>${r.sensor ? `<em>${{ front: '전방', rear: '후방', side: '측방' }[r.sensor]} 센서</em>` : ''}</div>`).join('')}</div>
        <p class="caption">보조 장치는 모두 끈 상태로 평가해요. 잘한 차로 변경·끼어들기 대응은 ‘잘함’으로 따로 남겨요.</p></section>`}
      <div class="banner info">${icon('info', 22)}<span>출발: 주차 브레이크 풀기 → 기어 D → 가속 페달(콘솔 또는 키보드 ↑). 끝: 멈춤 → P → 주차 브레이크</span></div>
      <p class="caption">화면 속 가상 시뮬레이션이에요. 실제 차량을 제어하지 않고, 실제 운전 실력 평가가 아니에요.${coach ? ' 정지 요청·속도 제한은 제조사 협업을 가정한 콘셉트예요.' : ''}</p>
    </div>`;
    if (coach) this.drawCoach();
  },
  drawCoach() {
    $('#presets').innerHTML = Object.entries(SIM.PRESETS).map(([k, p]) => `<button type="button" class="preset${simSettings.preset === k ? ' on' : ''}" data-k="${k}"><b>${p.label}</b><small>${p.desc}</small></button>`).join('');
    $$('#presets .preset').forEach((b) => (b.onclick = () => {
      const k = b.dataset.k;
      simSettings = { ...SIM.PRESETS[k], preset: k }; chime('click'); this.drawCoach();
      $(`#presets .preset[data-k="${k}"]`)?.focus();
    }));
    $('#coachDetail').innerHTML = coachControls(simSettings, false);
    bindCoachControls($('#coachDetail'), simSettings, () => { simSettings.preset = null; this.drawCoach(); });
  },
});

function coachControls(st, live) {
  const seg = (name, opts, cur) => `<div class="seg big" role="radiogroup" data-name="${name}">${opts.map(([v, l]) => `<button type="button" role="radio" data-v="${v}" aria-checked="${String(cur) === String(v)}">${l}</button>`).join('')}</div>`;
  return `<div class="cc-row"><span>최고 속도 제한</span>${seg('cap', [['null', '끔'], [30, '30'], [40, '40'], [50, '50'], [60, '60']], st.cap)}</div>
    <div class="cc-row"><span>차선 유지 보조</span>${seg('lka', [['off', '끄기'], ['normal', '보통'], ['strong', '강하게']], st.lka)}</div>
    <div class="cc-row"><span>크루즈(앞차 따라가기)</span>${seg('acc', [['false', '끔'], ['true', '켬']], st.acc)}${st.acc ? seg('accSet', [[30, '30'], [40, '40'], [50, '50'], [60, '60']], st.accSet) : ''}</div>
    ${live ? '' : '<p class="caption">km/h · 강습자가 주행 중에도 바꿀 수 있어요</p>'}`;
}
function bindCoachControls(root, st, after) {
  $$('.seg[data-name] button', root).forEach((b) => (b.onclick = () => {
    const name = b.parentElement.dataset.name; const v = b.dataset.v;
    st[name] = name === 'lka' ? v : v === 'null' ? null : v === 'true' ? true : v === 'false' ? false : +v;
    chime('click'); after?.();
    // 다시 그린 뒤 같은 버튼으로 포커스를 되돌린다(키보드·조그 다이얼)
    $(`.seg[data-name="${name}"] button[data-v="${cssEsc(v)}"]`, root)?.focus();
  }));
}

// ── 주행 중: 조수석 강습자 패널(강습 모드) ──
Screens.coach = () => ({
  mount(el) {
    el.innerHTML = `<div class="screen coach">
      <h1 class="sr-only">조수석 강습자 패널</h1>
      <div class="coach-head"><span class="lock-tag soft">조수석 강습자 패널</span>${VIRTUAL_CHIP(true)}</div>
      <button type="button" class="stop-btn" id="stopBtn"></button>
      <div class="coach-live" id="coachLive"></div>
      <section class="panel" id="coachCtl"></section>
      <section class="panel"><h2>센서가 본 상황 <small>최근</small></h2><ul class="ev-list" id="coachEv"></ul></section>
      <p class="caption">운전석 화면은 주행 중 조용히 쉬어요. 이 패널은 조수석 쪽 화면을 가정했어요.</p>
    </div>`;
    $('#stopBtn').onclick = () => { if (!sim) return; sim.requestStop(!sim.stopReq); chime('click'); this.update(true); };
    this.drawCtl(); this.evN = -1;
  },
  drawCtl() {
    if (!sim) return;
    $('#coachCtl').innerHTML = `<h2>주행보조 · 속도</h2>${coachControls(sim.settings, true)}`;
    bindCoachControls($('#coachCtl'), sim.settings, () => this.drawCtl());
  },
  update(force) {
    if (!sim) return;
    const b = $('#stopBtn');
    const stop = stopDisplay(sim);
    const k = stop.key;
    if (force || b.dataset.k !== k) {
      b.dataset.k = k;
      b.dataset.requested = String(sim.stopReq);
      b.innerHTML = `${sim.stopReq ? icon('reset', 30) : '<i class="stop-ico"></i>'}<span><b>${sim.stopReq ? '정지 요청 해제' : '정지 요청 (가상)'}</b><small>${stop.text}</small></span>`;
    }
    const sn = sim.sensors();
    $('#coachLive').innerHTML = `<div><b>${Math.round(sim.ego.v * 3.6)}</b><small>km/h${sim.settings.cap ? ` · 최고 ${sim.settings.cap}` : ''}</small></div>
      <div><b>${Number.isFinite(sn.front) ? Math.round(sn.front) + 'm' : '—'}</b><small>앞차 거리${sim.ego.v > 3 && Number.isFinite(sn.front) ? ` · ${(sn.front / sim.ego.v).toFixed(1)}초` : ''}</small></div>
      <div><b>${['왼쪽', '가운데', '오른쪽'][sim.laneOf(sim.ego.x) + 1]}</b><small>차로</small></div>
      <div><b>${Math.max(0, 100 - sim.penalty)}</b><small>현재 점수(가상)</small></div>`;
    if (this.evN !== sim.events.length) {
      this.evN = sim.events.length;
      $('#coachEv').innerHTML = sim.events.slice(-5).reverse().map(evItem).join('') || '<li class="muted">아직 없어요</li>';
    }
  },
});

function evItem(e, i) {
  const sensor = e.sensor ? `<em class="sens">${SENSOR_LABEL[e.sensor]} 센서(가상)</em>` : '';
  const tag = e.pts > 0 ? `<b class="pts bad">−${e.pts}</b>` : e.good ? '<b class="pts good">잘함</b>' : '<b class="pts info">기록</b>';
  const spoke = voice?.didSpeak(e.eventId) ? `<span class="rp-spoke" title="주행 중에 음성으로 말한 항목">${icon('sound', 16)} 말했어요</span>` : '';
  const basis = e.phase === 'ENTRY' ? `<small>2D Mock · 가상 진행 ${{ LEFT: '좌회전', RIGHT: '우회전', STRAIGHT: '직진' }[e.dir] || '미확인'} · 깜빡이 ${{ LEFT: '왼쪽', RIGHT: '오른쪽', NONE: '끔' }[e.basis.signal] || '미확인'} · 진입 ${e.basis.speedKmh.toFixed(1)}km/h · 가상 ${['왼쪽', '가운데', '오른쪽'][e.basis.lane + 1]} 차로</small>` : '';
  return `<li class="${e.pts > 0 ? 'bad' : e.good ? 'good' : 'info'}"><span class="t">${fmtT(e.t)}</span>${tag}<div><b>${e.title}</b> ${sensor}${spoke}<small>${e.kmh}km/h · ${srcChip('MOCK')} · ${e.advice}</small>${basis}${sceneBtn(i, e.t)}</div></li>`;
}

// 리포트 사건 → 리플레이 항목 모양(그래프·재현 창이 같은 모양을 쓴다). 순서는 r.events와 같다.
const reportRows = (r) => r.events.map((e) => ({ atSec: e.t, title: e.title, kind: e.pts > 0 ? 'note' : e.good ? 'good' : 'info', spoken: !!voice?.didSpeak(e.eventId), source: 'MOCK' }));

// ── 주행 리포트 ──
Screens.report = () => ({
  mount(el) {
    const r = simReport; const prev = (H.simReports || [])[1];
    const coach = appMode === 'coach';
    const grade = r.score >= 90 ? '아주 안정적이었어요' : r.score >= 75 ? '안정적이었어요. 몇 가지만 다듬어요' : r.score >= 50 ? '다음엔 아래 두 가지만 신경 써요' : '천천히, 거리 넉넉히부터 다시 해요';
    el.innerHTML = `<div class="screen report">
      <div class="report-top">
        <div class="score-ring" style="--p:${r.score / 100}"><div><b>${r.score}</b><small>/100</small></div></div>
        <div class="report-sum">
          <div class="tag-row">${VIRTUAL_CHIP(coach)}<span class="tag soft">${coach ? '강습 모드' : '평가 모드'}</span></div>
          <h1>${grade}</h1>
          <p class="sub">${prev ? `지난번 ${prev.score}점 → 오늘 ${r.score}점 ${r.score > prev.score ? '— 늘었어요' : r.score === prev.score ? '— 그대로예요' : ''}` : '첫 가상 주행 기록이에요'}</p>
          <div class="stat-row">
            <div><b>${fmtT(r.durationSec)}</b><small>주행 시간</small></div>
            <div><b>${r.distanceKm.toFixed(2)}km</b><small>거리</small></div>
            <div><b>${r.laneKeepPct ?? '—'}${r.laneKeepPct != null ? '%' : ''}</b><small>차로 가운데 유지</small></div>
            <div><b>${r.maxKmh}</b><small>최고 km/h</small></div>
          </div>
        </div>
      </div>
      <section class="cat-grid">${r.cats.map((c) => `<div class="cat ${c.minus ? 'bad' : 'ok'}">${icon(c.icon, 26)}<b>${c.name}</b>
        <span>${c.minus ? `−${c.minus}점 · ${c.bad}번` : '깔끔했어요'}${c.good ? ` · 잘함 ${c.good}` : ''}</span>
        ${c.key === 'lane' && r.laneKeepPct != null ? `<div class="holdbar"><i style="width:${r.laneKeepPct}%"></i></div>` : ''}</div>`).join('')}</section>
      ${r.advice.length ? `<section class="panel advice"><h2>다음엔 이것만</h2>${r.advice.map((a, i) => `<p><b>${i + 1}.</b> ${a.advice} <small>(${a.title})</small></p>`).join('')}</section>` : ''}
      ${timelineSection(reportRows(r), true)}
      <section class="panel"><h2>상황별 기록 <small>${r.events.length}건 · 시간순</small></h2>
        <ul class="ev-list">${r.events.map(evItem).join('') || '<li class="muted">기록된 상황이 없어요. 짧게 달렸거나 아주 안정적이었어요.</li>'}</ul></section>
      <div class="actions two">
        ${btn('histBtn', `${icon('star', 22)} 점수 기록 보기`, 'secondary big')}
        ${btn('againBtn', '다시 달려 보기', 'primary big')}
      </div>
      <p class="caption">가상 시뮬레이션 점수예요. 실제 운전 실력 평가나 면허 기준이 아니에요.</p>
    </div>`;
    bindTimeline(el, reportRows(r), true);
    $('#againBtn').onclick = () => { RP.closePip(); session = C.onReviewDone(session); prep = newPrep(); simReport = null; sim = null; render(); };
    $('#histBtn').onclick = () => openOverlay('history');
  },
});

// ── 왼쪽 운전석 화면: 가상 도로 ──
let previewSim = null;
function drawSimView() {
  const cv = $('#simCanvas'); if (!cv || appMode === 'practice') return;
  const box = cv.parentElement.getBoundingClientRect(); const dpr = devicePixelRatio || 1;
  if (cv.width !== Math.round(box.width * dpr) || cv.height !== Math.round(box.height * dpr)) { cv.width = Math.round(box.width * dpr); cv.height = Math.round(box.height * dpr); }
  const W = cv.width, Hh = cv.height; if (!W || !Hh) return;
  const ctx = cv.getContext('2d');
  const sv = sim || (previewSim ||= new SIM.DriveSim({ ...simSettings }));
  const e = sv.ego; const ppm = Math.min(W / 13.5, Hh / 24);
  const egoY = Hh * 0.68; const X = (x) => W / 2 + x * ppm; const Y = (sw) => egoY - (sw - e.s) * ppm;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.fillStyle = T('--road-bg'); ctx.fillRect(0, 0, W, Hh);
  // 길가 나무(스크롤 감)
  ctx.fillStyle = T('--road-side');
  for (let k = Math.floor((e.s - 40) / 12); k < (e.s + 120) / 12; k++) { const y = Y(k * 12); for (const side of [-1, 1]) { ctx.beginPath(); ctx.arc(X(side * (6.6 + ((k * 7) % 3) * 0.4)), y, ppm * 0.9, 0, Math.PI * 2); ctx.fill(); } }
  ctx.fillStyle = T('--road-asphalt'); ctx.fillRect(X(-5.25), 0, 10.5 * ppm, Hh);
  ctx.strokeStyle = T('--road-line'); ctx.lineWidth = Math.max(2, ppm * 0.15);
  for (const x of [-5.25, 5.25]) { ctx.beginPath(); ctx.moveTo(X(x), 0); ctx.lineTo(X(x), Hh); ctx.stroke(); }
  ctx.setLineDash([3 * ppm, 6 * ppm]); ctx.lineDashOffset = -((e.s * ppm) % (9 * ppm));
  for (const x of [-1.75, 1.75]) { ctx.beginPath(); ctx.moveTo(X(x), 0); ctx.lineTo(X(x), Hh); ctx.stroke(); }
  ctx.setLineDash([]);
  drawJunctions(ctx, sv, W, Hh, ppm, X, Y);
  // 센서 범위
  const sn = sv.sensors(); const hot = sv.flash && sv.t < sv.flash.until ? sv.flash.sensor : null;
  const warnF = Number.isFinite(sn.front) && e.v > 2 && sn.front < e.v * 1.2 + 4;
  const cone = (dir, len, sensor) => {
    const y0 = dir > 0 ? Y(e.s + SIM.CAR_L / 2) : Y(e.s - SIM.CAR_L / 2); const y1 = dir > 0 ? Y(e.s + len) : Y(e.s - len);
    const g = ctx.createLinearGradient(0, y0, 0, y1);
    const col = hot === sensor ? '235,87,87' : sensor === 'front' && warnF ? '242,153,74' : '47,128,237';
    g.addColorStop(0, `rgba(${col},.32)`); g.addColorStop(1, `rgba(${col},0)`);
    ctx.fillStyle = g; ctx.beginPath(); ctx.moveTo(X(e.x - 0.7), y0); ctx.lineTo(X(e.x + 0.7), y0); ctx.lineTo(X(e.x + 1.9), y1); ctx.lineTo(X(e.x - 1.9), y1); ctx.closePath(); ctx.fill();
  };
  cone(1, 45, 'front'); cone(-1, 22, 'rear');
  for (const side of [-1, 1]) {
    const busy = side < 0 ? sn.left : sn.right; const col = hot === 'side' || busy ? TA('--warn-orange', 0.28) : 'rgba(47,128,237,.10)';
    ctx.fillStyle = col; ctx.fillRect(X(e.x + side * 1.1 + (side < 0 ? -2.6 : 0)), Y(e.s + 4), 2.6 * ppm, 10 * ppm);
  }
  // 다른 차
  for (const n of sv.npcs) { const y = Y(n.s); if (y < -60 || y > Hh + 60) continue; carTop(ctx, X(n.x), y, ppm, n.cutter && sv.t - (sv.cutIn.at || 0) < 8 ? T('--warn-orange') : T('--car-npc'), false); }
  carTop(ctx, X(e.x), Y(e.s), ppm, sv.stopReq ? T('--danger-2') : T('--primary'), true, sv.input.signal, sv.t);
  // 거리 표시
  if (Number.isFinite(sn.front) && sn.front < 45) { ctx.fillStyle = warnF ? T('--warn-orange') : T('--primary-text'); ctx.font = `700 ${Math.max(11, ppm * 0.9)}px Pretendard Variable, sans-serif`; ctx.textAlign = 'center'; ctx.fillText(`${Math.round(sn.front)}m`, X(e.x), Y(e.s + SIM.CAR_L / 2 + sn.front / 2)); }
  // HUD
  const hud = $('#simHud');
  const last = sv.events[sv.events.length - 1];
  const recent = last && sv.t - last.t < 2.6 ? last : null;
  const stop = stopDisplay(sv);
  const hk = [sv.settings.cap, sv.settings.lka, sv.settings.acc, sv.settings.accSet, stop.key, recent?.t, appMode].join('|');
  if (hud.dataset.k !== hk) {
    hud.dataset.k = hk;
    hud.innerHTML = `<div class="hud-top"><span class="limit">${SIM.ROAD_LIMIT_KMH}</span>
      ${appMode === 'coach' ? `${sv.settings.cap ? `<span class="hchip cap">최고 ${sv.settings.cap}</span>` : ''}
      <span class="hchip ${sv.settings.lka !== 'off' ? 'on' : ''}">차선 보조 ${{ off: '끔', normal: '보통', strong: '강' }[sv.settings.lka]}</span>
      <span class="hchip ${sv.settings.acc ? 'on' : ''}">크루즈 ${sv.settings.acc ? sv.settings.accSet : '끔'}</span>` : '<span class="hchip">평가 중 · 보조 끔</span>'}</div>
      ${sv.stopReq ? `<div class="hud-stop">${stop.text}</div>` : ''}
      ${recent ? `<div class="hud-ev ${recent.pts > 0 ? 'bad' : recent.good ? 'good' : ''}">${recent.sensor ? `${{ front: '전방', rear: '후방', side: '측방' }[recent.sensor]} 센서 · ` : ''}${recent.title}${recent.pts ? ` −${recent.pts}` : ''}</div>` : ''}`;
  }
}
// 가상 교차로(A04): 가로지르는 도로 + 횡단보도 + 정지선 + 회전 차로 화살표
function drawJunctions(ctx, sv, W, Hh, ppm, X, Y) {
  const e = sv.ego; const first = SIM.nextJunction(e.s - 40);
  for (const J of [first, SIM.junctionAt(first.k + 1)]) {
    const yTop = Y(J.s + SIM.JUNCTION_W), yBot = Y(J.s);
    if (yBot < -40 || yTop > Hh + 40) continue;
    ctx.fillStyle = T('--road-asphalt'); ctx.fillRect(0, yTop, W, yBot - yTop);
    ctx.strokeStyle = T('--road-line'); ctx.lineWidth = Math.max(2, ppm * 0.15);
    // 가로 도로 가운데 점선
    ctx.setLineDash([3 * ppm, 4 * ppm]); ctx.beginPath(); ctx.moveTo(0, (yTop + yBot) / 2); ctx.lineTo(X(-5.25), (yTop + yBot) / 2); ctx.moveTo(X(5.25), (yTop + yBot) / 2); ctx.lineTo(W, (yTop + yBot) / 2); ctx.stroke(); ctx.setLineDash([]);
    // 횡단보도(정지선 앞 4m)와 정지선
    ctx.fillStyle = T('--road-line');
    for (let x = -5; x < 5.2; x += 1) ctx.fillRect(X(x), Y(J.s - 1), 0.5 * ppm, 3 * ppm);
    ctx.fillRect(X(-5.25), Y(J.s - 5), 10.5 * ppm, Math.max(2, 0.3 * ppm));
    // 회전 차로 바닥 화살표(30m 전) — 맨 오른쪽/왼쪽 차로
    const lx = (J.dir === 'RIGHT' ? 1 : -1) * SIM.LANE_W; const ay = Y(J.s - 30); const sgn = J.dir === 'RIGHT' ? 1 : -1;
    ctx.strokeStyle = T('--road-line'); ctx.lineWidth = Math.max(2, ppm * 0.22); ctx.beginPath();
    ctx.moveTo(X(lx), ay + 2.5 * ppm); ctx.lineTo(X(lx), ay); ctx.quadraticCurveTo(X(lx), ay - 1 * ppm, X(lx + sgn * 0.9), ay - 1 * ppm); ctx.stroke();
    ctx.fillStyle = T('--road-line'); ctx.beginPath(); ctx.moveTo(X(lx + sgn * 1.4), ay - 1 * ppm); ctx.lineTo(X(lx + sgn * 0.8), ay - 1.5 * ppm); ctx.lineTo(X(lx + sgn * 0.8), ay - 0.5 * ppm); ctx.closePath(); ctx.fill();
    // 이름표
    const label = `가상 교차로 · ${J.dir === 'RIGHT' ? '우회전' : '좌회전'} 구간`;
    ctx.font = `600 ${Math.max(11, ppm * 0.75)}px Pretendard Variable, sans-serif`; ctx.textAlign = 'center';
    ctx.fillStyle = T('--road-line'); ctx.fillText(label, W / 2, (yTop + yBot) / 2 + ppm * 0.3);
  }
  // 방금 돈 교차로: 1.6초 동안 회전 궤적을 그려 '돌았다'를 보여 준다(주행 화면은 가상 시뮬레이션이다)
  const lt = sv.lastTurn;
  if (lt && sv.t - lt.t < 1.6) {
    const J = SIM.junctionAt(lt.k); const sgn = lt.dir === 'RIGHT' ? 1 : -1; const k = (sv.t - lt.t) / 1.6;
    ctx.strokeStyle = TA('--primary', 0.55 * (1 - k)); ctx.lineWidth = SIM.CAR_W * ppm * 0.6; ctx.lineCap = 'round';
    ctx.beginPath(); ctx.moveTo(X(lt.x), Y(J.s)); ctx.quadraticCurveTo(X(lt.x), Y(J.s + SIM.JUNCTION_W / 2), X(lt.x + sgn * 7), Y(J.s + SIM.JUNCTION_W / 2)); ctx.stroke(); ctx.lineCap = 'butt';
  }
}
function carTop(ctx, cx, cy, ppm, color, ego, signal, t) {
  const w = SIM.CAR_W * ppm, l = SIM.CAR_L * ppm, r = 0.35 * ppm;
  ctx.fillStyle = color; roundRect(ctx, cx - w / 2, cy - l / 2, w, l, r); ctx.fill();
  ctx.fillStyle = ego ? T('--car-glass') : T('--car-glass-npc'); roundRect(ctx, cx - w / 2 + 0.2 * ppm, cy - l / 2 + l * 0.22, w - 0.4 * ppm, l * 0.17, 0.12 * ppm); ctx.fill();
  roundRect(ctx, cx - w / 2 + 0.25 * ppm, cy + l / 2 - l * 0.24, w - 0.5 * ppm, l * 0.1, 0.1 * ppm); ctx.fill();
  if (ego) {
    ctx.fillStyle = T('--car-head'); ctx.fillRect(cx - w / 2 + 0.15 * ppm, cy - l / 2, 0.4 * ppm, 0.1 * ppm); ctx.fillRect(cx + w / 2 - 0.55 * ppm, cy - l / 2, 0.4 * ppm, 0.1 * ppm);
    if (signal && signal !== 'NONE' && Math.floor((t || 0) * 2.5) % 2 === 0) { ctx.fillStyle = T('--car-signal'); const sx = signal === 'LEFT' ? cx - w / 2 - 0.15 * ppm : cx + w / 2 - 0.15 * ppm; ctx.fillRect(sx, cy - l / 2 + 0.1 * ppm, 0.3 * ppm, 0.5 * ppm); ctx.fillRect(sx, cy + l / 2 - 0.6 * ppm, 0.3 * ppm, 0.5 * ppm); }
  }
}

// 시뮬레이션 루프(부드러운 화면용 — 신호 판정은 기존 tick이 그대로 한다)
/**
 * 주행 중 매 프레임: 상황을 알아내 음성 규칙에 넘긴다.
 *  - 연습 모드: 차량 신호만 본다(SignalWatcher) — 실제 앱이 할 일과 같다
 *  - 평가·강습 모드: 가상 주행 시뮬레이션의 기록을 쓴다
 * 어느 쪽이든 화면은 건드리지 않는다.
 */
function pumpVoice() {
  if (!voice) return;
  if (appMode === 'practice') {
    const codes = watcher.step({
      // 변화율은 실제 시간으로 — 빨리 감기 때문에 급출발이 완만해 보이면 안 된다
      tSec: voiceRealSec, kmh: V.speedKmh.value, brakePct: V.brakePedalPct.value,
      turnSignal: V.turnSignal.value, steeringDeg: V.steeringDeg.value,
      wiperOn: V.wiperOn.value, lowBeamOn: V.lowBeamOn.value, gear: V.gear.value,
    });
    for (const code of codes) {
      const eventId = recordVoiceEvent({ t: voiceSec, code, title: VOICE.TITLES[code] || code, kmh: V.speedKmh.value, sensor: null, advice: '' });
      voice.queue(code, voiceSec, V.speedKmh.value, eventId);
    }
  } else if (sim) {
    while (seenSimEvents < sim.events.length) {
      const e = sim.events[seenSimEvents++];
      const eventId = recordVoiceEvent(e);
      if (e.code === 'JUNCTION_AHEAD') {
        if (e.dir === 'LEFT' || e.dir === 'RIGHT') {
          voice.queue(e.dir === 'LEFT' ? 'TURN_LEFT_CAUTION' : 'TURN_RIGHT_CAUTION', e.t, e.kmh, eventId);
        }
      } else if (!e.good && !e.info) voice.queue(e.code, e.t, e.kmh, eventId);
    }
  }
  const nowSec = appMode === 'practice' ? voiceSec : (sim?.t ?? voiceSec);
  const utterance = voice.flush(nowSec);
  if (utterance) void playCoaching(voice, utterance);
  else if (!voice.pending && performance.now() >= mockNavUntil) {
    const praise = voice.considerSteady(nowSec, V.speedKmh.value, nextVoiceEventId + 1);
    if (praise) {
      nextVoiceEventId++;
      void playCoaching(voice, praise);
    }
  }
}

/** 주행 중 시간표 한 장(초당 10장). 사건 시각과 같은 시계를 쓴다(sim: sim.t, 연습: voiceSec) */
function recordTrack() {
  const base = { kmh: V.speedKmh.value, brake: V.brakePedalPct.value, accel: V.accelPedalPct.value, steer: V.steeringDeg.value, sig: V.turnSignal.value };
  if (sim && appMode !== 'practice') {
    track.push({ t: sim.t, ...base, kmh: sim.ego.v * 3.6, x: sim.ego.x, s: sim.ego.s,
      npcs: sim.npcs.filter((n) => Math.abs(n.s - sim.ego.s) < 60).map((n) => [n.id, n.x, n.s, !!(n.cutter && sim.t - (sim.cutIn.at || 0) < 8)]) });
  } else track.push({ t: voiceSec, ...base });
}

/** 복기 화면 공통: 속도 시간표 + 사건 점 + '장면 보기'. 주차 후 화면에서만 부른다. */
function timelineSection(rows, hasSim) {
  if (track.frames.length < 2) return '';
  return `<section class="panel tl"><h2>속도 시간표 <small>점을 누르면 그 장면을 다시 봐요</small></h2>
    ${RP.timelineSvg(track.frames, rows)}${RP.timelineLegend(hasSim)}</section>`;
}
function bindTimeline(root, rows, hasSim) {
  const open = (i, opener) => {
    const r = rows[i]; if (!r) return;
    RP.openPip(root.querySelector('.screen') || root, { frames: track.around(r.atSec), row: r, kind: hasSim ? 'sim' : 'signals', reduced: reduceMotion, opener, sourceLabel: C.SourceLabel[r.source || 'MOCK'] });
  };
  $$('.tl-mk', root).forEach((g) => {
    g.addEventListener('click', () => open(+g.dataset.i, g));
    g.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(+g.dataset.i, g); } });
  });
  $$('[data-scene]', root).forEach((b) => (b.onclick = () => open(+b.dataset.scene, b)));
}
const sceneBtn = (i, atSec) => (track.hasScene(atSec) ? `<button type="button" class="mini scene-btn" data-scene="${i}">장면 보기</button>` : '');

let simLast = performance.now();
let simLoopOn = false; let lastIdleDraw = 0;
function startSimLoop() { if (simLoopOn) return; simLoopOn = true; simLast = performance.now(); requestAnimationFrame(simLoop); }
function simLoop(t) {
  const driving = session.phase === C.Phase.DRIVING_LOCK;
  // 연습 모드에서는 주행 중이 아닐 때 루프를 멈춘다(성능). 주행 중에는 음성 감시 때문에 돌려야 한다.
  if (appMode === 'practice' && !simRunning() && !driving) { simLoopOn = false; return; }
  const dt = siteOverlayOpen() ? 0 : Math.min(0.06, (t - simLast) / 1000); simLast = t;   // 체험판 안내를 읽는 동안 가상 주행 정지
  if (driving) { voiceSec += dt * timeScale; voiceRealSec += dt; pumpVoice(); recordTrack(); }
  if (!simRunning() || overlay) applyKeyboard(keyboardDrive.releaseAll());
  else applyKeyboard(keyboardDrive.sample(dt));
  if (simRunning()) {
    sim.input.accel = V.accelPedalPct.value / 100; sim.input.brake = V.brakePedalPct.value / 100;
    sim.input.steer = V.steeringDeg.value; sim.input.signal = V.turnSignal.value; sim.input.gear = V.gear.value;
    sim.input.maneuver = simManeuver;
    const junctionBefore = sim.junction.k;
    if (V.gear.value === 'P') sim.ego.v = 0;
    const n = Math.max(1, Math.round(dt / 0.02)); for (let i = 0; i < n; i++) sim.step(dt / n);
    if (sim.junction.k !== junctionBefore) setSimManeuver('STRAIGHT');
    const kmh = Math.round(sim.ego.v * 3.6);
    if (kmh !== V.speedKmh.value) setV('speedKmh', kmh);
    setV('uxRestricted', sim.ego.v > 0.3, { onlyIfChanged: true });
    if (V.gear.value !== 'P') setV('tripDistanceKm', +(V.tripDistanceKm.value + (sim.ego.v * dt) / 1000).toFixed(4));
    $('#v-speed').textContent = kmh; $('#c-speed').value = kmh;
  }
  // 주행 중이 아니면 도로 미리보기는 0.4초에 한 번만 그린다(정차 중 CPU 절약)
  if (appMode !== 'practice' && (simRunning() || t - lastIdleDraw > 400)) { drawSimView(); lastIdleDraw = t; }
  requestAnimationFrame(simLoop);
}
startSimLoop();

// 키보드 운전(가상 주행 중): ↑/W 가속 · ↓/S 브레이크 · ←→/A D 핸들 · 스페이스 = 강습자 정지(강습 모드)
function keyDrive(e) {
  const code = e.code || e.key, down = e.type === 'keydown';
  const channel = keyboardDrive.channel(code);
  // 해제는 포커스·오버레이가 바뀌어도 먼저 처리한다.
  if (!down) { if (channel) keyboardDrive.key(code, false); return; }
  if (!simRunning() || overlay || e.repeat || /INPUT|SELECT|TEXTAREA/.test(document.activeElement?.tagName || '') ||
      (document.activeElement?.getAttribute?.('role') === 'slider' && !/^Key[WASD]$/.test(code))) return;
  if (e.code === 'Space' && appMode === 'coach' && !e.target.closest?.('button')) {
    e.preventDefault(); sim.requestStop(!sim.stopReq); return;
  }
  if (!channel) return;
  e.preventDefault();
  if (!keyboardDrive.has(code)) consoleInput[channel]();
  const initial = channel === 'steer' ? V.steeringDeg.value : channel === 'brake' ? V.brakePedalPct.value : V.accelPedalPct.value;
  keyboardDrive.key(code, true, initial);
}
addEventListener('keydown', keyDrive); addEventListener('keyup', keyDrive);
addEventListener('blur', releaseConsoleInputs);
document.addEventListener('visibilitychange', () => { if (document.hidden) releaseConsoleInputs(); });
document.addEventListener('focusin', (e) => { if (/INPUT|SELECT|TEXTAREA/.test(e.target.tagName || '')) applyKeyboard(keyboardDrive.releaseAll()); });

function setAccel(v) { setV('accelPedalPct', clamp(v, 0, 100)); syncConsole(); }

// ── 모드 전환 ──
function setMode(m) {
  if (m === appMode) return;
  if (session.phase === C.Phase.DRIVING_LOCK || session.phase === C.Phase.REVIEW) { flashNote('주차하고 리포트를 닫은 다음 바꿀 수 있어요'); return; }
  appMode = m; H.mode = m; saveHistory();
  sim = null; simReport = null; previewSim = null; judgeState = null;
  if (session.phase === C.Phase.PREPARING) prep = newPrep();
  applyMode(); render();
}
function applyMode() {
  setSimManeuver('STRAIGHT');
  document.body.dataset.mode = appMode;
  $$('#modeSeg button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.v === appMode)));
  $('#simGroup').hidden = appMode === 'practice';
  $('#r-fast').hidden = appMode !== 'practice';
  $('#dvCaption').innerHTML = appMode === 'practice' ? '시스템 Driving view<br><small>앱이 가리지 않는 자리</small>' : `운전석 화면 · 가상 도로<br><small>${appMode === 'coach' ? '강습 모드(제조사 협업 가정)' : '평가 모드'} · 가상 센서</small>`;
  chromeKey = ''; guideKey = '';
  syncConsole();
  startSimLoop();
}

// ── 자동 시연(평가·강습 모드) ──
async function startSimDemo() {
  if (session.phase !== C.Phase.CHECKING && session.phase !== C.Phase.READY) { location.href = `${location.pathname}?demo=1`; return; }
  demo.running = true; const tk = ++demo.token; $('#demoBtn').textContent = '시연 멈추기';
  const alive = () => demo.running && demo.token === tk;
  const step = async (label, ms = 700) => { if (!alive()) throw new Error('stop'); demoBanner(label); await dsleep(ms); if (!alive()) throw new Error('stop'); };
  const click = (sel) => { const el = $(sel); if (el && !el.disabled) el.click(); };
  const waitFor = async (cond, max = 30000) => { const t0 = performance.now(); while (!cond()) { if (performance.now() - t0 > max || !alive()) throw new Error('stop'); await sleep(60); } };
  const ramp = async (fn, from, to, ms) => { ms /= demoSpeed; const t0 = performance.now(); for (;;) { const k = Math.min(1, (performance.now() - t0) / ms); fn(from + (to - from) * k); if (k >= 1) break; await sleep(40); if (!alive()) throw new Error('stop'); } };
  const steerTo = async (lane, sig) => {
    if (sig) setV('turnSignal', sig); syncConsole();
    const target = lane * SIM.LANE_W;
    // 차로 이동 속도는 차 속도에 비례한다(sim.js vy). A05 이후 시연 속 차가 느려져(약 24km/h) 9초로는 모자라 시연이 멈췄다 → 기본 30초
    await waitFor(() => { const dx = target - sim.ego.x; setSteer(clamp(dx * 140, -220, 220)); return Math.abs(dx) < 0.12; });
    setSteer(0); setV('turnSignal', 'NONE'); syncConsole();
  };
  const coach = appMode === 'coach';
  try {
    await waitFor(() => !booting);
    await step(coach ? '강습 모드: 강습자가 정지·속도 제한·주행보조를 조절해요(가상)' : '평가 모드: 가상 센서가 주행을 기록하고 점수를 매겨요', 2000);
    click('#startBtn'); await sleep(500);
    if (coach) { await step('상황별 추천: “익숙해지는 중” — 차선 보조 보통, 최고 60', 1500); $$('#presets .preset')[1]?.click(); }
    await step('주차 브레이크를 풀고 기어 D', 900); click('#c-pb'); await sleep(300); click('#c-gear button[data-v="D"]');
    await waitFor(() => simRunning());
    await step('급출발 — 가속 페달을 한 번에 깊게', 300); await ramp(setAccel, 0, 100, 250);
    await waitFor(() => sim.events.some((x) => x.code === 'HARSH_START'), 4000);
    await step('센서가 급출발을 기록했어요(−3) → 다음부터 살살', 1400); await ramp(setAccel, 100, 35, 600);
    await waitFor(() => sim.ego.v * 3.6 > 42, 15000); setAccel(18);
    await step('차로 가운데 유지 중 — 왼쪽 화면에 전방·후방·측방 센서 범위', 2200);
    await waitFor(() => sim.cutIn.done, 15000);
    await step('옆 차가 앞으로 끼어들었어요 → 전방 센서 감지, 발을 떼고 거리 벌리기', 300);
    setAccel(0); await ramp(setBrake, 0, 22, 400); await sleep(1600); await ramp(setBrake, 22, 0, 400); setAccel(20);
    await waitFor(() => !sim.cutIn.watchUntil, 9000);
    await step('끼어든 차에 잘 대응했어요(잘함)', 1500);
    await step('방향지시등을 켜고 왼쪽 차로로 — 후방 센서가 뒤차 간격 확인', 300); await steerTo(-1, 'LEFT');
    await step('신호 + 충분한 간격 = 차로 변경 잘함', 1500);
    // 시나리오: 오른쪽 차로 뒤에서 빠르게 오는 차를 두고, 신호 없이 끼어들기
    sim.npcs = sim.npcs.filter((n) => !(sim.laneOf(n.x) === 0 && Math.abs(n.s - sim.ego.s) < 40));
    sim.npcs.push({ id: 900, s: sim.ego.s - 13, x: 0, v: sim.ego.v + 2, vd: sim.ego.v + 2 });
    await step('이번엔 신호 없이, 뒤차가 가까운데 오른쪽으로 끼어들면…', 600); await steerTo(0, null);
    await step('후방 센서 감지: 무리한 끼어들기(−8) + 방향지시등 없음(−3)', 2200);
    // 가상 교차로 우회전(A04): 맨 오른쪽 차로 → 교차로 앞에서 속도 줄이고 깜빡이 → 천천히 돌기
    {
      // 차로 이동·감속 중에 목표 교차로를 지나치지 않도록 충분히 먼 구간을 고른다.
      const J = SIM.nextJunction(sim.ego.s + 300); const R = J.dir === 'RIGHT';
      await step(`다음 교차로에서 ${R ? '우' : '좌'}회전 — ${R ? '맨 오른쪽' : '맨 왼쪽'} 차로로 미리 옮겨요`, 300); await steerTo(R ? 1 : -1, J.dir);
      await waitFor(() => J.s - sim.ego.s < 150, 40000);
      await step(`${R ? '우' : '좌'}회전 구간 — 속도를 줄이고 깜빡이 켜기. 음성: “${VOICE.LINES[R ? 'TURN_RIGHT_CAUTION' : 'TURN_LEFT_CAUTION'].line}”`, 300);
      setAccel(0); await ramp(setBrake, 0, 25, 500);
      await waitFor(() => sim.ego.v * 3.6 < 20, 9000); setBrake(0); setAccel(4);
      setSimManeuver(J.dir); setV('turnSignal', J.dir); syncConsole();
      await waitFor(() => sim.junction.k >= J.k, 35000);
      setV('turnSignal', 'NONE'); syncConsole();
      const good = sim.lastTurn?.k === J.k && sim.events.some((x) => x.code === 'TURN_GOOD' && x.t >= sim.lastTurn.t);
      await step(good ? '천천히, 알맞은 차로에서 돌았어요 — 교차로 회전 잘함' : '회전 기록이 남았어요 — 주차 후 리포트에서 다시 봐요', 1600); setAccel(25);
    }
    if (coach) {
      await step('강습자: “최고 40km/h로 제한”', 600); $$('#coachCtl .seg[data-name="cap"] button')[2]?.click(); setAccel(60);
      await sleep(2600);
      await step('가속 페달을 밟아도 40km/h에서 더 안 올라가요(가상)', 1600);
      await step('강습자: 정지 요청 버튼', 600); click('#stopBtn');
      await waitFor(() => sim.ego.v < 0.2, 12000); setAccel(0);
      await step('차가 부드럽게 멈췄어요. 운전자 감점은 없어요', 1800);
      click('#stopBtn'); await sleep(400);
    } else {
      await step('급제동 — 브레이크를 한 번에 세게', 300); setAccel(0); await ramp(setBrake, 0, 100, 150);
      await waitFor(() => sim.ego.v < 0.3, 8000); await step('급제동 기록(−4): 멈출 곳을 미리 보고 일찍', 1500);
    }
    setBrake(60); await waitFor(() => sim.ego.v < 0.2, 8000); setBrake(0);
    await step('주차: 기어 P + 주차 브레이크', 700); click('#c-gear button[data-v="P"]'); await sleep(300); click('#c-pb');
    await waitFor(() => session.phase === C.Phase.REVIEW);
    await step('주행 리포트: 점수 · 항목별 · 상황별 조언', 2500);
    $('#appBody').scrollTo({ top: 400, behavior: 'smooth' }); await step('상황별 기록 — 어느 센서가 무엇을 봤는지', 2500);
    stopDemo('끝 이제 직접 달려 보세요(키보드 ↑↓←→)');
  } catch (err) { if (demo.token === tk) stopDemo('멈췄어요'); }
}

// ── 시작 ─────────────────────────────────────────────────────
$('#brandMascot').innerHTML = mascot('calm', 40);
$('#topMascot').innerHTML = mascot('calm', 30);
$('#dvArt').innerHTML = drivingViewArt();
$$('.gnb-app, .gnb-ico, .ps-ico').forEach((el) => el.insertAdjacentHTML('afterbegin', icon(el.dataset.ico, 21)));
// 상태 표시줄 시계(장식)
const tickClock = () => { const d = new Date(); $('#psTime').textContent = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`; };
tickClock(); setInterval(tickClock, 20000);
$('#historyBtn .ib-ico').innerHTML = icon('star', 22);
$('#profileBtn .ib-ico').innerHTML = icon('user', 22);
$('#consoleFab .fab-ico').innerHTML = icon('console', 22);
// C04: 앱 안의 창(내 기록·설정)은 주행 중에 열지 않는다(S04). 전에는 누르면 아무 반응이 없다가
// 주차하는 순간 갑자기 떴다(누른 기록이 남아서) → 주행 중에는 누른 것을 무시하고, 버튼에 이유를 적어 둔다.
function appOverlayLocked() { return session.phase === C.Phase.DRIVING_LOCK; }   // 함수 선언: renderChrome이 먼저 불려도 안전
function syncAppButtons() {
  const locked = appOverlayLocked();
  for (const id of ['historyBtn', 'profileBtn']) {
    const b = $('#' + id); if (!b) continue;
    b.setAttribute('aria-disabled', String(locked));
    b.classList.toggle('is-locked', locked);
    b.title = locked ? '주행 중에는 열지 않아요. 기어 P + 주차 브레이크를 걸면 열려요' : '';
  }
}
$('#historyBtn').onclick = () => { if (!appOverlayLocked()) openOverlay('history'); };

// ── C04: 하단 바(시스템 영역) — 데모에서 열리지 않는 버튼을 누르면 이유를 말해 준다 ──
// 전에는 아무 반응이 없어 고장처럼 보였다. 말풍선은 앱 화면이 아니라 하단 바 위(시스템 영역)에 띄운다.
// 주행 중에는 움직임 없이 글자만 바뀐다(S04 — 앱 화면은 그대로).
const GNB_NAME = { road: '내비', music: '미디어', phone: '전화', grid: '앱 라이브러리', home: '홈', gear: '차량 설정', defrost: '서리 제거', seatHeat: '시트 열선' };
let gnbTipTimer = 0;
/** 받침에 맞는 조사: 내비는 / 홈은 / 공조 온도는 */
const eunNeun = (w) => { const c = w.charCodeAt(w.length - 1); return c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) % 28 ? `${w}은` : `${w}는`; };
function gnbNotice(text) {
  const tip = $('#gnbTip'); if (!tip) return;
  tip.textContent = text; tip.hidden = false;
  tip.classList.toggle('still', session.phase === C.Phase.DRIVING_LOCK);
  clearTimeout(gnbTipTimer); gnbTipTimer = setTimeout(() => { tip.hidden = true; }, 2600);
}
function bindGnb() {
  const items = $$('.pleos-gnb .gnb-app, .pleos-gnb .gnb-ico, .pleos-gnb .gnb-climate');
  for (const el of items) {
    const key = el.dataset.ico;
    const self = el.classList.contains('on');
    const name = el.classList.contains('gnb-climate') ? '공조 온도' : (GNB_NAME[key] || el.textContent.trim() || el.title);
    el.setAttribute('role', 'button'); el.tabIndex = 0;
    el.setAttribute('aria-label', self ? '치일까봐 — 지금 열려 있는 앱' : `${name} — 데모에서는 열리지 않아요`);
    const go = () => gnbNotice(self ? '지금 보고 있는 치일까봐예요'
      : el.classList.contains('gnb-climate') || ['defrost', 'seatHeat', 'gear'].includes(key)
        ? `${eunNeun(name)} 차가 직접 다루는 기능이라 데모에서는 바꿀 수 없어요. 치일까봐는 차를 조작하지 않아요`
        : `${eunNeun(name)} 실제 차에서 열리는 앱이에요. 이 데모에서는 치일까봐만 열려요`);
    el.addEventListener('click', go);
    el.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(); } });
  }
}
bindGnb();
$('#profileBtn').onclick = () => { if (!appOverlayLocked()) openOverlay('profile'); };
$('#helpBtn').onclick = () => { obStep = 0; openOverlay('intro'); };
$('#aboutBtn').onclick = () => openOverlay('about');
$('#demoSpeed').onchange = (e) => { demoSpeed = +e.target.value || 1; };
$('#demoBtn').onclick = () => (demo.running ? stopDemo('멈췄어요') : startDemo());
addEventListener('keydown', (e) => { if (e.key === 'Escape' && overlay) closeOverlay(); });
THEME.init();
THEME.onChange(repaintForTheme);
applyPrefs();
syncThemeBtn();
$('#themeBtn').onclick = () => THEME.toggle();
bindConsole(); syncConsole();
$$('#modeSeg button').forEach((b) => (b.onclick = () => setMode(b.dataset.v)));
applyMode();
render();
setInterval(tick, 100);
if (new URLSearchParams(location.search).has('demo')) setTimeout(startDemo, 200);
else if (!H.introSeen || (H.introV || 1) < INTRO_V) setTimeout(() => { obStep = 0; openOverlay('intro'); }, 300);
