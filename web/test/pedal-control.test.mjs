// A05 — 브레이크 슬라이더와 페달 그림 사이의 입력 충돌 회귀 테스트.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { holdPedal, bindBrakeSlider } from '../pedal-control.js';

function fakePedal() {
  const handlers = new Map();
  const classes = new Set();
  return {
    addEventListener(name, handler) { handlers.set(name, handler); },
    setPointerCapture() {},
    classList: {
      add(name) { classes.add(name); },
      remove(name) { classes.delete(name); },
      contains(name) { return classes.has(name); },
    },
    emit(name, extra = {}) { handlers.get(name)?.({ preventDefault() {}, stopPropagation() {}, pointerId: 1, ...extra }); },
  };
}

function fakeFrames() {
  let nextId = 0;
  const pending = new Map();
  return {
    requestFrame(cb) { const id = ++nextId; pending.set(id, cb); return id; },
    cancelFrame(id) { pending.delete(id); },
    runOne(t) {
      const first = pending.entries().next().value;
      assert.ok(first, '예정된 애니메이션 프레임');
      const [id, cb] = first;
      pending.delete(id);
      cb(t);
    },
    get active() { return pending.size; },
    get nextCallback() { return pending.values().next().value; },
  };
}

function setup() {
  const pedal = fakePedal();
  const frames = fakeFrames();
  const slider = { oninput: null };
  let pct = 0, ticks = 0;
  const cancelBrakeHold = holdPedal(
    pedal, () => pct, (v) => { pct = v; }, 1600, 900,
    { requestFrame: (cb) => frames.requestFrame(cb), cancelFrame: (id) => frames.cancelFrame(id), now: () => 0 },
  );
  bindBrakeSlider(slider, cancelBrakeHold, (value) => { pct = value; }, () => { ticks++; });
  return { pedal, frames, slider, get pct() { return pct; }, get ticks() { return ticks; } };
}

test('A05 pointer cancellation immediately releases pressure without a tail', () => {
  const f = setup(); f.pedal.emit('pointerdown'); f.frames.runOne(640);
  f.pedal.emit('pointercancel'); assert.equal(f.pct, 0); assert.equal(f.frames.active, 0);
});
test('A05 unexpected capture loss releases pressure', () => {
  const f = setup(); f.pedal.emit('pointerdown'); f.frames.runOne(320);
  f.pedal.emit('lostpointercapture'); assert.equal(f.pct, 0); assert.equal(f.frames.active, 0);
});
test('A05 second pointer and non-owner releases cannot steal a hold', () => {
  const f = setup(); f.pedal.emit('pointerdown'); f.frames.runOne(160);
  f.pedal.emit('pointerdown', { pointerId: 2 });
  f.pedal.emit('pointerup', { pointerId: 2 });
  assert.equal(f.pedal.classList.contains('down'), true);
  f.pedal.emit('pointerup'); assert.equal(f.pedal.classList.contains('down'), false);
});
test('A05 canceled callbacks cannot overwrite a newer slider value', () => {
  const f = setup(); f.pedal.emit('pointerdown'); const late = f.frames.nextCallback;
  f.slider.oninput({ target: { value: '63' } }); late(500);
  assert.equal(f.pct, 63); assert.equal(f.frames.active, 0);
});
test('A05 secondary mouse button cannot press a pedal', () => {
  const f = setup(); f.pedal.emit('pointerdown', { button: 2 });
  assert.equal(f.frames.active, 0); assert.equal(f.pct, 0);
});

test('A05 brake slider takes over an active pedal hold without ReferenceError', () => {
  const { pedal, frames, slider, ...unused } = setup();
  void unused;
  pedal.emit('pointerdown');
  frames.runOne(160); // 1.6초에 100%, 0.16초면 약 10%
  assert.equal(frames.active, 1);
  slider.oninput({ target: { value: '55' } });
  assert.equal(slider.oninput instanceof Function, true);
  assert.equal(pedal.classList.contains('down'), false);
  assert.equal(frames.active, 0, '슬라이더 입력 시 누름 애니메이션 취소');
  const check = setup();
  check.slider.oninput({ target: { value: '55' } });
  assert.equal(check.pct, 55);
  assert.equal(check.ticks, 1);
});

test('A05 brake slider cancels the pedal release animation', () => {
  const fixture = setup();
  fixture.pedal.emit('pointerdown');
  fixture.frames.runOne(640);
  assert.equal(fixture.pct, 40);
  fixture.pedal.emit('pointerup');
  assert.equal(fixture.frames.active, 1, '놓은 뒤 감속 애니메이션 예약');
  fixture.slider.oninput({ target: { value: '72' } });
  assert.equal(fixture.pct, 72, '슬라이더 값이 최종값');
  assert.equal(fixture.frames.active, 0, '놓음 애니메이션도 취소');
  assert.equal(fixture.ticks, 1);
  fixture.pedal.emit('pointerup');
  assert.equal(fixture.frames.active, 0, '취소 이후 pointerup에서 애니메이션 재시작 없음');
});

test('A05 brake slider is wired in the live app, not a stale stopRamp callback', () => {
  const app = readFileSync(new URL('../app.js', import.meta.url), 'utf8');
  assert.match(app, /import \{ holdPedal, bindBrakeSlider \} from '\.\/pedal-control\.js'/);
  assert.match(app, /const cancelBrakeHold = holdPedal\(\$\('#c-brakeHold'\)/);
  assert.match(app, /bindBrakeSlider\(\$\('#c-brake'\), cancelBrakeHold, setBrake, tick\)/);
  assert.doesNotMatch(app, /\bstopRamp\s*\(/);
});
