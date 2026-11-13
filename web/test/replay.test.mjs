// node --test web/test — C01 속도 시간표 · C03 장면 재현(11/12 회의)
import test from 'node:test';
import assert from 'node:assert/strict';
import { Track, frameAt, timelineSvg, TRACK_HZ, TRACK_MAX } from '../replay.js';

const fill = (sec, f = (t) => ({ kmh: t * 2 })) => { const tr = new Track(); for (let t = 0; t <= sec; t += 0.02) tr.push({ t, ...f(t) }); return tr; };

test('track_keeps_about_10_frames_per_second', () => {
  const tr = fill(10);
  assert.ok(Math.abs(tr.frames.length - 10 * TRACK_HZ) <= 2, `${tr.frames.length}`);
});

test('track_memory_is_bounded_on_long_drives', () => {
  const tr = fill(TRACK_MAX / TRACK_HZ + 120);
  assert.ok(tr.frames.length <= TRACK_MAX);
  assert.ok(tr.duration > TRACK_MAX / TRACK_HZ, '줄여도 처음~끝 구간은 그대로');
});

test('frame_interpolates_between_samples_and_matches_cars_by_id', () => {
  const fr = [{ t: 0, kmh: 0, s: 0, x: 0, npcs: [[1, 0, 10, false]] }, { t: 1, kmh: 10, s: 2, x: 1, npcs: [[1, 0, 20, false]] }];
  const f = frameAt(fr, 0.5);
  assert.equal(f.kmh, 5); assert.equal(f.s, 1); assert.equal(f.npcs[0][2], 15);
});

test('scene_needs_recorded_frames_around_event', () => {
  const tr = fill(10);
  assert.ok(tr.hasScene(5));
  assert.equal(tr.hasScene(60), false, '기록이 없는 시각은 장면 보기를 내놓지 않는다');
});

test('timeline_has_one_marker_per_event_and_no_score', () => {
  const tr = fill(20);
  const svg = timelineSvg(tr.frames, [{ atSec: 3, title: '급출발', kind: 'note' }, { atSec: 12, title: '차로 변경 잘함', kind: 'good', spoken: true }]);
  assert.equal((svg.match(/class="tl-mk /g) || []).length, 2);
  assert.ok(svg.includes('tl-spoke'), '말한 항목은 고리로 표시');
  assert.ok(!/점수|감점|등급/.test(svg));
});
