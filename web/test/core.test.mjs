// node --test web/test — app/src/test/.../CoreLogicTest.kt 의 핵심 시나리오를 JS 포팅본에 그대로 적용
import test from 'node:test';
import assert from 'node:assert/strict';
import * as C from '../core.js';

const ok = (v, at) => ({ value: v, status: 'OK', source: 'EMULATOR', receivedAt: at });
const st = (status, at = 0) => ({ value: null, status, source: 'EMULATOR', receivedAt: at });
const parked = (at = 0, o = {}) => ({
  gear: ok('P', at), parkingBrakeOn: ok(true, at), uxRestricted: ok(false, at), driverBeltBuckled: ok(true, at),
  brakePressed: ok(false, at), brakePedalPct: ok(0, at), turnSignal: ok('NONE', at), wiperOn: ok(false, at), lowBeamOn: ok(false, at),
  steeringDeg: ok(0, at), tripDistanceKm: ok(100, at), tripTimeSec: ok(0, at), ...o,
});

test('gate_ready_only_when_park_brake_and_no_restriction', () => {
  assert.equal(C.evaluateGate(parked()).ready, true);
  assert.equal(C.evaluateGate(parked(0, { parkingBrakeOn: ok(false, 0) })).reason, 'PARKING_BRAKE_OFF');
});
test('gate_locks_when_unknown', () => {
  assert.equal(C.evaluateGate(parked(0, { gear: st('UNKNOWN') })).reason, 'UNKNOWN_STATE');
  assert.equal(C.evaluateGate(parked(0, { uxRestricted: st('DISCONNECTED') })).reason, 'UNKNOWN_STATE');
});
test('gate_restriction_has_priority', () => {
  assert.equal(C.evaluateGate(parked(0, { uxRestricted: ok(true, 0), gear: st('UNKNOWN') })).reason, 'DRIVING_RESTRICTED');
});
test('gate_speed_zero_but_drive_is_not_parked', () => {
  assert.equal(C.evaluateGate(parked(0, { gear: ok('D', 0) })).reason, 'NOT_IN_PARK');
});
test('gate_parking_brake_unsupported_policy', () => {
  const s = parked(0, { parkingBrakeOn: st('UNSUPPORTED') });
  assert.equal(C.evaluateGate(s).reason, 'UNKNOWN_STATE');
  assert.equal(C.evaluateGate(s, { requireParkingBrake: true, allowWhenParkingBrakeUnsupported: true }).ready, true);
  assert.equal(C.evaluateGate(parked(0, { parkingBrakeOn: st('UNKNOWN') }), { requireParkingBrake: true, allowWhenParkingBrakeUnsupported: true }).ready, false);
});
test('brake_already_pressed_does_not_complete', () => {
  const j = C.brakePressReleaseJudge();
  j.start(parked(0, { brakePressed: ok(true, 0) }), 10);
  assert.equal(j.update(parked(0, { brakePressed: ok(false, 20) }), 20).kind, 'progress');
  assert.equal(j.update(parked(0, { brakePressed: ok(true, 30) }), 30).kind, 'progress');
  assert.equal(j.update(parked(0, { brakePressed: ok(false, 40) }), 40).kind, 'done');
});
test('old_values_before_start_are_ignored', () => {
  const j = C.turnSignalJudge();
  j.start(parked(0), 100);
  assert.equal(j.update(parked(0, { turnSignal: ok('LEFT', 50) }), 110).step, 1);
});
test('unsupported_signal_gives_unavailable', () => {
  const j = C.seatBeltJudge(); j.start(parked(0), 0);
  assert.deepEqual(j.update(parked(0, { driverBeltBuckled: st('UNSUPPORTED') }), 1), { kind: 'unavailable', status: 'UNSUPPORTED' });
});
test('belt_already_buckled_requires_rebuckle', () => {
  const j = C.seatBeltJudge(); j.start(parked(0), 0);
  assert.equal(j.update(parked(0, { driverBeltBuckled: ok(true, 5) }), 5).kind, 'progress');
  j.update(parked(0, { driverBeltBuckled: ok(false, 6) }), 6);
  assert.equal(j.update(parked(0, { driverBeltBuckled: ok(true, 7) }), 7).kind, 'done');
});
test('turn_signal_left_right_off', () => {
  const j = C.turnSignalJudge(); j.start(parked(0), 0);
  j.update(parked(0, { turnSignal: ok('LEFT', 1) }), 1);
  j.update(parked(0, { turnSignal: ok('RIGHT', 2) }), 2);
  assert.equal(j.update(parked(0, { turnSignal: ok('NONE', 3) }), 3).kind, 'done');
});
test('pedal_hold_two_seconds_in_band', () => {
  const j = C.pedalHoldJudge('brakePedalPct'); j.start(parked(0), 0);
  assert.equal(j.update(parked(0, { brakePedalPct: ok(28, 10) }), 10).kind, 'progress');
  assert.equal(j.update(parked(0, { brakePedalPct: ok(40, 1000) }), 1000).kind, 'progress');
  j.update(parked(0, { brakePedalPct: ok(25, 1100) }), 1100);
  const p = j.update(parked(0, { brakePedalPct: ok(30, 3200) }), 3200);
  assert.equal(p.kind, 'done'); assert.ok(p.metric > 0);
});
test('session_flow_parked_drive_review_and_trip_summary', () => {
  let s = C.initialSession(); const P = parked(0);
  s = C.onGate(s, C.evaluateGate(P), P, 0); assert.equal(s.phase, 'READY');
  s = C.onStartPressed(s); assert.equal(s.phase, 'PREPARING');
  const D = parked(0, { gear: ok('D', 1), uxRestricted: ok(true, 1), tripDistanceKm: ok(100, 1), tripTimeSec: ok(0, 1) });
  s = C.onGate(s, C.evaluateGate(D), D, 10_000); assert.equal(s.phase, 'DRIVING_LOCK');
  const B = parked(2, { tripDistanceKm: ok(103.2, 2), tripTimeSec: ok(840, 2) });
  s = C.onGate(s, C.evaluateGate(B), B, 900_000); assert.equal(s.phase, 'REVIEW');
  const sum = C.summarize(s.tripStart, s.tripEnd);
  assert.equal(sum.durationMin, 14); assert.ok(Math.abs(sum.distanceKm - 3.2) < 1e-9); assert.equal(sum.durationFromVehicle, true);
});
test('preparing_pause_resumes_when_brake_reapplied', () => {
  let s = { ...C.initialSession(), phase: 'PREPARING', lockReason: null };
  const off = parked(0, { parkingBrakeOn: ok(false, 1) });
  s = C.onGate(s, C.evaluateGate(off), off, 0); assert.equal(s.lockReason, 'PARKING_BRAKE_OFF'); assert.equal(s.phase, 'PREPARING');
  s = C.onGate(s, C.evaluateGate(parked(2)), parked(2), 1); assert.equal(s.lockReason, null);
});
test('summary_falls_back_to_wall_clock', () => {
  const sum = C.summarize({ distanceKm: null, tripTimeSec: null, wallClockMs: 0 }, { distanceKm: null, tripTimeSec: null, wallClockMs: 600_000 });
  assert.equal(sum.durationMin, 10); assert.equal(sum.durationFromVehicle, false);
});
test('badges_mark_mock_only', () => {
  const h = { missionResults: [{ id: 'SEAT_BELT', source: 'MOCK' }], parkingSimCompleted: 0, pactsMade: 0, reviewsSaved: 0, graduated: false, mentorSessions: 0, badges: [] };
  const got = C.newlyEarned(h, 0);
  assert.deepEqual(got.map((b) => [b.badge, b.mockOnly]), [['FIRST_CLICK', true]]);
});
test('parking_sim_ideal_reverse_lands_in_slot', () => {
  const sim = new C.ParkingSim({ lengthMm: 4700, widthMm: 1830, wheelbaseMm: 2720 });
  let p = { x: sim.minRadius, y: -5.5, heading: Math.PI / 2 };
  while (p.heading > 0.001) p = sim.step(p, 540, -0.5, 0.01);
  while (sim.bodyCenter(p).y < 0) p = sim.step(p, 0, -0.5, 0.01);
  assert.equal(sim.parkState(p, { cx: 0, cy: 0 }).ok, true);
});
