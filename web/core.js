// 치일까봐 웹 프로토타입 — core 로직
// app/src/main/java/com/chiilkkabwa/app/core/* (Kotlin)를 1:1에 가깝게 옮긴 것.
// 규칙(CLAUDE.md §5): 모르면 잠근다 / 시작 이후의 새 관측만 성공 / 출처 라벨을 섞지 않는다.

export const Status = {
  OK: 'OK', UNKNOWN: 'UNKNOWN', UNSUPPORTED: 'UNSUPPORTED',
  PERMISSION_DENIED: 'PERMISSION_DENIED', DISCONNECTED: 'DISCONNECTED',
};
export const SourceLabel = { VEHICLE: '실차 신호', EMULATOR: '에뮬레이터 신호', MOCK: '모의' };
export const StatusText = {
  UNSUPPORTED: '이 차에서는 이 신호를 읽을 수 없어요.',
  PERMISSION_DENIED: '이 신호를 읽을 권한이 없어요.',
  DISCONNECTED: '차량 신호가 잠시 끊겼어요.',
  UNKNOWN: '아직 신호를 받지 못했어요.',
};

export const usable = (s) => !!s && s.status === Status.OK && s.value !== null && s.value !== undefined;
const blocking = (s) =>
  [Status.UNSUPPORTED, Status.PERMISSION_DENIED, Status.DISCONNECTED].includes(s.status) ? s.status : null;

// ── 안전 게이트 ───────────────────────────────────────────────
export const LockReason = {
  DRIVING_RESTRICTED: '연습 중 — 화면은 잠시 쉴게요. 도로만 보세요.',
  NOT_IN_PARK: '기어를 P에 두면 시작할 수 있어요.',
  PARKING_BRAKE_OFF: '주차 브레이크를 걸면 시작할 수 있어요.',
  UNKNOWN_STATE: '차량 상태를 확인하고 있어요. 기어 P와 주차 브레이크를 확인해 주세요.',
};

export const DEFAULT_POLICY = { requireParkingBrake: true, allowWhenParkingBrakeUnsupported: false };

export function evaluateGate(s, policy = DEFAULT_POLICY) {
  const ux = s.uxRestricted;
  if (usable(ux) && ux.value === true) return { ready: false, reason: 'DRIVING_RESTRICTED' };
  if (!usable(ux) || !usable(s.gear)) return { ready: false, reason: 'UNKNOWN_STATE' };
  if (s.gear.value !== 'P') return { ready: false, reason: 'NOT_IN_PARK' };
  if (policy.requireParkingBrake) {
    const pb = s.parkingBrakeOn;
    if (usable(pb) && pb.value === true) { /* ok */ }
    else if (usable(pb) && pb.value === false) return { ready: false, reason: 'PARKING_BRAKE_OFF' };
    else if (pb.status === Status.UNSUPPORTED && policy.allowWhenParkingBrakeUnsupported) { /* ok */ }
    else return { ready: false, reason: 'UNKNOWN_STATE' };
  }
  return { ready: true, reason: null };
}

// ── 미션 판정 ─────────────────────────────────────────────────
const progress = (step, total, hint, extra = {}) => ({ kind: 'progress', step, total, hint, ...extra });
const done = (source, praise, metric = null) => ({ kind: 'done', source, praise, metric });
const unavailable = (status) => ({ kind: 'unavailable', status });

/** 값 변화(edge)를 '시작 이후'로만 추적 */
export class EdgeTracker {
  reset(sig, now) { this.startedAt = now; this.last = sig.value; this.lastAt = sig.receivedAt; }
  newValue(sig) {
    if (!usable(sig)) return null;
    if (sig.receivedAt < this.startedAt) return null;
    if (sig.receivedAt === this.lastAt && sig.value === this.last) return null;
    const changed = sig.value !== this.last;
    this.last = sig.value; this.lastAt = sig.receivedAt;
    return changed ? sig.value : null;
  }
}

/** 안전벨트는 조작 이벤트가 아닌 체결 상태 확인 미션(S08)이다.
 * 이미 체결한 벨트는 풀지 않으며, 미지원/확인 불가 상태는 성공으로 처리하지 않는다.
 * 브레이크·방향지시등의 시작 이후 edge 판정은 유지한다.
 */
export function seatBeltJudge() {
  return {
    id: 'SEAT_BELT', title: '안전벨트 매기',
    start() { /* 상태 확인에는 과거 변화 기록이 필요 없다. */ },
    update(s) {
      const belt = s.driverBeltBuckled;
      const blocked = blocking(belt); if (blocked) return unavailable(blocked);
      if (!usable(belt)) return progress(1, 1, '안전벨트 상태를 확인 중이에요. 이미 맸다면 그대로 유지해 주세요.');
      return belt.value === true
        ? done(belt.source, '안전벨트가 체결되어 있어요. 그대로 유지해 주세요.')
        : progress(1, 1, '안전벨트를 매 주세요. 이미 맸다면 풀지 말고 그대로 두세요.');
    },
  };
}

export function brakePressReleaseJudge() {
  const edge = new EdgeTracker();
  let pressed = false, maxPct = null;
  return {
    id: 'BRAKE_PRESS_RELEASE', title: '브레이크 끝까지 밟고 천천히 떼기',
    start(s, now) { edge.reset(s.brakePressed, now); pressed = false; maxPct = null; },
    update(s) {
      const b = blocking(s.brakePressed); if (b) return unavailable(b);
      if (pressed && usable(s.brakePedalPct)) maxPct = Math.max(maxPct ?? 0, s.brakePedalPct.value);
      const v = edge.newValue(s.brakePressed);
      if (v === true) pressed = true;
      if (v === false && pressed) return done(s.brakePressed.source, '끝까지 꾹, 그리고 천천히 떼기 — 좋아요', maxPct);
      return !pressed
        ? progress(1, 2, '오른발로 브레이크를 끝까지 밟아 주세요')
        : progress(2, 2, '좋아요. 이제 천천히 떼 볼까요?');
    },
  };
}

export function turnSignalJudge() {
  const edge = new EdgeTracker();
  let step = 0, retry = false;
  return {
    id: 'TURN_SIGNAL', title: '방향지시등 왼쪽 → 오른쪽',
    start(s, now) { edge.reset(s.turnSignal, now); step = 0; retry = false; },
    update(s) {
      const b = blocking(s.turnSignal); if (b) return unavailable(b);
      const v = edge.newValue(s.turnSignal);
      if (v !== null) retry = false;
      if (step === 0 && v === 'LEFT') step = 1;
      else if (step === 1 && v === 'RIGHT') step = 2;
      else if (step === 2 && v === 'NONE') return done(s.turnSignal.source, '깜빡이, 손가락 하나로 충분해요');
      else if (v !== null && v !== 'NONE') retry = true; // 순서가 다를 때: 실패가 아니라 '한 번 더'
      const hint = ['왼쪽 방향지시등을 켜 보세요(레버 아래로)', '이번엔 오른쪽(레버 위로)', '이제 꺼 볼까요?'][step];
      return progress(step + 1, 3, hint, { retry: retry ? '거의 다 왔어요. 한 번 더' : null });
    },
  };
}

export function toggleJudge(id, title, key, hint, praise) {
  const edge = new EdgeTracker();
  return {
    id, title,
    start(s, now) { edge.reset(s[key], now); },
    update(s) {
      const b = blocking(s[key]); if (b) return unavailable(b);
      return edge.newValue(s[key]) !== null ? done(s[key].source, praise) : progress(1, 1, hint);
    },
  };
}

export function steeringCenterJudge(toleranceDeg = 5, holdMs = 2000) {
  let startedAt = 0, since = null;
  return {
    id: 'STEERING_CENTER', title: '핸들 똑바로 맞추기',
    start(s, now) { startedAt = now; since = null; },
    update(s, now) {
      const sig = s.steeringDeg;
      const b = blocking(sig); if (b) return unavailable(b);
      if (usable(sig) && sig.receivedAt >= startedAt && Math.abs(sig.value) <= toleranceDeg) {
        if (since === null) since = now;
        if (now - since >= holdMs) return done(sig.source, '바퀴가 똑바로 출발 전에 늘 확인해요');
        return progress(1, 1, '좋아요, 그대로 2초만 기다려요', { holdRatio: (now - since) / holdMs });
      } else if (usable(sig) && Math.abs(sig.value) > toleranceDeg) since = null;
      return progress(1, 1, '핸들을 가운데로 맞추고 2초 기다려요', { holdRatio: 0 });
    },
  };
}

/** 페달 감각 '살살 밟기'. 웹 프로토타입은 브레이크 페달 압력을 쓴다(PRD F5 대안 — 가속 페달은 실차에서 열지 않음). */
export function pedalHoldJudge(key = 'brakePedalPct', low = 20, high = 35, holdMs = 2000) {
  let startedAt = 0, since = null, samples = [];
  const std = (xs) => {
    if (xs.length < 2) return 0;
    const m = xs.reduce((a, b) => a + b, 0) / xs.length;
    return Math.sqrt(xs.reduce((a, x) => a + (x - m) ** 2, 0) / xs.length);
  };
  return {
    id: 'PEDAL_FEEL', title: '초록 칸 안에서 2초 버티기', low, high,
    start(s, now) { startedAt = now; since = null; samples = []; },
    update(s, now) {
      const sig = s[key];
      const b = blocking(sig); if (b) return unavailable(b);
      if (usable(sig) && sig.receivedAt >= startedAt) {
        const v = sig.value;
        if (v >= low && v <= high) {
          if (since === null) since = now;
          samples.push(v);
          if (now - since >= holdMs) return done(sig.source, '부드러웠어요', std(samples));
          return progress(1, 1, '좋아요, 그대로', { holdRatio: (now - since) / holdMs });
        }
        since = null; samples = [];
      }
      return progress(1, 1, `초록 칸(${low}~${high}%) 안에서 2초 버텨 보세요`, { holdRatio: 0 });
    },
  };
}

export const MissionCatalog = {
  required: () => [seatBeltJudge(), brakePressReleaseJudge(), turnSignalJudge()],
  optional: () => [
    toggleJudge('WIPER', '와이퍼 켜고 끄기', 'wiperOn', '와이퍼 레버를 한 번 움직여 보세요', '비 오는 날 이 레버, 기억해 둬요'),
    toggleJudge('LOW_BEAM', '전조등 켜기', 'lowBeamOn', '전조등 스위치를 돌려 보세요', '어두워지면 여기'),
    steeringCenterJudge(),
  ],
};

// ── 세션 상태 기계 ───────────────────────────────────────────
export const Phase = { CHECKING: 'CHECKING', READY: 'READY', PREPARING: 'PREPARING', DRIVING_LOCK: 'DRIVING_LOCK', REVIEW: 'REVIEW' };

export const initialSession = () => ({ phase: Phase.CHECKING, lockReason: 'UNKNOWN_STATE', tripStart: null, tripEnd: null });

const mark = (s, now) => ({
  distanceKm: usable(s.tripDistanceKm) ? s.tripDistanceKm.value : null,
  tripTimeSec: usable(s.tripTimeSec) ? s.tripTimeSec.value : null,
  wallClockMs: now,
});

export function onGate(state, gate, s, now) {
  const locked = gate.ready ? null : gate.reason;
  const drivingLike = locked === 'DRIVING_RESTRICTED' || locked === 'NOT_IN_PARK';
  switch (state.phase) {
    case Phase.CHECKING:
    case Phase.READY:
      return locked === null ? { ...state, phase: Phase.READY, lockReason: null } : { ...state, phase: Phase.CHECKING, lockReason: locked };
    case Phase.PREPARING:
      // Kotlin 원본은 Ready로 돌아와도 lockReason을 지우지 않아 '일시 정지'가 풀리지 않는다 → 여기서는 지운다(docs/09에 기록).
      if (locked === null) return state.lockReason ? { ...state, lockReason: null } : state;
      if (drivingLike) return { ...state, phase: Phase.DRIVING_LOCK, lockReason: locked, tripStart: mark(s, now), tripEnd: null };
      return { ...state, lockReason: locked };
    case Phase.DRIVING_LOCK:
      return locked === null ? { ...state, phase: Phase.REVIEW, lockReason: null, tripEnd: mark(s, now) } : { ...state, lockReason: locked };
    case Phase.REVIEW:
      // E3(C01): 복기는 주차가 '지금도' 확인될 때만 열린다. 주행이 아닌 잠금(주차 브레이크 해제, 신호 UNKNOWN/STALE 등)도
      // 복기를 잠근다. 단계는 REVIEW로 남겨 작성 중인 복기를 지키고, 다시 주차가 확인되면 이어서 연다.
      if (locked === null) return state.lockReason ? { ...state, lockReason: null } : state;
      if (drivingLike) return { ...state, phase: Phase.DRIVING_LOCK, lockReason: locked, tripStart: mark(s, now), tripEnd: null };
      return state.lockReason === locked ? state : { ...state, lockReason: locked };
    default: return state;
  }
}
export const onStartPressed = (st) => (st.phase === Phase.READY ? { ...st, phase: Phase.PREPARING } : st);
export const onReviewDone = (st) => ({ ...st, phase: Phase.PREPARING, tripStart: null, tripEnd: null });

export function summarize(start, end) {
  if (!start || !end) return null;
  const dist = start.distanceKm != null && end.distanceKm != null && end.distanceKm >= start.distanceKm ? end.distanceKm - start.distanceKm : null;
  const vehSec = start.tripTimeSec != null && end.tripTimeSec != null && end.tripTimeSec >= start.tripTimeSec ? end.tripTimeSec - start.tripTimeSec : null;
  const sec = vehSec ?? Math.floor((end.wallClockMs - start.wallClockMs) / 1000);
  return { distanceKm: dist, durationMin: Math.floor(sec / 60), durationFromVehicle: vehSec != null };
}

// ── 성취 ──────────────────────────────────────────────────────
export const Badges = {
  FIRST_CLICK: { title: '첫 딸깍', desc: '처음으로 안전벨트 미션을 해냈어요', icon: 'belt', hue: 150 },
  WARMUP_DONE: { title: '운전석 워밍업', desc: '워밍업 필수 미션 3개를 모두 마쳤어요', icon: 'seat', hue: 205 },
  SOFT_FOOT: { title: '살살 밟기', desc: '페달을 초록 칸 안에서 2초 유지했어요', icon: 'pedal', hue: 130 },
  PARKING_IMAGE: { title: '첫 주차 연습', desc: '가상 주차를 끝까지 해 봤어요', icon: 'parking', hue: 220 },
  PROMISE: { title: '오늘의 약속', desc: '멘토와 연습 약속을 정했어요', icon: 'pact', hue: 35 },
  FIRST_REVIEW: { title: '첫 복기', desc: '연습 후 오늘을 돌아봤어요', icon: 'note', hue: 265 },
  THREE_SESSIONS: { title: '세 번째 연습', desc: '연습을 세 번 이어 갔어요', icon: 'three', hue: 190 },
  GRADUATE: { title: '혼자 출발', desc: '‘혼자 운전 시작’을 선택했어요 — 수료 카드가 만들어졌어요', icon: 'flag', hue: 45 },
  MENTOR_RETURN: { title: '이번엔 멘토', desc: '다른 사람의 연습에 멘토로 함께했어요', icon: 'mentor', hue: 330 },
};
export const REQUIRED_WARMUP = ['SEAT_BELT', 'BRAKE_PRESS_RELEASE', 'TURN_SIGNAL'];

export function newlyEarned(h, now) {
  const have = new Set(h.badges.map((b) => b.badge));
  const out = [];
  const add = (b, mock) => { if (!have.has(b) && !out.some((o) => o.badge === b)) out.push({ badge: b, at: now, mockOnly: mock }); };
  const mockOnly = (id) => h.missionResults.filter((r) => r.id === id).every((r) => r.source === 'MOCK');
  const doneIds = new Set(h.missionResults.map((r) => r.id));
  if (doneIds.has('SEAT_BELT')) add('FIRST_CLICK', mockOnly('SEAT_BELT'));
  if (REQUIRED_WARMUP.every((id) => doneIds.has(id))) add('WARMUP_DONE', REQUIRED_WARMUP.every(mockOnly));
  if (doneIds.has('PEDAL_FEEL')) add('SOFT_FOOT', mockOnly('PEDAL_FEEL'));
  if (h.parkingSimCompleted >= 1) add('PARKING_IMAGE', false);
  if (h.pactsMade >= 1) add('PROMISE', false);
  if (h.reviewsSaved >= 1) add('FIRST_REVIEW', false);
  if (h.reviewsSaved >= 3) add('THREE_SESSIONS', false);
  if (h.graduated) add('GRADUATE', false);
  if (h.mentorSessions >= 1) add('MENTOR_RETURN', false);
  return out;
}

/** 레벨 1~5 = 복기 횟수 기반. 실력이 아니라 '이어 간 연습 횟수'. */
export const level = (h) => (h.reviewsSaved >= 10 ? 5 : h.reviewsSaved >= 6 ? 4 : h.reviewsSaved >= 3 ? 3 : h.reviewsSaved >= 1 ? 2 : 1);
export const LEVEL_STEPS = [0, 1, 3, 6, 10];
export const LEVEL_NAMES = ['첫 시동', '한 번 돌아봄', '이어 가는 중', '꾸준히 연습 중', '열 번의 연습'];

// ── 가상 주차 ─────────────────────────────────────────────────
// 자전거 모델, 뒤축 중심 기준. 좌표: 미터, x 오른쪽, y 아래, heading 0 = 화면 위.
export class ParkingSim {
  constructor(dims, steeringRatio = 15, maxWheelDeg = 35) {
    this.dims = dims;
    this.steeringRatio = steeringRatio; this.maxWheelDeg = maxWheelDeg;
    this.wheelbase = (dims.wheelbaseMm ?? Math.round(dims.lengthMm * 0.58)) / 1000;
    this.length = dims.lengthMm / 1000; this.width = dims.widthMm / 1000;
    this.overhang = (this.length - this.wheelbase) / 2; // 앞뒤 오버행을 같다고 근사
  }
  wheelAngleRad(steeringDeg) {
    const w = Math.max(-this.maxWheelDeg, Math.min(this.maxWheelDeg, steeringDeg / this.steeringRatio));
    return (w * Math.PI) / 180;
  }
  get minRadius() { return this.wheelbase / Math.tan((this.maxWheelDeg * Math.PI) / 180); }
  step(p, steeringDeg, speed, dt) {
    const delta = this.wheelAngleRad(steeringDeg);
    const dist = speed * dt;
    const h = p.heading + (dist / this.wheelbase) * Math.tan(delta);
    return { x: p.x + dist * Math.sin(h), y: p.y - dist * Math.cos(h), heading: h };
  }
  /** 차체 중심(뒤축 중심에서 앞으로 wheelbase/2) */
  bodyCenter(p) {
    const d = this.wheelbase / 2;
    return { x: p.x + d * Math.sin(p.heading), y: p.y - d * Math.cos(p.heading) };
  }
  /** 차체 네 모서리(화면 좌표) */
  corners(p) {
    const c = this.bodyCenter(p);
    const hl = this.length / 2, hw = this.width / 2;
    const fx = Math.sin(p.heading), fy = -Math.cos(p.heading); // 앞 방향
    const rx = Math.cos(p.heading), ry = Math.sin(p.heading); // 오른쪽 방향
    return [[hl, -hw], [hl, hw], [-hl, hw], [-hl, -hw]].map(([f, r]) => ({ x: c.x + f * fx + r * rx, y: c.y + f * fy + r * ry }));
  }
  /** 칸 안 판정. 웹판은 차체 중심 기준(뒤축 기준이면 차가 칸 밖으로 튀어나와도 성공이 됨) */
  parkState(p, slot, posTol = 0.35, headingTolDeg = 6) {
    const c = this.bodyCenter(p);
    const dx = Math.abs(c.x - slot.cx), dy = Math.abs(c.y - slot.cy);
    let hd = ((p.heading * 180) / Math.PI) % 180; if (hd < 0) hd += 180;
    const headingErr = Math.min(hd, 180 - hd);
    return { ok: dx <= posTol && dy <= posTol && headingErr <= headingTolDeg, dx, dy, headingErr };
  }
}

/** 두 볼록 사각형이 겹치는지(분리축) */
export function polysOverlap(a, b) {
  for (const poly of [a, b]) {
    for (let i = 0; i < poly.length; i++) {
      const p1 = poly[i], p2 = poly[(i + 1) % poly.length];
      const nx = p2.y - p1.y, ny = p1.x - p2.x;
      const proj = (P) => P.map((q) => q.x * nx + q.y * ny);
      const pa = proj(a), pb = proj(b);
      if (Math.max(...pa) < Math.min(...pb) || Math.max(...pb) < Math.min(...pa)) return false;
    }
  }
  return true;
}

export const CONTENT = {
  goals: ['출발·정지', '주차', '차선 바꾸기', '좌회전', '그냥 몸풀기'],
  pacts: ['멈출 땐 한 단어로 “멈춰”', '주차 땐 “핸들 반 바퀴”처럼 숫자로', '잘하면 바로 말해 주기', '한 번에 하나만 말하기', '조작하기 전에 먼저 물어보기', '큰 소리 내지 않기'],
  PACT_MAX: 3,
  learner: ['자신감 생겼어요', '후진이 아직 무서워요', '말이 너무 빨랐어요', '출발이 어려웠어요', '생각보다 괜찮았어요'],
  mentor: ['속도 조절이 좋아졌어', '핸들 감각이 늘었어', '주변 확인을 잘했어', '다음엔 후진 연습하자', '오늘은 쉬어 가자'],
  nextGoals: ['후진 주차 속도', '평행 주차 기준점', '출발할 때 부드럽게', '차선 바꾸기 전 확인', '좌회전 타이밍'],
  retry: ['조금만 더 천천히 해 볼까요?', '거의 다 왔어요. 한 번 더', '괜찮아요, 처음엔 다 그래요'],
};

export const MISSION_META = {
  SEAT_BELT: { icon: 'belt', know: '출발 전 전 좌석 안전벨트', car: '‘딸깍’ 소리가 날 때까지', signal: 'driverBeltBuckled' },
  BRAKE_PRESS_RELEASE: { icon: 'brake', know: '브레이크는 오른발, 뒤꿈치는 바닥에', car: '끝까지 밟았다가 천천히', signal: 'brakePedalPct' },
  TURN_SIGNAL: { icon: 'turn', know: '진로를 바꾸기 30m 전에 신호', car: '레버 아래 = 왼쪽, 위 = 오른쪽', signal: 'turnSignal' },
  WIPER: { icon: 'wiper', know: '비 오는 날은 시야부터', car: '와이퍼 레버 위치 익히기', signal: 'wiperOn', optional: true },
  LOW_BEAM: { icon: 'beam', know: '해 질 녘·터널에서는 전조등', car: '스위치를 돌려 켜기', signal: 'lowBeamOn', optional: true },
  STEERING_CENTER: { icon: 'wheel', know: '출발 전 바퀴는 똑바로', car: '핸들 가운데 ±5°에서 2초', signal: 'steeringDeg', optional: true },
};
