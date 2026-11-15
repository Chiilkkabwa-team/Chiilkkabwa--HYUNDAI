// node --test web/test — F12 주행 중 음성 길잡이 · F13 리플레이 규칙 고정(docs/06 T13·T14)
// 이 테스트가 깨지면 '주행 중에 너무 많이 말하는 앱'이 된다. 규칙을 바꾸려면 docs/01 F12도 같이 고친다.
import test from 'node:test';
import assert from 'node:assert/strict';
import { VoiceGuide, RULES, LINES, PRIORITY, priorityOf, estimateSec, tooLongLines, buildReplay, REPLAY_EMPTY, SignalWatcher, THRESHOLDS, TITLES, Speaker } from '../voice.js';

const g = (o = {}) => new VoiceGuide({ goal: '출발·정지', mode: 'solo', ...o });

test('voice_all_lines_fit_time_budget', () => {
  // 한 문장 2.5초 상한 — 긴 문장은 사전에 들어가면 안 된다
  assert.deepEqual(tooLongLines(), []);
  assert.ok(estimateSec('방금 출발이 조금 급했어요.') <= RULES.maxSecPerLine);
});

test('voice_respects_min_interval', () => {
  const v = g();
  assert.ok(v.consider('HARSH_START', 10)); v.committed();
  // 45초 안에는 다른 상황이라도 말하지 않는다
  assert.equal(v.consider('HARSH_BRAKE', 20), null);
  assert.equal(v.skipped.at(-1).why, 'TOO_SOON');
  assert.ok(v.consider('HARSH_BRAKE', 10 + RULES.minIntervalSec));
});

test('voice_caps_per_drive', () => {
  const v = g();
  let t = 0;
  // 같은 코드 상한(2회)에 먼저 걸리지 않게 코드를 돌려 쓴다
  const codes = ['HARSH_START', 'HARSH_BRAKE', 'TAILGATE', 'HARSH_START', 'HARSH_BRAKE'];
  assert.ok(codes.length >= RULES.maxPerDrive, '테스트 코드 목록이 상한만큼 있어야 한다');
  for (let i = 0; i < RULES.maxPerDrive; i++) {
    t += RULES.minIntervalSec;
    assert.ok(v.consider(codes[i], t), `발화 ${i + 1}`);
    v.committed();
  }
  assert.equal(v.spokenCount, RULES.maxPerDrive);
  assert.equal(v.remaining(), 0);
  assert.equal(v.consider('TAILGATE', t + 999), null);
  assert.equal(v.skipped.at(-1).why, 'DRIVE_CAP');
});

test('voice_only_for_selected_goal', () => {
  const v = g({ goal: '출발·정지' });
  // '차선 바꾸기' 전용 문구는 열리지 않는다
  assert.equal(v.consider('NO_SIGNAL', 100), null);
  assert.equal(v.skipped.at(-1).why, 'NOT_THIS_GOAL');
  // 목표가 없는(모든 목표 공통) 문구는 열린다
  assert.ok(v.consider('LIGHTS_RAIN', 100));

  const v2 = g({ goal: '차선 바꾸기' });
  assert.ok(v2.consider('NO_SIGNAL', 100));
});

test('voice_off_by_default_with_mentor', () => {
  const m = new VoiceGuide({ goal: '출발·정지', mode: 'mentor' });
  assert.equal(m.enabled, false);
  assert.equal(m.consider('HARSH_START', 10), null);
  assert.equal(m.skipped.at(-1).why, 'OFF');
  // 혼자 연습은 기본 켜짐
  assert.equal(new VoiceGuide({ mode: 'solo' }).enabled, true);
  // 설정이 있으면 설정이 이긴다
  assert.equal(new VoiceGuide({ mode: 'mentor', enabled: true }).enabled, true);
  assert.equal(new VoiceGuide({ mode: 'solo', enabled: false }).enabled, false);
});

test('voice_drops_utterance_on_focus_loss', () => {
  const v = g();
  assert.ok(v.consider('HARSH_START', 10));
  // 내비 안내가 끼어들었다 → 이 발화는 버린다
  const d = v.dropped('AUDIO_FOCUS');
  assert.equal(d.code, 'HARSH_START');
  assert.equal(v.spokenCount, 0, '버린 발화는 횟수에 세지 않는다');
  assert.equal(v.log.length, 0, '버린 발화는 기록에 남지 않는다');
  // 버렸으니 나중에 다시 말하지 않는다 — 큐가 비어 있어야 한다
  assert.equal(v.pending, null);
  assert.equal(v.dropped(), null);
  // 간격 카운터도 올라가지 않았으므로 바로 다음 상황은 말할 수 있다
  assert.ok(v.consider('HARSH_BRAKE', 12));
});

test('voice_same_code_capped', () => {
  const v = g();
  let t = 0;
  for (let i = 0; i < RULES.maxSameCode; i++) { t += RULES.minIntervalSec; assert.ok(v.consider('HARSH_START', t)); v.committed(); }
  t += RULES.minIntervalSec;
  assert.equal(v.consider('HARSH_START', t), null);
  assert.equal(v.skipped.at(-1).why, 'SAME_CAP');
});

test('voice_speed_limit_off_by_default', () => {
  const v = g();
  assert.equal(v.consider('SPEEDING', 100), null, '제한 속도는 내비와 중복이라 기본 꺼짐');
  const on = g({ speedLimitOn: true });
  assert.ok(on.consider('SPEEDING', 100));
});

test('voice_steady_praise_once_after_quiet', () => {
  const v = g();
  assert.equal(v.considerSteady(RULES.steadyAfterSec - 1), null, '정해진 시간이 지나지 않으면 칭찬하지 않는다');
  const t = RULES.steadyAfterSec + 1;
  assert.ok(v.considerSteady(t)); v.committed();
  assert.equal(v.considerSteady(t + RULES.steadyAfterSec * 2), null, '주행당 1회');
  assert.equal(v.skipped.at(-1).why, 'ONCE_ONLY');
});

test('voice_bad_event_resets_steady_timer', () => {
  const v = g();
  v.consider('HARSH_START', 500); v.committed();
  assert.equal(v.considerSteady(500 + RULES.steadyAfterSec - 1), null, '지적이 있었으면 처음부터 다시 센다');
  assert.ok(v.considerSteady(500 + RULES.steadyAfterSec + 1));
});

test('voice_lines_have_no_commands_or_scores', () => {
  const banned = ['꺾으세요', '밟으세요', '점', '감점', '위험', '틀렸', '실패', '등급'];
  for (const [code, L] of Object.entries(LINES)) {
    for (const w of banned) assert.ok(!L.line.includes(w), `${code}: "${L.line}"에 금지어 "${w}"`);
    assert.ok(!/[!]/.test(L.line), `${code}: 느낌표로 놀라게 하지 않는다`);
    assert.ok(L.line.split('.').filter((s) => s.trim()).length <= 1 || L.line.endsWith('.'), `${code}: 한 문장`);
  }
});

// ── F13 리플레이 ──
const evs = [
  { eventId: 1, t: 92, code: 'HARSH_BRAKE', title: '급제동', kmh: 42, sensor: null, advice: '옛 조언' },
  { t: 30, code: 'TAILGATE', title: '안전거리 부족', kmh: 55, sensor: 'front', advice: '옛 조언' },
  { t: 150, code: 'GOOD_CHANGE', title: '차로 변경 잘함', kmh: 50, sensor: 'side', good: true, advice: '잘했어요' },
];

test('replay_orders_by_time', () => {
  const r = buildReplay(evs);
  assert.deepEqual(r.map((x) => x.atSec), [30, 92, 150]);
  assert.deepEqual(r.map((x) => x.kind), ['note', 'note', 'good']);
});

test('replay_marks_spoken', () => {
  const v = g();
  v.consider('HARSH_BRAKE', 92, 42, 1); v.committed();
  const r = buildReplay(evs, v);
  assert.equal(r.find((x) => x.atSec === 92).spoken, true, '말한 항목에 🔊');
  assert.equal(r.find((x) => x.atSec === 30).spoken, false);
});

test('replay_uses_voice_dictionary_advice', () => {
  // 말한 문장과 글로 쓴 조언이 어긋나면 안 된다 → 사전 쪽을 먼저 쓴다
  const r = buildReplay(evs);
  assert.equal(r.find((x) => x.atSec === 92).advice, LINES.HARSH_BRAKE.advice);
  // 사전에 없는 코드는 sim의 조언을 그대로
  assert.equal(r.find((x) => x.atSec === 150).advice, '잘했어요');
});

test('replay_has_no_scores', () => {
  const r = buildReplay(evs);
  for (const row of r) {
    assert.equal('pts' in row, false, '리플레이에 점수를 넣지 않는다');
    assert.ok(row.source, '출처 라벨은 항상 있다');
  }
});

test('replay_empty_message', () => {
  assert.deepEqual(buildReplay([]), []);
  assert.ok(REPLAY_EMPTY.includes('안정적으로'));
});

// ── 차량 신호만으로 상황 알아내기(연습 모드 = 실제 앱이 하는 일) ──
const sig = (o) => ({ kmh: 0, brakePct: 0, turnSignal: 'NONE', steeringDeg: 0, wiperOn: false, lowBeamOn: false, gear: 'D', ...o });

test('watch_needs_two_samples', () => {
  const w = new SignalWatcher();
  assert.deepEqual(w.step(sig({ tSec: 0, kmh: 0 })), [], '첫 표본만으로는 변화율을 모른다');
});

test('watch_harsh_start_and_brake', () => {
  const w = new SignalWatcher();
  w.step(sig({ tSec: 0, kmh: 0 }));
  assert.deepEqual(w.step(sig({ tSec: 1, kmh: 15 })), ['HARSH_START']);
  // 쿨다운 안에는 다시 올리지 않는다
  w.step(sig({ tSec: 1.5, kmh: 30 }));
  assert.deepEqual(w.step(sig({ tSec: 2, kmh: 45 })), []);

  const b = new SignalWatcher();
  b.step(sig({ tSec: 0, kmh: 50, brakePct: 60 }));
  assert.deepEqual(b.step(sig({ tSec: 1, kmh: 30, brakePct: 60 })), ['HARSH_BRAKE']);
});

test('watch_brake_needs_actual_pedal', () => {
  // 브레이크를 밟지 않고 속도가 떨어진 것(내리막 끝·엔진 브레이크)은 급제동이 아니다
  const w = new SignalWatcher();
  w.step(sig({ tSec: 0, kmh: 50, brakePct: 0 }));
  assert.deepEqual(w.step(sig({ tSec: 1, kmh: 30, brakePct: 0 })), []);
});

test('watch_no_signal_lane_change', () => {
  const w = new SignalWatcher();
  w.step(sig({ tSec: 0, kmh: 50, steeringDeg: 0 }));
  assert.deepEqual(w.step(sig({ tSec: 0.5, kmh: 50, steeringDeg: 90 })), ['NO_SIGNAL']);

  // 깜빡이를 켰으면 지적하지 않는다
  const ok = new SignalWatcher();
  ok.step(sig({ tSec: 0, kmh: 50, steeringDeg: 0, turnSignal: 'LEFT' }));
  assert.deepEqual(ok.step(sig({ tSec: 0.5, kmh: 50, steeringDeg: 90, turnSignal: 'LEFT' })), []);

  // 주차장 저속 조작은 차로 변경이 아니다
  const slow = new SignalWatcher();
  slow.step(sig({ tSec: 0, kmh: 5, steeringDeg: 0 }));
  assert.deepEqual(slow.step(sig({ tSec: 0.5, kmh: 5, steeringDeg: 200 })), []);
});

test('watch_rain_lights_only_on_wiper_turn_on', () => {
  const w = new SignalWatcher();
  w.step(sig({ tSec: 0, kmh: 40, wiperOn: false, lowBeamOn: false }));
  assert.deepEqual(w.step(sig({ tSec: 1, kmh: 40, wiperOn: true, lowBeamOn: false })), ['LIGHTS_RAIN']);
  // 계속 와이퍼가 켜져 있어도 반복하지 않는다
  assert.deepEqual(w.step(sig({ tSec: 20, kmh: 40, wiperOn: true, lowBeamOn: false })), []);
  // 전조등을 켜면 더 말하지 않는다
  const ok = new SignalWatcher();
  ok.step(sig({ tSec: 0, kmh: 40 }));
  assert.deepEqual(ok.step(sig({ tSec: 1, kmh: 40, wiperOn: true, lowBeamOn: true })), []);
});

test('watch_ignores_park_gear_and_frame_jumps', () => {
  const p = new SignalWatcher();
  p.step(sig({ tSec: 0, kmh: 0, gear: 'P' }));
  assert.deepEqual(p.step(sig({ tSec: 1, kmh: 20, gear: 'P' })), [], '기어 P에서는 판정하지 않는다');

  const j = new SignalWatcher();
  j.step(sig({ tSec: 0, kmh: 0 }));
  assert.deepEqual(j.step(sig({ tSec: 9, kmh: 60 })), [], '프레임이 튀면 변화율을 믿지 않는다');
});

test('watch_codes_all_have_lines_and_titles', () => {
  // 감지는 되는데 말할 문장이 없으면 조용히 사라진다 → 사전·제목이 다 있어야 한다
  for (const code of ['HARSH_START', 'HARSH_BRAKE', 'NO_SIGNAL', 'LIGHTS_RAIN']) {
    assert.ok(LINES[code], `${code} 음성 문구 없음`);
    assert.ok(TITLES[code], `${code} 제목 없음`);
  }
  assert.ok(THRESHOLDS.harshBrakeKmhPerSec < 0);
});

test('watch_feeds_guide_end_to_end', () => {
  // 신호 → 감지 → 음성 규칙 → 리플레이까지 한 번에
  const w = new SignalWatcher();
  const v = new VoiceGuide({ goal: '출발·정지', mode: 'solo' });
  const rows = [];
  const frames = [
    sig({ tSec: 0, kmh: 0 }), sig({ tSec: 1, kmh: 15 }),            // 급출발
    sig({ tSec: 60, kmh: 50, brakePct: 60 }), sig({ tSec: 61, kmh: 28, brakePct: 60 }), // 45초 뒤 급제동
  ];
  for (const f of frames) {
    for (const code of w.step(f)) {
      const eventId = rows.length + 1;
      const u = v.consider(code, f.tSec, f.kmh, eventId);
      if (u) v.committed();
      rows.push({ eventId, t: f.tSec, code, title: TITLES[code], kmh: f.kmh });
    }
  }
  assert.equal(v.log.length, 2, '두 상황 모두 말했다');
  const r = buildReplay(rows, v);
  assert.deepEqual(r.map((x) => x.spoken), [true, true]);
  assert.deepEqual(r.map((x) => x.title), ['급출발', '급제동']);
});

// ── 11/12 회의: 겹친 상황 우선순위 · 칭찬 · 회전 주의(B01) ──
test('voice_overlap_speaks_most_urgent_only', () => {
  const v = g();
  // 끼어든 차 → 0.3초 뒤 급제동: 급제동이 더 위험하므로 그것만 말한다
  v.queue('NPC_CUTIN', 10.0, 40); v.queue('HARSH_BRAKE', 10.3, 38);
  assert.equal(v.flush(10.5), null, '모으는 시간(0.8초) 안에는 아직 말하지 않는다');
  const u = v.flush(10.9);
  assert.equal(u.code, 'HARSH_BRAKE');
  assert.ok(v.skipped.some((x) => x.code === 'NPC_CUTIN' && x.why === 'LOWER_PRIORITY'));
});

test('voice_priority_table_covers_every_line', () => {
  for (const code of Object.keys(LINES)) assert.ok(code in PRIORITY, `${code} 우선순위 없음`);
  assert.ok(priorityOf('CRASH') > priorityOf('HARSH_BRAKE'));
  assert.ok(priorityOf('HARSH_BRAKE') > priorityOf('NPC_CUTIN'));
  assert.ok(priorityOf('STEADY') < priorityOf('LANE_DRIFT'), '칭찬은 늘 맨 뒤');
});

test('voice_praise_only_while_moving', () => {
  const v = g();
  assert.equal(v.considerSteady(RULES.steadyAfterSec + 1, 0), null, '멈춰 있을 땐 칭찬하지 않는다');
  assert.ok(v.considerSteady(RULES.steadyAfterSec + 1, 40));
});

test('voice_turn_caution_is_not_navigation', () => {
  for (const code of ['TURN_LEFT_CAUTION', 'TURN_RIGHT_CAUTION']) {
    const L = LINES[code];
    assert.ok(L, code);
    // 방향 지시(‘~하세요’, 거리 안내)는 내비 몫이다. 주의만 말한다.
    assert.ok(!/하세요|미터|m 앞/.test(L.line), `${code}: "${L.line}"`);
    assert.ok(estimateSec(L.line) <= RULES.maxSecPerLine);
  }
  assert.ok(g({ goal: '좌회전' }).consider('TURN_RIGHT_CAUTION', 5));
  assert.equal(g({ goal: '주차' }).consider('TURN_RIGHT_CAUTION', 5), null, '다른 목표면 열리지 않는다');
});

test('watcher_turn_signal_at_low_speed_opens_turn_caution', () => {
  const w = new SignalWatcher();
  w.step({ tSec: 0, kmh: 18, turnSignal: 'NONE', gear: 'D' });
  assert.deepEqual(w.step({ tSec: 0.1, kmh: 18, turnSignal: 'RIGHT', gear: 'D' }), ['TURN_RIGHT_CAUTION']);
  const fast = new SignalWatcher();
  fast.step({ tSec: 0, kmh: 70, turnSignal: 'NONE', gear: 'D' });
  assert.deepEqual(fast.step({ tSec: 0.1, kmh: 70, turnSignal: 'LEFT', gear: 'D' }), [], '빠를 때 깜빡이는 차로 변경으로 본다');
});

test('voice_skips_ineligible_priority_winner_and_uses_next_candidate', () => {
  const v = g({ goal: '좌회전' });
  v.queue('HARSH_BRAKE', 10, 18, 1);
  v.queue('TURN_LEFT_CAUTION', 10.2, 18, 2);
  assert.equal(v.flush(11).eventId, 2);
  assert.deepEqual(v.skipped.map(({ eventId, why }) => [eventId, why]), [[1, 'NOT_THIS_GOAL']]);
});

test('voice_repetition_limit_does_not_hide_a_lower_priority_candidate', () => {
  const v = g();
  for (const t of [0, 45]) v.committed(v.consider('HARSH_BRAKE', t));
  v.queue('HARSH_BRAKE', 90, 18, 1);
  v.queue('HARSH_START', 90.2, 18, 2);
  assert.equal(v.flush(91).eventId, 2);
  assert.equal(v.skipped[0].why, 'SAME_CAP');
});

test('voice_interval_uses_offer_time_instead_of_event_time', () => {
  const v = g();
  v.queue('HARSH_BRAKE', 10, 18, 1);
  v.committed(v.flush(11));
  assert.equal(v.consider('HARSH_START', 55), null);
  assert.ok(v.consider('HARSH_START', 56));
});

test('voice_caps_legacy_high_settings_at_five', () => {
  assert.equal(g({ maxPerDrive: 20 }).cap, 5);
});

test('hazard_and_unknown_direction_are_not_turn_cautions', () => {
  for (const direction of ['HAZARD', 'UNKNOWN', undefined]) {
    const w = new SignalWatcher();
    w.step({ tSec: 0, kmh: 18, turnSignal: 'NONE', gear: 'D' });
    assert.deepEqual(w.step({ tSec: 0.1, kmh: 18, turnSignal: direction, gear: 'D' }), []);
  }
  const w = new SignalWatcher();
  w.step({ tSec: 0, kmh: 18, gear: 'D' });
  assert.deepEqual(w.step({ tSec: 0.1, kmh: 18, turnSignal: 'LEFT', gear: 'D' }), []);
});

test('replay_joins_completed_speech_to_its_event_only', () => {
  const v = g({ goal: '좌회전' });
  const playing = v.consider('TURN_RIGHT_CAUTION', 10, 18, 1);
  const rows = [
    { eventId: 1, t: 10, code: 'JUNCTION_AHEAD' },
    { eventId: 2, t: 10, code: 'JUNCTION_AHEAD' },
  ];
  assert.deepEqual(buildReplay(rows, v).map(r => r.spoken), [false, false]);
  v.committed(playing);
  assert.deepEqual(buildReplay(rows, v).map(r => r.spoken), [true, false]);
});

test('late_completion_after_cancel_cannot_commit_or_clear_new_request', () => {
  const v = g();
  const cancelled = v.consider('HARSH_BRAKE', 10, 18, 1);
  v.dropped('AUDIO_FOCUS');
  const active = v.consider('HARSH_START', 12, 18, 2);
  assert.equal(v.committed(cancelled), null);
  assert.equal(v.pending, active);
  assert.equal(v.spokenCount, 0);
  v.committed(active);
  assert.equal(v.didSpeak(2), true);
  assert.equal(v.committed(active), null);
  assert.equal(v.spokenCount, 1);
});

test('speaker_requires_end_callback_and_ignores_late_callbacks', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const old = globalThis.SpeechSynthesisUtterance;
  t.after(() => {
    if (old === undefined) delete globalThis.SpeechSynthesisUtterance;
    else globalThis.SpeechSynthesisUtterance = old;
  });
  globalThis.SpeechSynthesisUtterance = class {};
  const speaker = new Speaker();
  let utterance;
  speaker.api = { speak(u) { utterance = u; }, cancel() { utterance.onend(); } };
  speaker.unlock();

  const timedOut = speaker.speak('테스트');
  t.mock.timers.tick(5000);
  assert.equal(await timedOut, 'dropped');
  assert.equal(speaker.lastError, 'TIMEOUT');

  const cancelled = speaker.speak('테스트');
  const oldUtterance = utterance;
  speaker.stop('AUDIO_FOCUS');
  assert.equal(await cancelled, 'dropped');
  const success = speaker.speak('테스트');
  oldUtterance.onerror({ error: 'late' });
  utterance.onend();
  assert.equal(await success, 'spoken');
  assert.equal(speaker.lastError, null);

  const failed = speaker.speak('테스트');
  utterance.onerror({ error: 'synthesis-failed' });
  assert.equal(await failed, 'dropped');
  assert.equal(speaker.lastError, 'synthesis-failed');
});

test('speaker_prefers_local_korean_voice_and_reports_missing_korean', (t) => {
  const previous = globalThis.speechSynthesis;
  t.after(() => {
    if (previous === undefined) delete globalThis.speechSynthesis;
    else globalThis.speechSynthesis = previous;
  });
  const english = { name: 'English', lang: 'en-US', localService: true };
  const remote = { name: 'Remote Korean', lang: 'ko-KR', localService: false };
  const local = { name: 'Local Korean', lang: 'ko-KR', localService: true };
  globalThis.speechSynthesis = { getVoices: () => [english, remote, local] };
  const preferred = new Speaker();
  assert.equal(preferred.voice, local);
  globalThis.speechSynthesis = { getVoices: () => [english] };
  const unavailable = new Speaker();
  unavailable.unlock();
  assert.equal(unavailable.state().ok, false);
});
