import test from 'node:test';
import assert from 'node:assert/strict';
import { KeyboardDrive, gearPresentation, stopRequestState } from '../drive-controls.js';

const advance = (k, seconds) => { let out; for (let i = 0; i < seconds * 50; i++) out = k.sample(0.02); return out; };
test('keyboard pedals ramp toward explicit bounded targets', () => {
  const k = new KeyboardDrive(); k.key('KeyW', true);
  assert.ok(k.sample(0.02).accel > 0); assert.ok(k.sample(0.02).accel < 10);
  assert.equal(advance(k, 1).accel, 60);
  k.key('KeyS', true); const both = advance(k, 1);
  assert.equal(both.accel, 0); assert.equal(both.brake, 70);
});
test('steering ramps to 180 degrees and returns to zero after release', () => {
  const k = new KeyboardDrive(); k.key('KeyA', true);
  assert.ok(k.sample(0.02).steer > -180);
  assert.equal(advance(k, 0.3).steer, -180);
  k.key('KeyA', false); const out = advance(k, 0.2);
  assert.equal(out.steer ?? 0, 0); assert.deepEqual(k.sample(0.02), {});
});
test('opposing steering is neutral and releasing one resumes remaining direction', () => {
  const k = new KeyboardDrive(); k.key('KeyA', true); advance(k, 0.2);
  k.key('KeyD', true); assert.equal(advance(k, 0.2).steer, 0);
  k.key('KeyA', false); assert.equal(advance(k, 0.2).steer, 180);
});
test('physical aliases do not release each other', () => {
  const k = new KeyboardDrive(); k.key('KeyW', true); k.key('ArrowUp', true); k.key('KeyW', false);
  assert.equal(advance(k, 1).accel, 60);
  k.key('ArrowUp', false); advance(k, 1); assert.deepEqual(k.sample(0.02), {});
});
test('manual takeover suppresses held key autorepeat until a fresh press', () => {
  const k = new KeyboardDrive(); k.key('KeyW', true); advance(k, 1);
  k.takeover('accel'); k.key('KeyW', true); assert.deepEqual(k.sample(0.02), {});
  k.key('KeyW', false); k.key('KeyW', true); assert.ok(k.sample(0.02).accel > 0);
});
test('one channel takeover leaves other keyboard inputs intact', () => {
  const k = new KeyboardDrive(); k.key('KeyW', true); k.key('KeyD', true);
  k.takeover('steer'); assert.deepEqual(Object.keys(k.sample(0.02)), ['accel']);
});
test('blur clear releases owned channels only and no late frame can reactivate them', () => {
  const k = new KeyboardDrive(); k.key('ArrowUp', true); k.key('ArrowLeft', true); advance(k, 1);
  assert.deepEqual(k.releaseAll(), { accel: 0, steer: 0 });
  assert.deepEqual(k.sample(0.02), {});
});
test('quick taps and frame stalls remain bounded and finite', () => {
  const k = new KeyboardDrive();
  for (let i = 0; i < 40; i++) { k.key('KeyA', true); k.sample(0.01); k.key('KeyA', false); k.sample(0.01); }
  k.key('ArrowRight', true, 540);
  for (const dt of [0, -1, NaN, 100]) {
    const v = k.sample(dt).steer; assert.ok(Number.isFinite(v) && Math.abs(v) <= 540);
  }
  assert.equal(advance(k, 2).steer, 180);
});
test('keyboard-only channels do not emit zeros for untouched pointer or slider inputs', () => {
  const k = new KeyboardDrive(); k.key('KeyA', true);
  assert.deepEqual(Object.keys(k.sample(0.02)), ['steer']);
  assert.equal(k.key('KeyQ', true), null);
});
test('gear labels distinguish stopped drive from parking and preserve unknown', () => {
  for (const gear of ['P', 'R', 'N', 'D']) assert.ok(gearPresentation(gear, true).includes(gear));
  assert.match(gearPresentation('D', true), /주행/);
  assert.match(gearPresentation('P', false), /브레이크/);
  assert.match(gearPresentation(null, true), /확인/);
});
test('stop request is distinct from deceleration, stopped, and released', () => {
  assert.equal(stopRequestState(false, 0).key, 'AVAILABLE');
  assert.equal(stopRequestState(true, 10).key, 'DECELERATING');
  assert.equal(stopRequestState(true, 0.01).key, 'STOPPED');
  assert.equal(stopRequestState(false, 0, true).key, 'RELEASED');
  assert.equal(stopRequestState(true, NaN).key, 'UNKNOWN');
});
