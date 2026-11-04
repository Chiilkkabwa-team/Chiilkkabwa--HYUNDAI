// 치일까봐 웹 프로토타입 — 주행 시뮬레이션(평가 모드·강습 모드 콘셉트)
// ※ 전부 화면 속 가상 시뮬레이션이다. 실제 차량 신호·제어와 무관하다.
//   - 전후방·측방 '센서'는 시뮬레이션 안의 가상 레이더다(공개 SDK에서 이 값을 받을 수 있는지는 미확인).
//   - 강습자 정지 요청·최고 속도 제한·주행보조 강도는 '제조사 협업 가정' 콘셉트다(CLAUDE.md §5: 실제 제어 구현 금지).

export const LANE_W = 3.5;
export const CAR_L = 4.7;
export const CAR_W = 1.83;
export const ROAD_LIMIT_KMH = 60;
const KMH = 1 / 3.6;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

/** 상황 코드 → 제목·감점·조언·감지 센서 */
export const EVENTS = {
  HARSH_START: { title: '급출발', pts: 3, sensor: null, cat: 'smooth', advice: '가속 페달은 3초에 걸쳐 천천히 깊게. 출발은 “살살”이 기본이에요.' },
  HARSH_BRAKE: { title: '급제동', pts: 4, sensor: null, cat: 'smooth', advice: '멈출 곳을 미리 보고 일찍, 두 번에 나눠 밟아요.' },
  HARSH_BRAKE_NEEDED: { title: '급제동(앞차 가까움)', pts: 2, sensor: 'front', cat: 'smooth', advice: '앞차와 거리가 좁아서 세게 밟아야 했어요. 거리를 2초 이상 두면 급제동이 줄어요.' },
  LANE_DRIFT: { title: '차선 밟음', pts: 2, sensor: 'side', cat: 'lane', advice: '시선을 멀리 두면 차가 차로 가운데로 가요. 핸들은 작게 자주.' },
  ROAD_EDGE: { title: '도로 가장자리 접근', pts: 3, sensor: 'side', cat: 'lane', advice: '가장자리 선이 보이면 핸들을 반대로 조금만 돌려요.' },
  NO_SIGNAL: { title: '방향지시등 없이 차로 변경', pts: 3, sensor: null, cat: 'change', advice: '차로를 바꾸기 전 방향지시등 → 미러 → 어깨 너머 확인 순서로.' },
  UNSAFE_CUT: { title: '무리한 끼어들기', pts: 8, sensor: 'rear', cat: 'change', advice: '옆 차로 앞뒤 간격이 좁았어요. 뒤차가 미러에 차 전체가 보일 만큼 멀어졌을 때 옮겨요.' },
  GOOD_CHANGE: { title: '차로 변경 잘함', pts: 0, sensor: 'side', cat: 'change', good: true, advice: '신호를 켜고 충분한 간격에서 옮겼어요.' },
  TAILGATE: { title: '안전거리 부족', pts: 3, sensor: 'front', cat: 'gap', advice: '앞차가 지나간 표지를 보고 “하나, 둘” 센 뒤 내가 지나가면 2초 거리예요.' },
  NPC_CUTIN: { title: '옆 차가 앞으로 끼어듦', pts: 0, sensor: 'front', cat: 'gap', info: true, advice: '끼어든 차가 있으면 가속 페달에서 발을 떼고 거리를 다시 벌려요.' },
  CUTIN_HANDLED: { title: '끼어든 차에 잘 대응', pts: 0, sensor: 'front', cat: 'gap', good: true, advice: '당황하지 않고 거리를 다시 벌렸어요.' },
  CRASH: { title: '충돌(가상)', pts: 30, sensor: 'front', cat: 'gap', advice: '실제였다면 사고예요. 속도를 줄이고 거리를 넉넉히 두는 연습부터 다시 해요.' },
  SPEEDING: { title: '제한 속도 초과', pts: 3, sensor: null, cat: 'speed', advice: `제한 ${ROAD_LIMIT_KMH}km/h. 계기판 숫자를 3~5초마다 한 번씩 봐요.` },
  COACH_STOP: { title: '강습자 정지 요청', pts: 0, sensor: null, cat: 'coach', info: true, advice: '조수석 강습자가 정지를 요청해 차가 천천히 멈췄어요(제조사 협업 가정).' },
  ACC_CANCEL: { title: '브레이크로 크루즈 해제', pts: 0, sensor: null, cat: 'coach', info: true, advice: '브레이크를 밟으면 크루즈가 꺼져요. 다시 켜려면 강습자 패널에서.' },
};

export const CATEGORIES = [
  { key: 'smooth', name: '출발·정지 부드러움', icon: 'pedal' },
  { key: 'lane', name: '차선 유지', icon: 'road' },
  { key: 'change', name: '차로 변경 매너', icon: 'turn' },
  { key: 'gap', name: '안전거리·끼어들기 대응', icon: 'mentor' },
  { key: 'speed', name: '속도 준수', icon: 'gear' },
];

/** 강습 모드 주행보조 프리셋(상황별) */
export const PRESETS = {
  first: { label: '처음 도로', desc: '차선 보조 강하게 · 크루즈 40 · 최고 50km/h', lka: 'strong', acc: true, accSet: 40, cap: 50 },
  growing: { label: '익숙해지는 중', desc: '차선 보조 보통 · 크루즈 끔 · 최고 60km/h', lka: 'normal', acc: false, accSet: 50, cap: 60 },
  solo: { label: '실전 연습', desc: '보조 모두 끄기 — 스스로 해 보기', lka: 'off', acc: false, accSet: 50, cap: null },
};

function rng(seed) { let s = seed >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }

export class DriveSim {
  constructor(settings = {}, seed = 7) {
    this.rand = rng(seed);
    this.settings = { lka: 'off', acc: false, accSet: 50, cap: null, ...settings };
    this.input = { accel: 0, brake: 0, steer: 0, signal: 'NONE', gear: 'D' };
    this.t = 0; this.ego = { s: 0, x: 0, v: 0, a: 0 };
    this.stopReq = false;
    this.events = []; this.penalty = 0; this.cool = {};
    this.stats = { moving: 0, inLane: 0, maxV: 0 };
    this.prevLane = 0; this.headLow = 0; this.overT = 0;
    this.cutIn = { done: false, watchUntil: 0, bad: false, at: 0 };
    this.leadBrakeDone = false;
    this.flash = { sensor: null, until: 0 };
    this.npcs = [
      { id: 1, s: 55, x: 0, v: 13.9, vd: 13.9 },
      { id: 2, s: -22, x: -LANE_W, v: 16.5, vd: 16.5 },
      { id: 3, s: 35, x: -LANE_W, v: 15, vd: 15 },
      { id: 4, s: 28, x: LANE_W, v: 12, vd: 12 },
      { id: 5, s: 110, x: LANE_W, v: 13, vd: 13 },
      { id: 6, s: -35, x: LANE_W, v: 14, vd: 14 },
    ];
    this.nextId = 7;
  }

  laneOf(x) { return clamp(Math.round(x / LANE_W), -1, 1); }

  /** 같은 차로(가로로 겹치는) 앞차 */
  leadOf(x, s, exclude = null) {
    let best = null;
    for (const n of this.npcs) {
      if (n === exclude || n.s <= s || Math.abs(n.x - x) > CAR_W + 0.3) continue;
      if (!best || n.s < best.s) best = n;
    }
    return best;
  }

  log(code, extra = {}) {
    const ev = EVENTS[code];
    const cd = extra.cooldown ?? 4;
    if (this.cool[code] && this.t < this.cool[code]) return null;
    this.cool[code] = this.t + cd;
    const rec = { t: this.t, code, title: ev.title, pts: ev.pts, cat: ev.cat, advice: ev.advice, good: !!ev.good, info: !!ev.info, sensor: ev.sensor, kmh: Math.round(this.ego.v * 3.6) };
    this.events.push(rec); this.penalty += ev.pts;
    if (ev.sensor) this.flash = { sensor: ev.sensor, until: this.t + 1.6 };
    return rec;
  }

  requestStop(on) {
    if (on && !this.stopReq) this.log('COACH_STOP', { cooldown: 0 });
    this.stopReq = on;
  }

  step(dt) {
    const e = this.ego, I = this.input, S = this.settings;
    this.t += dt;
    const drive = I.gear === 'D';
    const lead = this.leadOf(e.x, e.s);
    const gap = lead ? lead.s - e.s - CAR_L : Infinity;

    // ── 세로(가속·제동) ──
    const aDriver = (drive ? I.accel * 3.2 : 0) - I.brake * 8;
    let a = aDriver;
    if (S.acc && I.brake > 0.05) { S.acc = false; this.log('ACC_CANCEL', { cooldown: 0 }); }
    if (S.acc && drive) {
      let aAcc = (S.accSet * KMH - e.v) * 0.6;
      if (lead) aAcc = Math.min(aAcc, (gap - (1.8 * e.v + 5)) * 0.25 + (lead.v - e.v) * 0.8);
      aAcc = clamp(aAcc, -3.5, 1.5);
      a = I.accel > 0.05 ? Math.max(aAcc, aDriver) : aAcc;
    }
    if (S.cap) {
      const cap = S.cap * KMH;
      a = e.v > cap ? Math.min(a, -1.2) : Math.min(a, (cap - e.v) * 1.5);
    }
    if (this.stopReq) a = Math.min(a, e.v > 0.05 ? -3.5 : 0);
    a -= 0.015 * e.v + (e.v > 0 ? 0.05 : 0);
    const v0 = e.v;
    e.v = Math.max(0, e.v + a * dt);
    e.a = (e.v - v0) / dt;
    e.s += e.v * dt;
    this.stats.maxV = Math.max(this.stats.maxV, e.v);

    // ── 가로(조향·차선 보조) ──
    let vy = e.v * (I.steer / 540) * 0.14;
    if (S.lka !== 'off' && I.signal === 'NONE' && e.v > 8) {
      const c = this.laneOf(e.x) * LANE_W;
      vy += (c - e.x) * (S.lka === 'strong' ? 1.4 : 0.5);
      if (S.lka === 'strong') vy = clamp(vy, -0.6, 0.6);
    }
    e.x += vy * dt;
    const edge = 1.5 * LANE_W - CAR_W / 2;
    if (Math.abs(e.x) > edge - 0.15) { this.log('ROAD_EDGE', { cooldown: 5 }); e.x = clamp(e.x, -edge, edge); }

    // ── 다른 차 ──
    this.updateNpcs(dt);

    // ── 충돌 ──
    for (const n of this.npcs) {
      if (Math.abs(n.x - e.x) < CAR_W && Math.abs(n.s - e.s) < CAR_L) {
        if (n.s > e.s) { // 내 차가 앞차에 닿음 → 운전자 충돌
          if (this.log('CRASH', { cooldown: 5 }) && this.cutIn.watchUntil > this.t) this.cutIn.bad = true;
          e.s = n.s - CAR_L - 0.3; e.v = Math.min(e.v, n.v * 0.8);
        } else { n.s = e.s - CAR_L - 0.3; n.v = Math.min(n.v, e.v); } // 뒤차가 따라붙은 경우는 운전자 감점 아님
      }
    }

    // ── 상황 감지 ──
    if (e.v < 8 && e.a > 2.6) this.log('HARSH_START');
    if (e.a < -5.2 && I.brake > 0.4 && !this.stopReq) {
      const ttc = lead && e.v > lead.v ? gap / (e.v - lead.v) : Infinity;
      this.log(gap < 1.2 * v0 + 4 || ttc < 2.5 ? 'HARSH_BRAKE_NEEDED' : 'HARSH_BRAKE');
    }
    if (lead && e.v > 5 && gap / e.v < 1.0) {
      this.headLow += dt;
      if (this.headLow > 2) { if (this.log('TAILGATE', { cooldown: 8 }) && this.cutIn.watchUntil > this.t) this.cutIn.bad = true; this.headLow = 0; }
    } else this.headLow = 0;
    if (e.v * 3.6 > ROAD_LIMIT_KMH + 5) { this.overT += dt; if (this.overT > 3) { this.log('SPEEDING', { cooldown: 10 }); this.overT = 0; } } else this.overT = 0;

    const lane = this.laneOf(e.x);
    const dev = e.x - lane * LANE_W;
    if (e.v > 3) {
      this.stats.moving += dt;
      if (Math.abs(dev) < 0.55) this.stats.inLane += dt;
      if (Math.abs(dev) > 0.95 && I.signal === 'NONE') this.log('LANE_DRIFT', { cooldown: 5 });
    }
    if (lane !== this.prevLane) { this.onLaneChange(lane); this.prevLane = lane; }

    // 끼어들기 대응 관찰이 끝나면
    if (this.cutIn.watchUntil && this.t > this.cutIn.watchUntil) {
      if (!this.cutIn.bad) this.log('CUTIN_HANDLED', { cooldown: 0 });
      this.cutIn.watchUntil = 0;
    }
  }

  /** 차로가 바뀐 순간: 가상 전후방 센서로 새 차로의 앞뒤 간격을 본다 */
  onLaneChange(lane) {
    const e = this.ego;
    let gapF = Infinity, gapR = Infinity, closing = 0;
    for (const n of this.npcs) {
      if (this.laneOf(n.x) !== lane || n.lc) continue;
      if (n.s >= e.s) gapF = Math.min(gapF, n.s - e.s - CAR_L);
      else { const g = e.s - n.s - CAR_L; if (g < gapR) { gapR = g; closing = n.v - e.v; } }
    }
    const unsafe = gapF < Math.max(6, e.v * 0.6) || gapR < Math.max(8, closing * 3 + e.v * 0.3);
    this.lastGap = { gapF, gapR };
    if (this.input.signal === 'NONE') this.log('NO_SIGNAL', { cooldown: 0 });
    if (unsafe) this.log('UNSAFE_CUT', { cooldown: 0 });
    else if (this.input.signal !== 'NONE') this.log('GOOD_CHANGE', { cooldown: 0 });
  }

  updateNpcs(dt) {
    const e = this.ego;
    // 시나리오 1: 14초 이후, 달리고 있으면 옆 차로 차가 앞으로 끼어든다
    if (!this.cutIn.done && this.t > 14 && e.v > 6) {
      const lane = this.laneOf(e.x);
      const side = lane >= 1 ? -1 : lane <= -1 ? 1 : (this.rand() < 0.5 ? 1 : -1);
      const n = { id: this.nextId++, s: e.s + 16, x: (lane + side) * LANE_W, v: e.v * 0.95, vd: e.v * 0.9, cutter: true };
      n.lc = { from: n.x, to: lane * LANE_W, t0: this.t, dur: 2.2 };
      this.npcs.push(n);
      this.cutIn = { done: true, watchUntil: this.t + 6, bad: false, at: this.t };
      this.log('NPC_CUTIN', { cooldown: 0 });
    }
    // 시나리오 2: 45초쯤 앞차가 잠깐 속도를 줄인다(안전거리 확인)
    if (!this.leadBrakeDone && this.t > 45) {
      const lead = this.leadOf(e.x, e.s);
      if (lead && lead.s - e.s < 60) { lead.slowUntil = this.t + 4; this.leadBrakeDone = true; }
    }
    for (const n of this.npcs) {
      if (n.lc) {
        const k = clamp((this.t - n.lc.t0) / n.lc.dur, 0, 1);
        n.x = n.lc.from + (n.lc.to - n.lc.from) * (k * k * (3 - 2 * k));
        if (k >= 1) n.lc = null;
      }
      let target = n.slowUntil && this.t < n.slowUntil ? 5.5 : n.vd;
      const l = this.leadOf(n.x, n.s, n);
      const ahead = [l, e.s > n.s && Math.abs(e.x - n.x) < CAR_W + 0.3 ? e : null].filter(Boolean).sort((p, q) => p.s - q.s)[0];
      if (ahead) { const g = ahead.s - n.s - CAR_L; if (g < 2 * n.v + 6) target = Math.min(target, ahead.v - 0.5 + (g - 8) * 0.15); }
      const close = ahead && ahead.s - n.s - CAR_L < 8;
      const acc = clamp((target - n.v) * (close ? 2 : 0.8), close ? -8 : -4, 1.2);
      n.v = Math.max(0, n.v + acc * dt);
      n.s += n.v * dt;
      if (n.s - e.s < -70 || n.s - e.s > 320) {
        n.s = e.s + 170 + this.rand() * 80; n.x = (Math.floor(this.rand() * 3) - 1) * LANE_W;
        n.vd = 11 + this.rand() * 5; n.v = n.vd; n.lc = null; n.cutter = false; n.slowUntil = 0;
        if (this.npcs.some((m) => m !== n && Math.abs(m.x - n.x) < 1 && Math.abs(m.s - n.s) < 20)) n.s += 30;
      }
    }
  }

  /** 센서 표시용: 앞·뒤·옆 가장 가까운 거리(m) */
  sensors() {
    const e = this.ego; const lane = this.laneOf(e.x);
    let front = Infinity, rear = Infinity, left = false, right = false;
    for (const n of this.npcs) {
      const sameLane = Math.abs(n.x - e.x) < CAR_W + 0.3;
      if (sameLane && n.s > e.s) front = Math.min(front, n.s - e.s - CAR_L);
      if (sameLane && n.s < e.s) rear = Math.min(rear, e.s - n.s - CAR_L);
      if (Math.abs(n.s - e.s) < CAR_L + 6) {
        const nl = this.laneOf(n.x);
        if (nl === lane - 1) left = true;
        if (nl === lane + 1) right = true;
      }
    }
    return { front, rear, left, right };
  }

  report() {
    const moving = this.stats.moving;
    const laneKeepPct = moving > 0 ? Math.round((this.stats.inLane / moving) * 100) : null;
    const cats = CATEGORIES.map((c) => {
      const evs = this.events.filter((x) => x.cat === c.key);
      return { ...c, minus: evs.reduce((s, x) => s + x.pts, 0), bad: evs.filter((x) => x.pts > 0).length, good: evs.filter((x) => x.good).length };
    });
    const advice = [...new Map(this.events.filter((x) => x.pts > 0).sort((a, b) => b.pts - a.pts).map((x) => [x.code, x])).values()].slice(0, 3);
    return {
      score: Math.max(0, 100 - this.penalty), durationSec: Math.round(this.t), distanceKm: this.ego.s / 1000,
      laneKeepPct, maxKmh: Math.round(this.stats.maxV * 3.6), events: this.events.slice(), cats, advice,
    };
  }
}
