import test from 'node:test';
import assert from 'node:assert/strict';
import { DriveSim, junctionAt, nextJunction, CAR_L, CAR_W, LANE_W, JUNCTION_W, TURN_OK_KMH } from '../sim.js';
import { buildReplay } from '../voice.js';

function setup(k = 0, { maneuver = junctionAt(k).dir, signal = maneuver, lane = maneuver === 'LEFT' ? -1 : 1, kmh = 18 } = {}) {
  const s = new DriveSim();
  s.npcs = []; s.cutIn.done = true;
  const j = junctionAt(k);
  s.ego = { s: j.s - CAR_L / 2 - 0.01, x: lane * LANE_W, v: kmh / 3.6, a: 0 };
  s.input.maneuver = maneuver; s.input.signal = signal;
  s.prevLane = lane;
  return { s, j };
}
const codes = (s) => s.events.filter(e => e.cat === 'turn').map(e => e.code);
const enter = (s) => s.step(0.02);

for (const k of [0, 1]) {
  test('normal ' + junctionAt(k).dir + ' uses independently selected maneuver', () => {
    const { s, j } = setup(k); enter(s);
    assert.deepEqual(codes(s), ['TURN_GOOD']);
    const e = s.events.at(-1);
    assert.equal(e.dir, j.dir); assert.equal(e.source, 'MOCK');
    assert.equal(e.junctionK, k); assert.equal(e.phase, 'ENTRY');
    assert.equal(e.basis.signalMatchesManeuver, true);
    assert.equal(e.basis.laneMatches, true);
    assert.equal(e.basis.speedWithinLimit, true);
    assert.equal(s.lastTurn.dir, j.dir);
  });
  test('opposite signal at ' + junctionAt(k).dir + ' is not silently straight', () => {
    const { s } = setup(k, { signal: junctionAt(k).dir === 'RIGHT' ? 'LEFT' : 'RIGHT' });
    enter(s); assert.deepEqual(codes(s), ['TURN_WRONG_SIGNAL']);
    assert.equal(s.events.at(-1).basis.signalMatchesManeuver, false);
  });
}

test('no signal and straight records passage without turn penalty', () => {
  const { s } = setup(0, { maneuver: 'STRAIGHT', signal: 'NONE', lane: 0 });
  enter(s); assert.deepEqual(codes(s), ['JUNCTION_STRAIGHT']);
  assert.equal(s.penalty, 0); assert.equal(s.junction.turns, 0); assert.equal(s.lastTurn, undefined);
});
test('a signal alone never creates a virtual turn', () => {
  const { s } = setup(0, { maneuver: 'STRAIGHT', signal: 'RIGHT' }); enter(s);
  assert.deepEqual(codes(s), ['JUNCTION_STRAIGHT', 'TURN_WRONG_SIGNAL']);
  assert.equal(s.junction.turns, 0);
});
test('missing signal during explicit turn is recorded', () => {
  const { s } = setup(0, { signal: 'NONE' }); enter(s);
  assert.deepEqual(codes(s), ['TURN_WRONG_SIGNAL']);
});
test('wrong segment direction is independent of matching turn signal', () => {
  const { s } = setup(0, { maneuver: 'LEFT', signal: 'LEFT', lane: -1 }); enter(s);
  assert.deepEqual(codes(s), ['TURN_WRONG_DIRECTION']);
  assert.equal(s.events.at(-1).basis.directionMatchesSegment, false);
});
test('wrong lane and fast entry both keep direction, time and evidence', () => {
  const { s } = setup(1, { lane: 1, kmh: 40 }); enter(s);
  assert.deepEqual(codes(s), ['TURN_WRONG_LANE', 'TURN_FAST']);
  for (const e of s.events) {
    assert.equal(e.dir, 'LEFT'); assert.equal(e.junctionK, 1); assert.equal(e.source, 'MOCK');
    assert.equal(e.phase, 'ENTRY'); assert.equal(e.basis.speedUnit, 'km/h');
    assert.ok(e.t >= 0 && e.t <= s.t); assert.equal(e.basis.signal, 'LEFT');
  }
  assert.deepEqual(s.events[0].basis, s.events[1].basis);
});
test('vehicle straddling turn lane boundary is not in the turn lane', () => {
  const { s } = setup(); s.ego.x = LANE_W - (LANE_W - CAR_W) / 2 - 0.01;
  enter(s); assert.deepEqual(codes(s), ['TURN_WRONG_LANE']);
});
test('25 km/h entry is accepted, strictly greater is fast without rounding', () => {
  for (const [kmh, want] of [[TURN_OK_KMH, 'TURN_GOOD'], [TURN_OK_KMH + 0.01, 'TURN_FAST']]) {
    const { s } = setup(0, { kmh });
    // Direct segment with constant speed isolates the exact threshold.
    const before = { ...s.ego, t: 0 };
    s.ego.s += 0.02; s.t = 0.01; s.stepJunction(before);
    assert.deepEqual(codes(s), [want]);
  }
});
test('entry speed is captured at crossing, not the end of a long step', () => {
  const { s, j } = setup(0, { kmh: 36 });
  const before = { s: j.s - CAR_L / 2 - 1, x: LANE_W, v: 10, t: 2 };
  s.ego.s = j.s - CAR_L / 2 + 9; s.ego.v = 1; s.t = 3;
  s.stepJunction(before);
  const e = s.events.at(-1); assert.equal(e.code, 'TURN_FAST');
  assert.ok(Math.abs(e.t - 2.1) < 1e-8); assert.ok(e.basis.speedKmh > 25);
});
test('approach notification includes stopped and crawling vehicle once', () => {
  for (const v of [0, 0.2, 1.5]) {
    const { s, j } = setup(); s.ego.s = j.s - CAR_L / 2 - 60; s.ego.v = v;
    s.stepJunction(); s.stepJunction();
    assert.deepEqual(codes(s), ['JUNCTION_AHEAD']);
    assert.equal(s.junction.k, -1); assert.equal(s.junction.turns, 0);
    assert.equal(s.events[0].phase, 'APPROACH'); assert.equal(s.events[0].dir, j.dir);
  }
});
test('stopped at entry does not claim a turn until forward movement', () => {
  const { s, j } = setup(0, { kmh: 0 }); s.ego.s = j.s - CAR_L / 2;
  s.stepJunction({ ...s.ego, t: s.t });
  assert.deepEqual(codes(s), []); assert.equal(s.junction.k, -1);
  s.input.accel = 0.3; enter(s); assert.ok(codes(s).includes('TURN_GOOD'));
});
test('same junction cannot repeat after waiting, signal changes or reversing position', () => {
  const { s, j } = setup(); enter(s);
  s.t += 20; s.input.signal = 'LEFT'; s.stepJunction();
  s.ego.s = j.s - CAR_L / 2 - 1; s.stepJunction();
  s.input.signal = 'RIGHT'; s.ego.s += 2; s.stepJunction();
  assert.deepEqual(codes(s), ['TURN_GOOD']); assert.equal(s.junction.turns, 1);
});
test('next junction resets approach and entry independently after straight passage', () => {
  const { s } = setup(0, { maneuver: 'STRAIGHT', signal: 'NONE', lane: 0 }); enter(s);
  const j = junctionAt(1); s.ego.s = j.s - CAR_L / 2 - 50; s.ego.v = 1;
  s.stepJunction(); assert.equal(s.events.at(-1).code, 'JUNCTION_AHEAD');
  s.input.maneuver = 'LEFT'; s.input.signal = 'LEFT'; s.ego.x = -LANE_W;
  const before = { ...s.ego, t: s.t }; s.ego.s = j.s - CAR_L / 2 + 0.1; s.t += 10;
  s.stepJunction(before); assert.equal(s.events.at(-1).code, 'TURN_GOOD');
  assert.equal(s.junction.k, 1); assert.equal(s.junction.turns, 1);
});
test('crossing the full junction in one sample is not missed', () => {
  const { s, j } = setup(); const before = { ...s.ego, t: 0 };
  s.ego.s = j.s + JUNCTION_W + 5; s.t = 1; s.stepJunction(before);
  assert.deepEqual(codes(s), ['TURN_GOOD']); assert.equal(s.junction.k, 0);
});
test('nextJunction retains current junction through its far boundary', () => {
  for (const k of [0, 1, 5]) {
    const j = junctionAt(k);
    assert.equal(nextJunction(j.s - 0.01).k, k);
    assert.equal(nextJunction(j.s + JUNCTION_W).k, k);
    assert.equal(nextJunction(j.s + JUNCTION_W + 0.01).k, k + 1);
  }
});
test('nonfinite observations are not scored or turned into a good maneuver', () => {
  for (const key of ['s', 'x', 'v']) {
    const { s } = setup(); s.ego[key] = NaN; s.stepJunction();
    assert.deepEqual(codes(s), []);
  }
});
test('hazard and unknown indicator are never accepted as matching turns', () => {
  for (const signal of ['HAZARD', 'UNKNOWN']) {
    const { s } = setup(0, { signal }); enter(s);
    assert.deepEqual(codes(s), ['TURN_WRONG_SIGNAL']);
  }
});

test('junction evidence survives report and replay JSON persistence', () => {
  const { s } = setup(1, { signal: 'RIGHT', lane: 1, kmh: 40 }); enter(s);
  const report = s.report();
  const rows = JSON.parse(JSON.stringify(buildReplay(report.events, null, 'VEHICLE')));
  assert.equal(rows.length, 3);
  for (const row of rows) {
    assert.equal(row.dir, 'LEFT'); assert.equal(row.segmentDir, 'LEFT');
    assert.equal(row.junctionK, 1); assert.equal(row.source, 'MOCK');
    assert.equal(row.atSec, report.events[0].t); assert.equal(row.basis.signal, 'RIGHT');
    assert.equal(row.basis.laneMatches, false); assert.equal(row.basis.speedWithinLimit, false);
  }
});

test('unknown virtual direction is unavailable rather than straight or good', () => {
  const { s } = setup(0, { maneuver: 'UNKNOWN', signal: 'NONE' }); enter(s);
  assert.deepEqual(codes(s), ['JUNCTION_UNAVAILABLE']); assert.equal(s.penalty, 0);
});

test('low speed lane change and signal away from junction are not a turn', () => {
  const s = new DriveSim(); s.npcs = []; s.cutIn.done = true;
  s.ego.v = 3; s.input.signal = 'RIGHT'; s.input.steer = 540;
  for (let i = 0; i < 100; i++) s.step(0.02);
  assert.equal(s.junction.turns, 0); assert.deepEqual(codes(s), []);
});

test('approach does not fix the maneuver or judge it before entry', () => {
  const { s, j } = setup(1, { maneuver: 'STRAIGHT', signal: 'NONE' });
  s.ego.s = j.s - CAR_L / 2 - 10; s.stepJunction();
  assert.deepEqual(codes(s), ['JUNCTION_AHEAD']);
  s.input.maneuver = 'LEFT'; s.input.signal = 'LEFT'; s.ego.x = -LANE_W;
  const before = { ...s.ego, t: s.t }; s.ego.s += 11; s.t += 2;
  s.stepJunction(before);
  assert.deepEqual(codes(s), ['JUNCTION_AHEAD', 'TURN_GOOD']);
});

test('lane at entry is used even if later frame ends in the correct lane', () => {
  const { s, j } = setup();
  const before = { s: j.s - CAR_L / 2 - 1, x: 0, v: 5, t: 0 };
  s.ego.s = j.s - CAR_L / 2 + 9; s.ego.x = LANE_W; s.t = 2;
  s.stepJunction(before);
  assert.deepEqual(codes(s), ['TURN_WRONG_LANE']);
  assert.ok(s.events[0].basis.xM < 0.5);
});

test('different junctions do not suppress the same event code via global cooldown', () => {
  const { s } = setup(); enter(s);
  const j = junctionAt(1);
  const before = { s: j.s - CAR_L / 2 - 0.1, x: -LANE_W, v: 5, t: s.t };
  s.input.maneuver = 'LEFT'; s.input.signal = 'LEFT'; s.ego = { ...before, s: before.s + 0.2 };
  s.t += 0.1; s.stepJunction(before);
  assert.deepEqual(codes(s), ['TURN_GOOD', 'TURN_GOOD']);
  assert.deepEqual(s.events.map(e => e.junctionK), [0, 1]);
});
