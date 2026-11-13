// 치일까봐 — F12 주행 중 음성 길잡이 "조용한 동승자"
//
// 왜 주행 중에 소리를 내도 되나:
//   Pleos 공식 가이드 「주행 안전을 고려한 설계」 원문 —
//   "주행 규제 상태에서 **영상 재생**을 시작하거나 재개하지 않도록 처리하고,
//    **오디오 재생은 오디오 포커스 개발 가이드에 정의된 정책을 준수**합니다."
//   → 금지는 '영상'이고 오디오는 정책 준수 시 허용. 음성 연계 인터랙션은 권장 사항이다.
//   근거 원문·출처: docs/13_MEETING_2026-11-08.md §1, docs/03_PLEOS_SDK_NOTES.md §5-0
//
// 이 파일은 **규칙 엔진**(순수 함수·상태만)과 **브라우저 스피커**(Web Speech API)를 나눠 담는다.
// 규칙 엔진은 Android 구현(B48)과 같은 규칙을 쓰고, 단위 테스트(`voice_*`)로 고정한다.
// 화면은 절대 건드리지 않는다 — 음성이 나와도 잠금 화면은 그대로다(CLAUDE.md §5-4).

/** 음성 규칙 상수 — 바꿀 땐 docs/01_PRD.md F12 · docs/06 T13도 같이 고친다 */
export const RULES = {
  minIntervalSec: 45,      // 같은 주행에서 두 발화 사이 최소 간격
  maxPerDrive: 5,          // 1회 주행당 최대 발화 수
  maxSecPerLine: 2.5,      // 한 문장 길이 상한
  maxSameCode: 2,          // 같은 종류 지적은 주행당 2회까지
  steadyAfterSec: 90,      // 달리는 동안 90초간 지적이 없으면 칭찬 1회(11/12 회의: 잘할 때도 한마디. 10분은 너무 길어 들을 일이 없었다)
  steadyMinKmh: 10,        // 칭찬은 실제로 달리고 있을 때만(신호 대기 중 칭찬은 어색하다)
  windowSec: 0.8,          // 이 시간 안에 겹친 상황은 모아서 가장 급한 하나만 말한다
  syllablesPerSec: 6.5,    // 한국어 TTS rate 1.0 기준 추정치(T13에서 실측으로 교체)
};

/**
 * 음성 대사 사전 — docs/05_MISSIONS_CONTENT.md §4-1과 1:1.
 *  line   : 실제로 말하는 문장(한 문장, 2.5초 이내, 사실 + 짧은 제안. 지시·점수·경고 금지)
 *  goals  : 이 문장이 열리는 목표. null = 모든 목표
 *  advice : 리플레이 타임라인에 쓰는 한 줄 조언(화면용이라 조금 길어도 된다)
 *  off    : true면 기본 꺼짐(설정으로만 켤 수 있다)
 */
export const LINES = {
  HARSH_START: { line: '방금 출발이 조금 급했어요.', goals: ['출발·정지', '그냥 몸풀기'], advice: '가속 페달을 2초에 걸쳐 밟아 보세요.' },
  HARSH_BRAKE: { line: '조금 더 일찍 브레이크를요.', goals: ['출발·정지', '그냥 몸풀기'], advice: '앞차 브레이크등이 보이면 바로 발을 옮겨요.' },
  HARSH_BRAKE_NEEDED: { line: '앞차와 거리가 좁았어요.', goals: ['출발·정지', '그냥 몸풀기'], advice: '거리를 2초 이상 두면 급제동이 줄어요.' },
  NO_SIGNAL: { line: '차로를 바꿀 땐 깜빡이 먼저요.', goals: ['차선 바꾸기'], advice: '거울 → 깜빡이 → 어깨 확인 → 이동 순서로.' },
  LANE_DRIFT: { line: '차로 가운데가 흔들렸어요.', goals: ['차선 바꾸기'], advice: '멀리 보면 손이 덜 움직여요.' },
  ROAD_EDGE: { line: '가장자리에 조금 붙었어요.', goals: ['차선 바꾸기'], advice: '가장자리 선이 보이면 핸들을 반대로 조금만.' },
  TAILGATE: { line: '앞차와 조금 가까워요.', goals: ['출발·정지', '차선 바꾸기'], advice: '앞차가 지난 지점을 둘 셀 때 통과하면 2초 거리예요.' },
  UNSAFE_CUT: { line: '옆 차로 간격이 좁았어요.', goals: ['차선 바꾸기'], advice: '뒤차 전체가 미러에 보일 만큼 멀어졌을 때 옮겨요.' },
  NPC_CUTIN: { line: '끼어든 차가 있어요.', goals: ['출발·정지', '차선 바꾸기'], advice: '발을 떼고 거리를 다시 벌려요.' },
  CRASH: { line: '거리를 더 두고 가 볼까요.', goals: null, advice: '속도를 줄이고 거리를 넉넉히 두는 연습부터 다시.' },
  LIGHTS_RAIN: { line: '비가 와요. 전조등을 켤까요.', goals: null, advice: '와이퍼를 켤 때 전조등도 같이.' },
  TURN_WIDE: { line: '좌회전은 더 안쪽으로도 돼요.', goals: ['좌회전'], advice: '중앙선 끝을 보고 돌아요.' },
  PARK_SLOW: { line: '후진 속도가 지금 좋아요.', goals: ['주차'], advice: '후진은 걷는 속도보다 느리게.' },
  // 11/12 회의: 교차로 회전은 '길 안내'가 아니라 '주의'만. 내비가 이미 방향을 말하므로 방향 안내는 하지 않는다.
  TURN_LEFT_CAUTION: { line: '좌회전 전 맞은편을 봐요.', goals: ['좌회전', '그냥 몸풀기'], advice: '깜빡이를 켜고, 마주 오는 차와 횡단보도를 확인한 뒤 돌아요.' },
  TURN_RIGHT_CAUTION: { line: '우회전 전 횡단보도를 봐요.', goals: ['좌회전', '그냥 몸풀기'], advice: '우회전은 횡단보도 앞에서 한 번 멈추고, 보행자를 확인한 뒤 천천히 돌아요.' },
  STEADY: { line: '지금 흐름이 부드러워요.', goals: null, advice: '', once: true },
  // 기본 꺼짐: 내비가 이미 안내하므로 중복이고 잔소리로 느껴진다(docs/13 §3)
  SPEEDING: { line: '제한 속도를 조금 넘었어요.', goals: null, advice: '계기판 숫자를 3~5초마다 한 번씩.', off: true },
};

/**
 * 여러 상황이 한꺼번에 겹치면 **가장 급한 하나만** 말한다(11/12 회의).
 * 예: 끼어든 차 뒤에 급제동 → '급제동'이 더 위험하므로 그것만. 숫자가 클수록 먼저.
 * 같은 순간에 둘을 연달아 말하면 운전자는 둘 다 못 듣는다.
 */
export const PRIORITY = {
  CRASH: 100, HARSH_BRAKE_NEEDED: 90, HARSH_BRAKE: 85, UNSAFE_CUT: 80, TAILGATE: 70,
  NPC_CUTIN: 65, TURN_RIGHT_CAUTION: 60, TURN_LEFT_CAUTION: 60, NO_SIGNAL: 50, ROAD_EDGE: 45,
  LANE_DRIFT: 40, HARSH_START: 35, LIGHTS_RAIN: 30, SPEEDING: 25, TURN_WIDE: 20, PARK_SLOW: 15, STEADY: 5,
};
export const priorityOf = (code) => PRIORITY[code] ?? 0;

/** 한국어 발화 길이 추정(초). 한글 음절은 1, 그 밖의 글자는 0.4로 센다. */
export function estimateSec(text) {
  let n = 0;
  for (const ch of String(text)) {
    if (/\s/.test(ch)) continue;
    n += /[가-힣]/.test(ch) ? 1 : 0.4;
  }
  return n / RULES.syllablesPerSec;
}

/** 모든 대사가 길이 상한을 지키는지 — 테스트와 개발 중 자동 점검에서 쓴다 */
export function tooLongLines() {
  return Object.entries(LINES)
    .filter(([, v]) => estimateSec(v.line) > RULES.maxSecPerLine)
    .map(([k, v]) => ({ code: k, sec: +estimateSec(v.line).toFixed(2) }));
}

/**
 * 주행 1회당 하나 만든다. 입력은 `(코드, 지금 시각, 속도)`뿐이고
 * 출력은 "말할 문장" 또는 null이다. 화면·오디오는 건드리지 않는다.
 */
export class VoiceGuide {
  /**
   * @param {object} o
   * @param {string|null} o.goal 출발 전에 고른 목표 1개
   * @param {'solo'|'mentor'} o.mode 멘토 동승이면 기본 꺼짐
   * @param {boolean} [o.enabled] 설정에서 켜 둔 값(없으면 모드에 따라 결정)
   * @param {boolean} [o.speedLimitOn] 제한 속도 음성(기본 꺼짐)
   */
  constructor({ goal = null, mode = 'solo', enabled = null, speedLimitOn = false, maxPerDrive = RULES.maxPerDrive } = {}) {
    this.cap = Math.min(maxPerDrive, RULES.maxPerDrive);
    this.goal = goal;
    this.mode = mode;
    // 멘토가 타고 있으면 기본 꺼짐. 사람이 말하는데 기계가 끼어들면 안 된다.
    this.enabled = enabled == null ? mode !== 'mentor' : !!enabled;
    this.speedLimitOn = !!speedLimitOn;
    this.spokenCount = 0;
    this.lastAtSec = -Infinity;
    this.perCode = {};
    this.log = [];        // 실제로 말한 것(리플레이의 "말했어요" 표시에 쓴다)
    this.skipped = [];    // 왜 말하지 않았는지(T13 점검·디버그 패널용)
    this.pending = null;  // 포커스 대기 중인 발화
    this.lastQuietAt = 0; // 마지막 지적 시각(STEADY 판정)
    this.window = null;   // 모으는 중인 상황들 {t0, items:[{code,tSec,kmh,eventId}]}
  }

  /**
   * 상황을 바로 말하지 않고 짧게(windowSec) 모은다. flush()가 가장 급한 하나를 고른다.
   * 끼어들기 직후 급제동처럼 1초 안에 이어지는 상황을 한 번에 비교하기 위해서다.
   */
  queue(code, tSec, kmh = 0, eventId = null) {
    this.noteEvent(code, tSec);
    if (!this.window) this.window = { t0: tSec, items: [] };
    this.window.items.push({ code, tSec, kmh, eventId });
  }

  /** 발화 가능한 상황만 비교하고 각 미발화 사유를 남긴다. */
  flush(tSec, { force = false } = {}) {
    const w = this.window;
    if (!w || (!force && tSec - w.t0 < RULES.windowSec)) return null;
    this.window = null;
    const eligible = [];
    for (const item of w.items) {
      const why = this.blockedReason(item.code, tSec);
      if (why) this.skipped.push({ code: item.code, atSec: item.tSec, eventId: item.eventId, why });
      else eligible.push(item);
    }
    eligible.sort((a, b) => priorityOf(b.code) - priorityOf(a.code) || a.tSec - b.tSec);
    const [top, ...rest] = eligible;
    for (const item of rest) {
      this.skipped.push({ code: item.code, atSec: item.tSec, eventId: item.eventId, why: 'LOWER_PRIORITY' });
    }
    return top ? this.prepare(top.code, top.tSec, top.kmh, top.eventId, tSec) : null;
  }

  noteEvent(code, tSec) {
    if (LINES[code] && !LINES[code].once) this.lastQuietAt = Math.max(this.lastQuietAt, tSec);
  }

  /** 이 코드가 지금 열려 있는가(목표 한정 + 기본 꺼짐 항목) */
  opensFor(code) {
    const L = LINES[code];
    if (!L) return false;
    if (L.off && !(code === 'SPEEDING' && this.speedLimitOn)) return false;
    if (L.goals == null) return true;
    return this.goal != null && L.goals.includes(this.goal);
  }

  /** 지금 말할 수 있는가 — 말할 수 없으면 이유 문자열을 돌려준다 */
  blockedReason(code, tSec) {
    if (!this.enabled) return 'OFF';
    if (!LINES[code]) return 'NO_LINE';
    if (this.pending) return 'BUSY';
    if (!this.opensFor(code)) return 'NOT_THIS_GOAL';
    if (this.spokenCount >= this.cap) return 'DRIVE_CAP';
    if (tSec - this.lastAtSec < RULES.minIntervalSec) return 'TOO_SOON';
    const used = this.perCode[code] || 0;
    if (LINES[code].once && used >= 1) return 'ONCE_ONLY';
    if (used >= RULES.maxSameCode) return 'SAME_CAP';
    if (estimateSec(LINES[code].line) > RULES.maxSecPerLine) return 'TOO_LONG';
    return null;
  }

  /** 상황 하나가 발화 조건을 충족하면 재생할 문장을 반환한다. */
  consider(code, tSec, kmh = 0, eventId = null) {
    this.noteEvent(code, tSec);
    const why = this.blockedReason(code, tSec);
    if (why) { this.skipped.push({ code, atSec: tSec, eventId, why }); return null; }
    return this.prepare(code, tSec, kmh, eventId, tSec);
  }

  prepare(code, atSec, kmh, eventId, offeredAtSec) {
    const line = LINES[code].line;
    const utterance = { code, eventId, line, sec: +estimateSec(line).toFixed(2), atSec, offeredAtSec, kmh: Math.round(kmh) };
    this.pending = utterance;
    return utterance;
  }

  /** 달리는 동안 오래(steadyAfterSec) 지적이 없었으면 칭찬 한 번 */
  considerSteady(tSec, kmh = RULES.steadyMinKmh, eventId = null) {
    if (tSec - this.lastQuietAt < RULES.steadyAfterSec) return null;
    if (kmh < RULES.steadyMinKmh) return null;
    if (this.window) return null; // 지적을 모으는 중이면 칭찬하지 않는다
    return this.consider('STEADY', tSec, kmh, eventId);
  }

  /** 현재 발화가 정상 종료됐을 때만 호출한다. */
  committed(u = this.pending) {
    if (!u || this.pending !== u) return null;
    this.spokenCount += 1;
    this.lastAtSec = u.offeredAtSec;
    this.perCode[u.code] = (this.perCode[u.code] || 0) + 1;
    this.log.push(u);
    this.pending = null;
    return u;
  }

  /**
   * 오디오 포커스를 못 얻었거나 잃었다 → **그 발화는 버린다.**
   * 나중에 다시 말하지 않는다(내비·통화 끝난 뒤 뒤늦게 튀어나오면 더 놀란다).
   * 간격·횟수 카운터는 올리지 않는다.
   */
  dropped(why = 'AUDIO_FOCUS') {
    if (!this.pending) return null;
    const u = this.pending; this.pending = null;
    this.skipped.push({ code: u.code, atSec: u.atSec, eventId: u.eventId, why });
    return u;
  }

  /** 남은 발화 횟수(잠금 화면에 'n/5'로 보여 준다) */
  remaining() { return Math.max(0, this.cap - this.spokenCount); }

  /** 이 코드를 말했는가(리플레이 "말했어요" 표시) */
  didSpeak(eventId) {
    return eventId != null && this.log.some((x) => x.eventId === eventId);
  }
}

/**
 * 차량 신호만 보고 상황을 알아내는 감시기.
 *
 * 평가·강습 모드는 가상 주행 시뮬레이션이 상황을 알려 주지만,
 * **연습 모드(= 실제 앱이 하는 일)는 차량 신호밖에 없다.** 실제 앱에서 쓸 판정이 바로 이것이다.
 * 입력은 공개 SDK로 읽을 수 있는 것만 쓴다(속도·브레이크 페달·방향지시등·핸들 각도·와이퍼·전조등).
 * ADAS 경고 상태(차선이탈·전방충돌)는 서드파티 앱이 읽을 수 없다(docs/03 §5-1) → 쓰지 않는다.
 */
export const THRESHOLDS = {
  harshStartKmhPerSec: 9,    // 가속 9km/h/s ≈ 2.5m/s²
  harshBrakeKmhPerSec: -12,  // 감속 -12km/h/s ≈ -3.3m/s²
  harshBrakeMinPedal: 35,    // 브레이크 페달을 실제로 밟았을 때만(미끄러짐·내리막 구분)
  laneChangeDeg: 60,         // 이 각도를 넘으면 차로 변경으로 본다
  minKmhForLane: 20,         // 주차장 조작을 차로 변경으로 오해하지 않게
  codeCooldownSec: 6,
  turnMinKmh: 3,             // 회전 주의: 이 속도 사이에서 깜빡이를 켜면 교차로 회전으로 본다
  turnMaxKmh: 35,        // 같은 코드를 매 프레임 밀어 넣지 않는다
};

export class SignalWatcher {
  constructor() {
    this.prev = null;
    this.cool = {};
  }

  #open(code, tSec) {
    if (this.cool[code] != null && tSec - this.cool[code] < THRESHOLDS.codeCooldownSec) return false;
    this.cool[code] = tSec;
    return true;
  }

  /**
   * 신호 한 묶음을 보고 감지된 코드 목록을 돌려준다.
   * @param {{tSec:number,kmh:number,brakePct?:number,turnSignal?:string,steeringDeg?:number,wiperOn?:boolean,lowBeamOn?:boolean,gear?:string}} s
   * @returns {string[]}
   */
  step(s) {
    const out = [];
    const p = this.prev;
    this.prev = { ...s };
    if (!p) return out;
    const dt = s.tSec - p.tSec;
    if (dt <= 0 || dt > 2) return out; // 프레임이 튀면 변화율을 믿지 않는다

    const rate = (s.kmh - p.kmh) / dt;
    const driving = (s.gear || 'D') !== 'P';

    if (driving && rate >= THRESHOLDS.harshStartKmhPerSec && this.#open('HARSH_START', s.tSec)) out.push('HARSH_START');
    if (driving && rate <= THRESHOLDS.harshBrakeKmhPerSec && (s.brakePct || 0) >= THRESHOLDS.harshBrakeMinPedal
        && this.#open('HARSH_BRAKE', s.tSec)) out.push('HARSH_BRAKE');

    // 깜빡이 없이 차로 변경: 핸들이 크게 꺾였는데 방향지시등이 꺼져 있다
    const crossed = Math.abs(s.steeringDeg || 0) >= THRESHOLDS.laneChangeDeg && Math.abs(p.steeringDeg || 0) < THRESHOLDS.laneChangeDeg;
    if (driving && crossed && s.kmh >= THRESHOLDS.minKmhForLane) {
      const code = (s.turnSignal || 'NONE') === 'NONE' ? 'NO_SIGNAL' : null;
      if (code && this.#open(code, s.tSec)) out.push(code);
    }

    // 교차로 회전 주의: 천천히 가다가(3~35km/h) 방향지시등을 새로 켰다 = 곧 돈다.
    // 차로 변경은 보통 더 빠를 때라 구분한다. 길 안내가 아니라 주의만 말한다(11/12 회의).
    const sig = s.turnSignal || 'NONE';
    if (driving && (sig === 'LEFT' || sig === 'RIGHT') && p.turnSignal === 'NONE'
        && s.kmh >= THRESHOLDS.turnMinKmh && s.kmh <= THRESHOLDS.turnMaxKmh) {
      const code = sig === 'LEFT' ? 'TURN_LEFT_CAUTION' : 'TURN_RIGHT_CAUTION';
      if (this.#open(code, s.tSec)) out.push(code);
    }

    // 비 오는데 전조등이 꺼져 있다 — 와이퍼를 켠 순간에만 본다(계속 잔소리하지 않게)
    if (s.wiperOn && !s.lowBeamOn && !(p.wiperOn && !p.lowBeamOn) && this.#open('LIGHTS_RAIN', s.tSec)) out.push('LIGHTS_RAIN');

    return out;
  }
}

/** 감지 코드 → 리플레이에 쓸 제목(sim 없이 연습 모드에서 쓴다) */
export const TITLES = {
  HARSH_START: '급출발', HARSH_BRAKE: '급제동', HARSH_BRAKE_NEEDED: '앞차 가까움에 급제동',
  NO_SIGNAL: '깜빡이 없이 차로 변경', LANE_DRIFT: '차로 가운데 흔들림', ROAD_EDGE: '가장자리 접근',
  TAILGATE: '안전거리 부족', UNSAFE_CUT: '무리한 끼어들기', NPC_CUTIN: '옆 차가 끼어듦',
  CRASH: '충돌(가상)', LIGHTS_RAIN: '비 오는데 전조등 꺼짐', TURN_WIDE: '좌회전 반경',
  PARK_SLOW: '후진 속도', STEADY: '안정적으로 주행', SPEEDING: '제한 속도 초과',
  TURN_LEFT_CAUTION: '교차로 좌회전', TURN_RIGHT_CAUTION: '교차로 우회전',
};

/**
 * 주행 중 기록을 리플레이 타임라인으로 바꾼다(F13).
 * 점수·등급은 넣지 않는다 — 사실과 조언만.
 * @param {Array} events sim의 기록 배열 ({t, code, title, kmh, sensor, good, info})
 * @param {VoiceGuide|null} voice 말한 것 표시에 쓴다
 * @param {string} source 출처 라벨 키(MOCK / EMULATOR / VEHICLE)
 */
export function buildReplay(events = [], voice = null, source = 'MOCK') {
  return events
    .slice()
    .sort((a, b) => a.t - b.t)
    .map((e) => ({
      eventId: e.eventId ?? null,
      atSec: e.t,
      title: e.title,
      kmh: e.kmh,
      // 리플레이 조언은 음성 사전 쪽을 먼저 쓴다(말한 것과 글이 어긋나지 않게)
      advice: (LINES[e.code] && LINES[e.code].advice) || e.advice || '',
      sensor: e.sensor || null,
      kind: e.good ? 'good' : e.info ? 'info' : 'note',
      spoken: voice ? voice.didSpeak(e.eventId) : false,
      source,
    }));
}

export const REPLAY_EMPTY = '오늘은 특별히 기록된 순간이 없어요. 안정적으로 다녀왔네요.';

// ───────────────────────── 브라우저 스피커 ─────────────────────────
// Web Speech API(`speechSynthesis`)로 **실제 소리를 낸다.**
// Android 쪽(B48)은 Pleos TextToSpeech ON_DEVICE → 실패 시 Android TextToSpeech를 쓰고,
// 오디오 포커스는 AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK으로 요청한다(docs/03 §5-0).
// 브라우저에는 오디오 포커스 API가 없으므로, 여기서는
//   ① 다른 소리가 재생 중이면(= duck 상황 모의) 발화를 버리고
//   ② 사용자가 화면을 한 번이라도 눌렀는지(자동 재생 정책)를 확인한다.

export class Speaker {
  constructor() {
    this.api = typeof speechSynthesis !== 'undefined' ? speechSynthesis : null;
    this.voice = null;
    this.ready = false;
    this.unlocked = false;   // 사용자 조작이 한 번 있었는지(브라우저 자동 재생 정책)
    this.lastError = null;
    this.finishActive = null;
    this.onState = null;     // 화면에 상태를 알리는 콜백(잠금 화면 캡션)
    if (this.api) this.#pickVoice();
  }

  get supported() { return !!this.api; }

  #pickVoice() {
    const load = () => {
      const all = this.api.getVoices();
      if (!all.length) return false;
      // 한국어 음성을 먼저, 없으면 기본 음성
      const korean = all.filter((v) => /^ko(?:-|_|$)/i.test(v.lang));
      this.voice = korean.find((v) => v.localService && /^ko[-_]kr$/i.test(v.lang))
        ?? korean.find((v) => v.localService)
        ?? korean.find((v) => /^ko[-_]kr$/i.test(v.lang))
        ?? korean[0] ?? null;
      this.ready = true;
      this.onState?.(this.state());
      return true;
    };
    if (!load()) this.api.addEventListener?.('voiceschanged', load, { once: false });
  }

  state() {
    if (!this.supported) return { ok: false, why: '이 브라우저는 음성 합성을 지원하지 않아요' };
    if (!this.unlocked) return { ok: false, why: '화면을 한 번 누르면 음성이 나와요' };
    if (!this.voice) return { ok: false, why: this.ready ? '이 기기에서 쓸 수 있는 한국어 음성이 없어요' : '쓸 수 있는 음성을 찾는 중이에요' };
    return { ok: true, why: `${this.voice.name}` };
  }

  /** 사용자의 첫 조작 때 한 번 부른다(자동 재생 정책 해제) */
  unlock() {
    if (!this.supported || this.unlocked) return;
    this.unlocked = true;
    try { this.api.resume(); } catch { /* 무시 */ }
    this.onState?.(this.state());
  }

  /**
   * 한 문장 말한다.
   * @returns {Promise<'spoken'|'dropped'>} 'dropped'이면 호출자가 voice.dropped()를 부른다
   */
  speak(text, { rate = 1, pitch = 1, volume = 1 } = {}) {
    return new Promise((resolve) => {
      if (!this.supported || !this.unlocked || (this.ready && !this.voice)) { this.lastError = 'UNAVAILABLE'; return resolve('dropped'); }
      if (this.finishActive || this.api.speaking || this.api.pending) { this.lastError = 'BUSY'; return resolve('dropped'); }
      let timeout;
      const finish = (error = null) => {
        if (this.finishActive !== finish) return;
        clearTimeout(timeout);
        this.finishActive = null;
        this.lastError = error;
        resolve(error ? 'dropped' : 'spoken');
      };
      this.finishActive = finish;
      try {
        const u = new SpeechSynthesisUtterance(String(text));
        if (this.voice) { u.voice = this.voice; u.lang = this.voice.lang; } else u.lang = 'ko-KR';
        u.rate = rate; u.pitch = pitch; u.volume = volume;
        u.onend = () => finish();
        u.onerror = (e) => finish(e?.error || 'ERROR');
        timeout = setTimeout(() => this.stop('TIMEOUT'), (estimateSec(text) / rate + 1.5) * 1000);
        this.api.speak(u);
      } catch (e) {
        finish(String(e?.message || e) || 'ERROR');
      }
    });
  }

  /** 취소 처리 이후 도착하는 이전 콜백은 무시한다. */
  stop(reason = 'CANCELLED') {
    this.finishActive?.(reason);
    try { this.api?.cancel(); } catch { /* 무시 */ }
  }
}
