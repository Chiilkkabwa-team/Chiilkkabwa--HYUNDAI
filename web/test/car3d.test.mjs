// 3D 차 모형의 신호 해석(그리기 없이 확인할 수 있는 부분)
import test from 'node:test';
import assert from 'node:assert/strict';
import { carState, wheelAngleDeg, describe, MISSION_VIEW, VIEWS } from '../car3d.js';
import { MISSION_META } from '../core.js';

const sig = (value, status = 'OK') => ({ value, status, source: 'MOCK' });

test('쓸 수 없는 신호는 켜짐으로 보이지 않는다(모르면 꺼짐)', () => {
  const st = carState({ gear: sig('P', 'UNKNOWN'), parkingBrakeOn: sig(true, 'DISCONNECTED'), brakePressed: sig(true, 'STALE'), turnSignal: sig('LEFT', 'UNSUPPORTED'), lowBeamOn: sig(true, 'PERMISSION_DENIED'), driverBeltBuckled: sig(true, 'UNKNOWN'), steeringDeg: sig(300, 'UNKNOWN') });
  assert.deepEqual(st, { gearP: false, parkingBrake: false, brake: false, turn: 'NONE', lowBeam: false, wiper: false, belt: null, steeringDeg: 0 });
});

test('신호가 있으면 그대로 반영', () => {
  const st = carState({ gear: sig('P'), parkingBrakeOn: sig(true), brakePressed: sig(true), turnSignal: sig('RIGHT'), lowBeamOn: sig(true), wiperOn: sig(true), driverBeltBuckled: sig(false), steeringDeg: sig(-150) });
  assert.equal(st.gearP && st.parkingBrake && st.brake && st.lowBeam && st.wiper, true);
  assert.equal(st.turn, 'RIGHT'); assert.equal(st.belt, false); assert.equal(st.steeringDeg, -150);
});

test('앞바퀴 각도: 조향비 15:1, 최대 35°', () => {
  assert.equal(wheelAngleDeg(150), 10);
  assert.equal(wheelAngleDeg(-900), -35);
  assert.equal(wheelAngleDeg(undefined), 0);
});

test('화면 읽기 설명에 상태가 다 들어간다', () => {
  const t = describe({ gearP: true, parkingBrake: true, brake: false, turn: 'LEFT', lowBeam: false, wiper: false, belt: false, steeringDeg: 300 });
  for (const w of ['기어 P.', '주차 브레이크 걸림', '왼쪽 방향지시등', '안전벨트 안 맴', '앞바퀴 20도 오른쪽']) assert.ok(t.includes(w), w);
});

test('워밍업 미션마다 카메라 자리가 있다', () => {
  for (const id of Object.keys(MISSION_META)) assert.ok(VIEWS[MISSION_VIEW[id]], id);
});
